package com.franklinharper.social.media.client

import com.franklinharper.social.media.client.api.AddSourceRequest
import com.franklinharper.social.media.client.api.FeedResponse
import com.franklinharper.social.media.client.api.MarkSeenRequest
import com.franklinharper.social.media.client.auth.ServerSessionService
import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.client.fake.FakeBlueskyClient
import com.franklinharper.social.media.client.client.fake.FakeRssClient
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SeenState
import com.franklinharper.social.media.client.persistence.ServerApiDependencies
import com.franklinharper.social.media.client.persistence.ServerDatabaseFactory
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FeedApiTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `refresh performs server-side fetch and returns feed items`() = testApplication {
        val database = ServerDatabaseFactory.inMemory()
        val authService = createAuthService(database)
        val aliceToken = authService.signIn("alice@example.com", "secret").token
        val bobToken = authService.signIn("bob@example.com", "secret").token
        application {
            module(
                authService = authService,
                dependencies = createDependencies(database),
            )
        }
        addSource(aliceToken, AddSourceRequest.rss("https://example.com/feed.xml"))

        val response = client.post("/api/feed/refresh") {
            header(HttpHeaders.Authorization, "Bearer $aliceToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = decodeFeed(response.bodyAsText())
        assertEquals(listOf("rss-1"), body.items.map { it.itemId })
        assertEquals(emptyList(), feedItems(bobToken))
    }

    @Test
    fun `feed seen updates stay scoped to authenticated user`() = testApplication {
        val database = ServerDatabaseFactory.inMemory()
        val authService = createAuthService(database)
        val aliceToken = authService.signIn("alice@example.com", "secret").token
        val bobToken = authService.signIn("bob@example.com", "secret").token
        application {
            module(
                authService = authService,
                dependencies = createDependencies(database),
            )
        }
        addSource(aliceToken, AddSourceRequest.rss("https://example.com/feed.xml"))
        addSource(bobToken, AddSourceRequest.rss("https://example.com/feed.xml"))
        refreshFeed(aliceToken)
        refreshFeed(bobToken)

        val markSeenResponse = client.post("/api/feed/seen") {
            header(HttpHeaders.Authorization, "Bearer $aliceToken")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(MarkSeenRequest(itemIds = listOf("rss:rss-1"))))
        }

        assertEquals(HttpStatusCode.OK, markSeenResponse.status)
        assertEquals(emptyList(), feedItems(aliceToken))
        assertEquals(listOf("rss-1"), feedItems(bobToken))
        assertTrue(decodeFeed(getFeed(aliceToken, includeSeen = true).bodyAsText()).items.single().seen)
        assertFalse(decodeFeed(getFeed(bobToken, includeSeen = true).bodyAsText()).items.single().seen)
    }

    private suspend fun ApplicationTestBuilder.addSource(token: String, request: AddSourceRequest) {
        client.post("/api/sources") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }
    }

    private suspend fun ApplicationTestBuilder.refreshFeed(token: String) {
        client.post("/api/feed/refresh") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    private suspend fun ApplicationTestBuilder.feedItems(token: String): List<String> =
        decodeFeed(getFeed(token).bodyAsText()).items.map { it.itemId }

    private suspend fun ApplicationTestBuilder.getFeed(
        token: String,
        includeSeen: Boolean = false,
    ) = client.get("/api/feed?includeSeen=$includeSeen") {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun decodeFeed(body: String): FeedResponse =
        json.decodeFromString(body)

    private suspend fun createAuthService(database: com.franklinharper.social.media.client.db.SocialMediaDatabase): ServerSessionService {
        val authService = ServerSessionService(database)
        authService.createUser("alice@example.com", "secret")
        authService.createUser("bob@example.com", "secret")
        return authService
    }

    private fun createDependencies(database: com.franklinharper.social.media.client.db.SocialMediaDatabase): ServerApiDependencies {
        val rssSource = FeedSource(PlatformId.Rss, "https://example.com/feed.xml", "https://example.com/feed.xml")
        val blueskySource = FeedSource(PlatformId.Bluesky, "bob", "bob")
        return ServerApiDependencies(
            database = database,
            clientRegistry = ClientRegistry(
                listOf(
                    FakeRssClient(
                        itemsByUrl = mapOf(
                            rssSource.sourceId to listOf(feedItem("rss-1", PlatformId.Rss, rssSource, 100L)),
                        ),
                    ),
                    FakeBlueskyClient(
                        itemsByUser = mapOf(
                            blueskySource.sourceId to listOf(feedItem("bsky-1", PlatformId.Bluesky, blueskySource, 200L)),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun feedItem(
        id: String,
        platformId: PlatformId,
        source: FeedSource,
        publishedAt: Long,
    ): FeedItem = FeedItem(
        itemId = id,
        platformId = platformId,
        source = source,
        authorName = source.displayName,
        title = id,
        body = id,
        permalink = null,
        publishedAtEpochMillis = publishedAt,
        seenState = SeenState.Unseen,
    )
}
