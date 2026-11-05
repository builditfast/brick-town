package ai.freeapp.bricktown.town

import ai.freeapp.bricktown.auth.userId
import ai.freeapp.bricktown.core.respondError
import ai.freeapp.bricktown.domain.BillSkip
import ai.freeapp.bricktown.domain.Category
import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.domain.ImportFailure
import ai.freeapp.bricktown.domain.PdfImportResult
import ai.freeapp.bricktown.domain.looksLikePdf
import ai.freeapp.bricktown.domain.TownSettings
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.flags.FeatureFlags
import ai.freeapp.bricktown.flags.plan
import ai.freeapp.bricktown.wire.AcceptInvite
import ai.freeapp.bricktown.wire.CategoryOrder
import ai.freeapp.bricktown.wire.CreateTownRequest
import ai.freeapp.bricktown.wire.InviteRequest
import ai.freeapp.bricktown.wire.MemberUpdate
import ai.freeapp.bricktown.wire.SettleRequest
import ai.freeapp.bricktown.wire.agentSummaryOf
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readAvailable
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPOutputStream

private const val MAX_PDF_BYTES = 5 * 1024 * 1024

/**
 * The request body, or null once it passes [limit] bytes. Counts the bytes as they are
 * read, so a missing or lying Content-Length does not let more through. Memory only.
 */
private suspend fun ApplicationCall.readLimitedBody(limit: Int): ByteArray? {
    val declared = request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (declared != null && declared > limit) return null
    val channel = receiveChannel()
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(16 * 1024)
    while (true) {
        val read = channel.readAvailable(chunk)
        if (read < 0) break
        if (out.size() + read > limit) return null
        out.write(chunk, 0, read)
    }
    return out.toByteArray()
}

