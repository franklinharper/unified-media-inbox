package com.franklinharper.social.media.client

import com.franklinharper.social.media.client.api.AddSourceRequest
import com.franklinharper.social.media.client.api.ListSourcesResponse
import com.franklinharper.social.media.client.auth.ServerSessionService
import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.client.fake.FakeBlueskyClient
import com.franklinharper.social.media.client.client.fake.FakeRssClient
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.persistence.ServerApiDependencies
import com.franklinharper.social.media.client.persistence.ServerDatabaseFactory
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SourceApiTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `sources endpoints require authentication`() = testApplication {
        application {
            module(
                authService = createAuthService(),
                dependencies = createDependencies(),
            )
        }

        val response = client.get("/api/sources")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `add source persists source for authenticated user`() = testApplication {
        val authService = createAuthService()
        val aliceToken = authService.signIn("alice@example.com", "secret").token
        val bobToken = authService.signIn("bob@example.com", "secret").token
        application {
            module(
                authService = authService,
                dependencies = createDependencies(),
            )
        }

        val response = authedPost(aliceToken, "/api/sources", AddSourceRequest.rss("https://example.com/feed.xml"))

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(
            listOf(ConfiguredSource.RssFeed(url = "https://example.com/feed.xml")),
            listSources(aliceToken),
        )
        assertEquals(emptyList(), listSources(bobToken))
    }

    @Test
    fun `delete source removes only authenticated users source`() = testApplication {
        val authService = createAuthService()
        val aliceToken = authService.signIn("alice@example.com", "secret").token
        val bobToken = authService.signIn("bob@example.com", "secret").token
        application {
            module(
                authService = authService,
                dependencies = createDependencies(),
            )
        }

        authedPost(aliceToken, "/api/sources", AddSourceRequest.rss("https://example.com/alice.xml"))
        authedPost(bobToken, "/api/sources", AddSourceRequest.socialUser("bluesky", "bob"))

        val response = client.delete("/api/sources") {
            header(HttpHeaders.Authorization, "Bearer $aliceToken")
            contentType(ContentType.Application.Json)
            setBody("""{"platformId":"rss","kind":"rss_feed","value":"https://example.com/alice.xml"}""")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(emptyList(), listSources(aliceToken))
        assertEquals(
            listOf(ConfiguredSource.SocialUser(platformId = PlatformId.Bluesky, user = "bob")),
            listSources(bobToken),
        )
    }

    @Test
    fun `add source returns bad request for unsupported platform id`() = testApplication {
        val authService = createAuthService()
        val token = authService.signIn("alice@example.com", "secret").token
        application {
            module(
                authService = authService,
                dependencies = createDependencies(),
            )
        }

        val response = authedJsonRequest(
            token = token,
            method = "POST",
            path = "/api/sources",
            body = """{"platformId":"mastodon","kind":"social_user","value":"alice"}""",
        )

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `delete source returns bad request for unsupported source kind`() = testApplication {
        val authService = createAuthService()
        val token = authService.signIn("alice@example.com", "secret").token
        application {
            module(
                authService = authService,
                dependencies = createDependencies(),
            )
        }

        val response = authedJsonRequest(
            token = token,
            method = "DELETE",
            path = "/api/sources",
            body = """{"platformId":"rss","kind":"newsletter","value":"https://example.com/feed.xml"}""",
        )

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.listSources(token: String): List<ConfiguredSource> {
        val response = client.get("/api/sources") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        return json.decodeFromString<ListSourcesResponse>(response.bodyAsText()).sources.map { source ->
            when (source.kind) {
                "rss_feed" -> ConfiguredSource.RssFeed(url = source.value)
                "social_user" -> ConfiguredSource.SocialUser(platformId = source.platformId.toPlatformId(), user = source.value)
                else -> error("Unexpected source kind: ${source.kind}")
            }
        }
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.authedPost(
        token: String,
        path: String,
        request: AddSourceRequest,
    ): HttpResponse =
        client.post(path) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.authedJsonRequest(
        token: String,
        method: String,
        path: String,
        body: String,
    ): HttpResponse =
        when (method) {
            "POST" -> client.post(path) {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            "DELETE" -> client.delete(path) {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            else -> error("Unsupported method: $method")
        }

    private suspend fun createAuthService(database: com.franklinharper.social.media.client.db.SocialMediaDatabase = ServerDatabaseFactory.inMemory()): ServerSessionService {
        val authService = ServerSessionService(database)
        authService.createUser("alice@example.com", "secret")
        authService.createUser("bob@example.com", "secret")
        return authService
    }

    private fun createDependencies(database: com.franklinharper.social.media.client.db.SocialMediaDatabase = ServerDatabaseFactory.inMemory()): ServerApiDependencies =
        ServerApiDependencies(
            database = database,
            clientRegistry = ClientRegistry(
                listOf(
                    FakeRssClient(emptyMap()),
                    FakeBlueskyClient(emptyMap()),
                ),
            ),
        )

    private fun String.toPlatformId(): PlatformId = when (lowercase()) {
        "rss" -> PlatformId.Rss
        "bluesky" -> PlatformId.Bluesky
        "twitter" -> PlatformId.Twitter
        else -> error("Unsupported platform id: $this")
    }
}
