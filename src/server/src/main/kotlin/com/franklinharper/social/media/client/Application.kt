package com.franklinharper.social.media.client

import com.franklinharper.social.media.client.api.registerAuthRoutes
import com.franklinharper.social.media.client.auth.ServerSessionService
import com.franklinharper.social.media.client.persistence.ServerDatabaseFactory
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = { module() })
        .start(wait = true)
}

fun Application.module() {
    val authService by lazy { createDefaultAuthService() }
    module { authService }
}

fun Application.module(authServiceProvider: () -> ServerSessionService) {
    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }
        registerAuthRoutes(authServiceProvider)
    }
}

fun Application.module(authService: ServerSessionService) {
    module { authService }
}

private fun createDefaultAuthService(): ServerSessionService {
    val databaseFile = File(System.getProperty("user.dir"), "social-media-server.db")
    return ServerSessionService(ServerDatabaseFactory.fileBacked(databaseFile))
}
