package com.franklinharper.social.media.client

import com.franklinharper.social.media.client.api.AuthSessionResponse
import com.franklinharper.social.media.client.auth.ServerSessionService
import com.franklinharper.social.media.client.persistence.ServerDatabaseFactory
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class AuthApiTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `sign in returns session for valid email and password`() = testApplication {
        val authService = createAuthService()
        application {
            module(authService)
        }

        val response = client.post("/api/auth/sign-in") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"alice@example.com","password":"secret"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<AuthSessionResponse>(response.bodyAsText())
        assertEquals("alice@example.com", body.user.email)
        assertTrue(body.token.isNotBlank())
    }

    @Test
    fun `session restore returns user for valid bearer token`() = testApplication {
        val authService = createAuthService()
        val token = authService.signIn("alice@example.com", "secret").token
        application {
            module(authService)
        }

        val response = client.get("/api/auth/session") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<AuthSessionResponse>(response.bodyAsText())
        assertEquals(token, body.token)
        assertEquals("alice@example.com", body.user.email)
    }

    @Test
    fun `session restore returns unauthorized for invalid token`() = testApplication {
        val authService = createAuthService()
        application {
            module(authService)
        }

        val response = client.get("/api/auth/session") {
            header(HttpHeaders.Authorization, "Bearer bad-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `sign out revokes bearer token`() = testApplication {
        val authService = createAuthService()
        val token = authService.signIn("alice@example.com", "secret").token
        application {
            module(authService)
        }

        val response = client.post("/api/auth/sign-out") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)

        val followUp = client.get("/api/auth/session") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Unauthorized, followUp.status)
    }

    private suspend fun createAuthService(): ServerSessionService {
        val database = ServerDatabaseFactory.inMemory()
        val authService = ServerSessionService(database)
        authService.createUser("alice@example.com", "secret")
        return authService
    }
}
