package com.franklinharper.social.media.client

import com.franklinharper.social.media.client.api.registerFeedRoutes
import com.franklinharper.social.media.client.api.registerAuthRoutes
import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.client.bluesky.BlueskyClient
import com.franklinharper.social.media.client.client.rss.RssClient
import com.franklinharper.social.media.client.auth.ServerSessionService
import com.franklinharper.social.media.client.persistence.ServerApiDependencies
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
    val database by lazy { createDefaultDatabase() }
    val authService by lazy { ServerSessionService(database) }
    val dependencies by lazy { createDefaultDependencies(database) }
    module(
        authServiceProvider = { authService },
        dependenciesProvider = { dependencies },
    )
}

fun Application.module(
    authServiceProvider: () -> ServerSessionService,
    dependenciesProvider: () -> ServerApiDependencies,
) {
    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }
        registerAuthRoutes(authServiceProvider)
        registerFeedRoutes(authServiceProvider, dependenciesProvider)
    }
}

fun Application.module(authServiceProvider: () -> ServerSessionService) {
    val dependencies by lazy { createDefaultDependencies(createDefaultDatabase()) }
    module(authServiceProvider = authServiceProvider, dependenciesProvider = { dependencies })
}

fun Application.module(authService: ServerSessionService) {
    val dependencies by lazy { createDefaultDependencies(createDefaultDatabase()) }
    module(
        authServiceProvider = { authService },
        dependenciesProvider = { dependencies },
    )
}

fun Application.module(authService: ServerSessionService, dependencies: ServerApiDependencies) {
    module(
        authServiceProvider = { authService },
        dependenciesProvider = { dependencies },
    )
}

private fun createDefaultDatabase() = ServerDatabaseFactory.fileBacked(
    File(System.getProperty("user.dir"), "social-media-server.db"),
)

private fun createDefaultDependencies(database: com.franklinharper.social.media.client.db.SocialMediaDatabase): ServerApiDependencies =
    ServerApiDependencies(
        database = database,
        clientRegistry = ClientRegistry(
            listOf(
                RssClient(),
                BlueskyClient(),
            ),
        ),
    )
