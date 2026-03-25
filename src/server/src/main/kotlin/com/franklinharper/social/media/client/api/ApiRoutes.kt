package com.franklinharper.social.media.client.api

import com.franklinharper.social.media.client.auth.ServerSessionService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

private val apiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Route.registerAuthRoutes(authService: ServerSessionService) {
    route("/api/auth") {
        post("/sign-in") {
            val request = try {
                apiJson.decodeFromString(SignInRequest.serializer(), call.receiveText())
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val session = try {
                authService.signIn(request.email, request.password)
            } catch (_: Exception) {
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
            val token = call.bearerToken() ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val user = try {
                authService.requireUser(token)
            } catch (_: Exception) {
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
            val token = call.bearerToken() ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            try {
                authService.requireUser(token)
            } catch (_: Exception) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            authService.revokeSession(token)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private suspend inline fun <reified T> ApplicationCall.respondJson(value: T) {
    respondText(apiJson.encodeToString(value), ContentType.Application.Json)
}

private fun ApplicationCall.bearerToken(): String? {
    val header = request.headers[HttpHeaders.Authorization] ?: return null
    val parts = header.split(' ', limit = 2)
    if (parts.size != 2) return null
    if (!parts[0].equals("Bearer", ignoreCase = true)) return null
    return parts[1].takeIf { it.isNotBlank() }
}
