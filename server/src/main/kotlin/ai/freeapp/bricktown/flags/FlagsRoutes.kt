package ai.freeapp.bricktown.flags

import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.ktor.ext.inject
import java.nio.file.Path

/**
 * The flags declared in resources/flags.json, with the owner's switches read
 * from [statePath]. Empty means /root/.freeapp/flags/<tool>.json, the file
 * `flag on|off|plans <tool>.<name>` writes on this box.
 */
fun loadFeatureFlags(statePath: String): FeatureFlags {
    val spec = requireNotNull(FeatureFlags::class.java.getResource("/flags.json")) {
        "resources/flags.json is missing: every feature is declared there"
    }.readText()
    val tool = Json.parseToJsonElement(spec).jsonObject.getValue("tool").jsonPrimitive.content
    return FeatureFlags(spec, Path.of(statePath.ifEmpty { "/root/.freeapp/flags/$tool.json" }))
}

/**
 * The plan of the signed-in user, which plan-limited flags are checked against.
 * Null means no plan, so a flag limited to plans is off for this caller.
 *
 * EXTENSION POINT: once the app sells plans, return the user's plan from the
 * account record here. Never take it from the request: a client could name any plan.
 */
fun ApplicationCall.plan(): String? = null

/** Signed-in. What this user may use, so the app hides what is switched off. */
fun Route.flagsRoutes() {
    val flags by inject<FeatureFlags>()
    get("/flags") { call.respond(flags.forPlan(call.plan())) }
}
