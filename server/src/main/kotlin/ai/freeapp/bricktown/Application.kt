package ai.freeapp.bricktown

import ai.freeapp.bricktown.auth.ACCESS_TOKEN_AUTH
import ai.freeapp.bricktown.auth.authRoutes
import ai.freeapp.bricktown.auth.installAccessTokenAuth
import ai.freeapp.bricktown.auth.meRoutes
import ai.freeapp.bricktown.config.AppConfig
import ai.freeapp.bricktown.config.DatabaseFactory
import ai.freeapp.bricktown.core.ErrorResponse
import ai.freeapp.bricktown.di.serverModule
import ai.freeapp.bricktown.health.healthRoutes
import ai.freeapp.bricktown.flags.flagsRoutes
import ai.freeapp.bricktown.legal.legalRoutes
import ai.freeapp.bricktown.town.TownProblem
import ai.freeapp.bricktown.town.joinRoutes
import ai.freeapp.bricktown.town.townRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

private val log = LoggerFactory.getLogger("ai.freeapp.bricktown.Application")

fun Application.module() {
    // Before the database: a production box without a real JWT_SECRET must
    // refuse to start even when Postgres is unreachable.
    val config = AppConfig.from(environment.config)
    DatabaseFactory.init(environment.config)
    monitor.subscribe(ApplicationStopped) { DatabaseFactory.close() }

    install(Koin) {
        slf4jLogger()
        modules(serverModule(config))
    }
    install(DefaultHeaders)
    install(CallLogging) { level = Level.INFO }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Delete)
    }
    install(StatusPages) {
        exception<TownProblem> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.message.orEmpty()))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("The request body is malformed."))
        }
        // Ktor's engine answers a body it cannot read as JSON with a bare 415
        // before any exception handler here sees it, so the reason is added on
        // the way out.
        status(HttpStatusCode.UnsupportedMediaType) { status ->
            call.respond(status, ErrorResponse("Send the body as application/json."))
        }
        exception<Throwable> { call, cause ->
            log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Something went wrong on our side."))
        }
    }
    installAccessTokenAuth()

    routing {
        get("/") { call.respondText("kmp-ktor-template server is up") }
        healthRoutes()
        authRoutes()
        legalRoutes()
        joinRoutes()
        authenticate(ACCESS_TOKEN_AUTH) {
            meRoutes()
            flagsRoutes()
            townRoutes()
            // EXTENSION POINT: mount new signed-in feature routes here (e.g., booksRoutes()).
        }
    }
}
