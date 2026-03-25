package com.franklinharper.social.media.client

import com.franklinharper.social.media.client.auth.ServerSessionService
import com.franklinharper.social.media.client.persistence.ServerDatabaseFactory
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        val authService = ServerSessionService(ServerDatabaseFactory.inMemory())
        application {
            module(authService)
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Ktor: ${Greeting().greet()}", response.bodyAsText())
    }
}