/** Signed-in town routes. Mount inside `authenticate(ACCESS_TOKEN_AUTH)`. */
fun Route.townRoutes() {
    val repo by inject<TownRepository>()
    val flags by inject<FeatureFlags>()

    suspend fun ApplicationCall.requireSharing(): Boolean {
        if (flags.isOn("town.share", plan())) return true
        respondError(HttpStatusCode.Forbidden, "Sharing a town is switched off.")
        return false
    }

    route("/town") {
        get {
            val snapshot = repo.snapshot(call.userId())
            if (snapshot == null) call.respondError(HttpStatusCode.NotFound, TownRepository.NOT_IN_A_TOWN)
            else call.respondSnapshot(snapshot)
        }

        post { call.respond(repo.create(call.userId(), call.receive<CreateTownRequest>())) }

        delete {
            repo.deleteTown(call.userId())
            call.respond(HttpStatusCode.NoContent)
        }

        put("/settings") {
            call.respond(repo.putSettings(call.userId(), call.receive<TownSettings>()))
        }

        post("/import/pdf") {
            if (!flags.isOn("import.pdf", call.plan())) {
                call.respondError(HttpStatusCode.Forbidden, "Reading PDF statements is switched off.")
                return@post
            }
            val bytes = call.readLimitedBody(MAX_PDF_BYTES)
            if (bytes == null) {
                call.respondError(HttpStatusCode.PayloadTooLarge, "That PDF is bigger than 5 MB, which is more than I read.")
                return@post
            }
            call.respond(
                if (looksLikePdf(bytes)) PdfStatementReader.read(bytes)
                else PdfImportResult(failure = ImportFailure.NOT_A_STATEMENT),
            )
        }

        post("/leave") {
            repo.leave(call.userId())
            call.respond(HttpStatusCode.NoContent)
        }

        route("/categories") {
            put("/order") {
                repo.reorderCategories(call.userId(), call.receive<CategoryOrder>())
                call.respond(HttpStatusCode.NoContent)
            }
            put("/{id}") {
                val id = call.idParam("id")
                call.respond(repo.putCategory(call.userId(), id, call.receive<Category>()))
            }
            delete("/{id}") {
                val id = call.idParam("id")
                val mergeInto = call.request.queryParameters["mergeInto"]?.let { call.idValue(it) }
                repo.deleteCategory(call.userId(), id, mergeInto)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        route("/expenses/{id}") {
            put { call.respond(repo.putExpense(call.userId(), call.idParam("id"), call.receive<Expense>())) }
            delete {
                repo.deleteExpense(call.userId(), call.idParam("id"))
                call.respond(HttpStatusCode.NoContent)
            }
        }

        route("/gold/{date}") {
            put {
                repo.putGold(call.userId(), call.dateParam("date"))
                call.respond(HttpStatusCode.NoContent)
            }
            delete {
                repo.deleteGold(call.userId(), call.dateParam("date"))
                call.respond(HttpStatusCode.NoContent)
            }
        }

        put("/bill-skips") {
            repo.putBillSkip(call.userId(), call.receive<BillSkip>())
            call.respond(HttpStatusCode.NoContent)
        }

        post("/settle") {
            if (!flags.isOn("town.settle", call.plan())) {
                call.respondError(HttpStatusCode.Forbidden, "Settling up is switched off.")
                return@post
            }
            call.respond(repo.settle(call.userId(), call.receive<SettleRequest>().upTo))
        }

        put("/members/{userId}") {
            call.respond(repo.updateMember(call.userId(), call.idParam("userId"), call.receive<MemberUpdate>()))
        }

        route("/invites") {
            post {
                if (!call.requireSharing()) return@post
                val request = call.receive<InviteRequest>()
                call.respond(repo.createInvite(call.userId(), request))
            }
            delete("/{code}") {
                val code = InviteCodes.normalize(call.parameters["code"].orEmpty())
                if (code != null) repo.revokeInvite(call.userId(), code)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        get("/summary") {
            val userId = call.userId()
            val snapshot = repo.snapshot(userId)
            if (snapshot == null) {
                call.respondError(HttpStatusCode.NotFound, TownRepository.NOT_IN_A_TOWN)
                return@get
            }
            val asked = call.request.queryParameters["date"]?.let { call.dateValue(it) }
            val today = asked ?: TownRules.today(TownRules.zoneOf(snapshot.town.settings.timeZone))
            call.respond(agentSummaryOf(snapshot, today))
        }
    }

    route("/invites/{code}") {
        get {
            if (!call.requireSharing()) return@get
            call.respond(repo.previewInvite(call.codeParam()))
        }
        post("/accept") {
            if (!call.requireSharing()) return@post
            call.respond(repo.acceptInvite(call.userId(), call.codeParam(), call.receive<AcceptInvite>()))
        }
    }
}

/**
 * The snapshot repeats uuids on every brick, so 5 000 bricks are about 1.4 MB as
 * JSON and about a tenth of that gzipped. Phone HTTP stacks ask for gzip and
 * unpack it themselves; a client that does not ask gets plain JSON.
 */
private suspend fun ApplicationCall.respondSnapshot(snapshot: TownSnapshot) {
    val wantsGzip = request.headers[HttpHeaders.AcceptEncoding].orEmpty().contains("gzip", ignoreCase = true)
    if (!wantsGzip) return respond(snapshot)
    val json = Json.encodeToString(TownSnapshot.serializer(), snapshot).toByteArray(Charsets.UTF_8)
    val packed = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(json) } }.toByteArray()
    response.headers.append(HttpHeaders.ContentEncoding, "gzip")
    respondBytes(packed, ContentType.Application.Json)
}

private fun ApplicationCall.codeParam(): String =
    InviteCodes.normalize(parameters["code"].orEmpty())
        ?: problem(HttpStatusCode.NotFound, "That invite has expired or was revoked.")

private fun ApplicationCall.idParam(name: String): UUID = idValue(parameters[name].orEmpty())

private fun ApplicationCall.idValue(raw: String): UUID =
    runCatching { UUID.fromString(raw) }.getOrNull() ?: problem(HttpStatusCode.BadRequest, "That id is not valid.")

private fun ApplicationCall.dateParam(name: String): LocalDate = dateValue(parameters[name].orEmpty())

private fun ApplicationCall.dateValue(raw: String): LocalDate =
    runCatching { LocalDate.parse(raw) }.getOrNull()
        ?: problem(HttpStatusCode.BadRequest, "A date looks like 2026-11-24.")
