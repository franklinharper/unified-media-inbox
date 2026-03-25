package com.franklinharper.social.media.client.remote

import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedRequest
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class WebRemoteRepositoriesTest {

    @Test
    fun `remote configured source repository posts add-source request`() = runBlocking {
        val http = FakeWebApiHttp()
        val repository = WebRemoteConfiguredSourceRepository(http)

        repository.addSource(ConfiguredSource.RssFeed(url = "https://example.com/feed.xml"))

        assertEquals("/api/sources", http.lastPostPath)
        assertEquals(
            """{"platformId":"rss","kind":"rss_feed","value":"https://example.com/feed.xml"}""",
            http.lastPostBody,
        )
    }

    @Test
    fun `remote feed repository translates feed response into shared result`() = runBlocking {
        val repository = WebRemoteFeedRepository(
            FakeWebApiHttp(
                postResponses = mapOf(
                    "/api/feed/refresh?includeSeen=false" to WebApiResponse(
                        statusCode = 200,
                        body = """
                            {
                              "items": [
                                {
                                  "itemId": "rss:rss-1",
                                  "platformId": "rss",
                                  "source": {
                                    "platformId": "rss",
                                    "sourceId": "https://example.com/feed.xml",
                                    "displayName": "Example Feed"
                                  },
                                  "authorName": "Example Feed",
                                  "title": "Item 1",
                                  "body": "Body 1",
                                  "permalink": "https://example.com/items/1",
                                  "commentsPermalink": null,
                                  "publishedAtEpochMillis": 123,
                                  "seen": false
                                }
                              ],
                              "sourceStatuses": [
                                {
                                  "source": {
                                    "platformId": "rss",
                                    "sourceId": "https://example.com/feed.xml",
                                    "displayName": "Example Feed"
                                  },
                                  "state": "success",
                                  "contentOrigin": "refresh"
                                }
                              ]
                            }
                        """.trimIndent(),
                    ),
                ),
            ),
        )

        val result = repository.loadFeedItems(
            FeedRequest(
                sources = listOf(ConfiguredSource.RssFeed(url = "https://example.com/feed.xml")),
                includeSeen = false,
            ),
        )

        assertEquals("rss:rss-1", result.items.single().itemId)
        assertEquals("Example Feed", result.items.single().source.displayName)
        assertEquals(PlatformId.Rss, result.sourceStatuses.single().source.platformId)
    }

    @Test
    fun `remote source repository maps unauthorized to authentication failure`() = runBlocking {
        val repository = WebRemoteConfiguredSourceRepository(
            FakeWebApiHttp(
                getResponses = mapOf(
                    "/api/sources" to WebApiResponse(statusCode = 401, body = ""),
                ),
            ),
        )

        val failure = assertFailsWith<WebApiException> {
            repository.listSources()
        }

        assertIs<com.franklinharper.social.media.client.domain.ClientError.AuthenticationError>(failure.clientError)
        Unit
    }

    @Test
    fun `remote session repository restores signed in state from auth session response`() = runBlocking {
        val http = FakeWebApiHttp(
            initialBearerToken = "token-123",
            getResponses = mapOf(
                "/api/auth/session" to WebApiResponse(
                    statusCode = 200,
                    body = """
                        {
                          "token": "token-123",
                          "user": {
                            "userId": "user-1",
                            "email": "alice@example.com"
                          }
                        }
                    """.trimIndent(),
                ),
            ),
        )
        val repository = WebRemoteSessionRepository(http)

        val state = repository.getSessionState(PlatformId.Bluesky)

        val signedIn = assertIs<SessionState.SignedIn>(state)
        assertEquals(
            AccountSession(
                accountId = "user-1",
                accessToken = "token-123",
            ),
            signedIn.session,
        )
    }

    @Test
    fun `remote session repository signs in and stores bearer token`() = runBlocking {
        val http = FakeWebApiHttp(
            postResponses = mapOf(
                "/api/auth/sign-in" to WebApiResponse(
                    statusCode = 200,
                    body = """
                        {
                          "token": "token-abc",
                          "user": {
                            "userId": "user-9",
                            "email": "alice@example.com"
                          }
                        }
                    """.trimIndent(),
                ),
            ),
        )
        val repository = WebRemoteSessionRepository(http)

        val state = repository.signIn("alice@example.com", "secret")

        val signedIn = assertIs<SessionState.SignedIn>(state)
        assertEquals("token-abc", signedIn.session.accessToken)
        assertEquals("token-abc", http.bearerToken)
        assertEquals("/api/auth/sign-in", http.lastPostPath)
    }

    @Test
    fun `remote session repository signs up and stores bearer token`() = runBlocking {
        val http = FakeWebApiHttp(
            postResponses = mapOf(
                "/api/auth/sign-up" to WebApiResponse(
                    statusCode = 200,
                    body = """
                        {
                          "token": "token-signup",
                          "user": {
                            "userId": "user-42",
                            "email": "new@example.com"
                          }
                        }
                    """.trimIndent(),
                ),
            ),
        )
        val repository = WebRemoteSessionRepository(http)

        val state = repository.signUp("new@example.com", "secret")

        val signedIn = assertIs<SessionState.SignedIn>(state)
        assertEquals("token-signup", signedIn.session.accessToken)
        assertEquals("token-signup", http.bearerToken)
        assertEquals("/api/auth/sign-up", http.lastPostPath)
        assertEquals("""{"email":"new@example.com","password":"secret"}""", http.lastPostBody)
    }
}

private class FakeWebApiHttp(
    private val getResponses: Map<String, WebApiResponse> = emptyMap(),
    private val postResponses: Map<String, WebApiResponse> = emptyMap(),
    private val deleteResponses: Map<String, WebApiResponse> = emptyMap(),
    initialBearerToken: String? = null,
) : WebApiHttp {
    override var bearerToken: String? = initialBearerToken

    var lastGetPath: String? = null
    var lastPostPath: String? = null
    var lastPostBody: String? = null
    var lastDeletePath: String? = null
    var lastDeleteBody: String? = null

    override suspend fun get(path: String): WebApiResponse {
        lastGetPath = path
        return getResponses[path] ?: WebApiResponse(statusCode = 200, body = "")
    }

    override suspend fun post(path: String, body: String?): WebApiResponse {
        lastPostPath = path
        lastPostBody = body
        return postResponses[path] ?: WebApiResponse(statusCode = 200, body = "")
    }

    override suspend fun delete(path: String, body: String?): WebApiResponse {
        lastDeletePath = path
        lastDeleteBody = body
        return deleteResponses[path] ?: WebApiResponse(statusCode = 204, body = "")
    }
}
