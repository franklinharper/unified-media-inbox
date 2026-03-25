package com.franklinharper.social.media.client.api

import com.franklinharper.social.media.client.auth.AuthFailure
import com.franklinharper.social.media.client.auth.AuthenticatedUser
import com.franklinharper.social.media.client.auth.ServerSessionService
import com.franklinharper.social.media.client.persistence.ServerApiDependencies
import com.franklinharper.social.media.client.persistence.ServerRepositories
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

private val apiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Route.registerAuthRoutes(authServiceProvider: () -> ServerSessionService) {
    route("/api/auth") {
        post("/sign-in") {
            val authService = authServiceProvider()
            val request = try {
                apiJson.decodeFromString(SignInRequest.serializer(), call.receiveText())
            } catch (_: SerializationException) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val session = try {
                authService.signIn(request.email, request.password)
            } catch (e: CancellationException) {
                throw e
            } catch (_: AuthFailure) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val user = authService.requireUser(session.token)
            call.respondJson(
                AuthSessionResponse(
                    token = session.token,
                    user = AuthUserResponse(
                        userId = user.userId,
                        email = user.email,
                    ),
                ),
            )
        }

        get("/session") {
            val authService = authServiceProvider()
            val token = call.bearerToken() ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val user = try {
                authService.requireUser(token)
            } catch (e: CancellationException) {
                throw e
            } catch (_: AuthFailure) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            call.respondJson(
                AuthSessionResponse(
                    token = token,
                    user = AuthUserResponse(
                        userId = user.userId,
                        email = user.email,
                    ),
                ),
            )
        }

        post("/sign-out") {
            val authService = authServiceProvider()
            val token = call.bearerToken() ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            try {
                authService.requireUser(token)
            } catch (e: CancellationException) {
                throw e
            } catch (_: AuthFailure) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            authService.revokeSession(token)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

fun Route.registerFeedRoutes(
    authServiceProvider: () -> ServerSessionService,
    dependenciesProvider: () -> ServerApiDependencies,
) {
    route("/api") {
        get("/sources") {
            val user = call.requireAuthenticatedUser(authServiceProvider()) ?: return@get
            val repositories = ServerRepositories(dependenciesProvider()).forUser(user.userId)
            call.respondJson(ListSourcesResponse(repositories.listSources().map { it.toSourceDto() }))
        }

        post("/sources") {
            val user = call.requireAuthenticatedUser(authServiceProvider()) ?: return@post
            val request = try {
                apiJson.decodeFromString(AddSourceRequest.serializer(), call.receiveText())
            } catch (_: SerializationException) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val source = request.toDomainSourceOrNull() ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val repositories = ServerRepositories(dependenciesProvider()).forUser(user.userId)
            repositories.addSource(source)
            call.respond(HttpStatusCode.Created)
        }

        delete("/sources") {
            val user = call.requireAuthenticatedUser(authServiceProvider()) ?: return@delete
            val request = try {
                apiJson.decodeFromString(SourceDto.serializer(), call.receiveText())
            } catch (_: SerializationException) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }
            val source = request.toDomainSourceOrNull() ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }
            val repositories = ServerRepositories(dependenciesProvider()).forUser(user.userId)
            repositories.removeSource(source)
            call.respond(HttpStatusCode.NoContent)
        }

        get("/feed") {
            val user = call.requireAuthenticatedUser(authServiceProvider()) ?: return@get
            val includeSeen = call.request.queryParameters["includeSeen"]?.toBooleanStrictOrNull() ?: false
            val repositories = ServerRepositories(dependenciesProvider()).forUser(user.userId)
            call.respondJson(repositories.listFeed(includeSeen).toFeedResponse())
        }

        post("/feed/refresh") {
            val user = call.requireAuthenticatedUser(authServiceProvider()) ?: return@post
            val includeSeen = call.request.queryParameters["includeSeen"]?.toBooleanStrictOrNull() ?: false
            val repositories = ServerRepositories(dependenciesProvider()).forUser(user.userId)
            call.respondJson(repositories.refreshFeed(includeSeen).toFeedResponse())
        }

        post("/feed/seen") {
            val user = call.requireAuthenticatedUser(authServiceProvider()) ?: return@post
            val request = try {
                apiJson.decodeFromString(MarkSeenRequest.serializer(), call.receiveText())
            } catch (_: SerializationException) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val repositories = ServerRepositories(dependenciesProvider()).forUser(user.userId)
            repositories.markSeen(request.itemIds)
            call.respond(HttpStatusCode.OK)
        }
    }
}

private suspend inline fun <reified T> ApplicationCall.respondJson(value: T) {
    respondText(apiJson.encodeToString(value), ContentType.Application.Json)
}

private suspend fun ApplicationCall.requireAuthenticatedUser(
    authService: ServerSessionService,
): AuthenticatedUser? {
    val token = bearerToken() ?: run {
        respond(HttpStatusCode.Unauthorized)
        return null
    }
    return try {
        authService.requireUser(token)
    } catch (e: CancellationException) {
        throw e
    } catch (_: AuthFailure) {
        respond(HttpStatusCode.Unauthorized)
        null
    }
}

private fun ApplicationCall.bearerToken(): String? {
    val header = request.headers[HttpHeaders.Authorization] ?: return null
    val parts = header.split(' ', limit = 2)
    if (parts.size != 2) return null
    if (!parts[0].equals("Bearer", ignoreCase = true)) return null
    return parts[1].takeIf { it.isNotBlank() }
}
