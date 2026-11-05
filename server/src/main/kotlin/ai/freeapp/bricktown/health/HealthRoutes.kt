package ai.freeapp.bricktown.health

import ai.freeapp.bricktown.config.DatabaseFactory
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoutes() {
    get("/health") {
        val dbOk = runCatching {
            DatabaseFactory.dataSource.connection.use { it.isValid(1) }
        }.getOrDefault(false)

        if (dbOk) {
            call.respond(mapOf("status" to "ok", "db" to "up"))
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "degraded", "db" to "down"))
        }
    }
}
