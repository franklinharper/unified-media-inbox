package com.franklinharper.social.media.client.client.twitter

import com.franklinharper.social.media.client.client.NetworkResponse
import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.FeedQuery
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SessionState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TwitterClientTest {

    @Test
    fun `loadFeed maps recent tweets into normalized feed items`() = runBlocking {
        val client = TwitterClient(
            sessionProvider = { AccountSession(accountId = "twitter-app", accessToken = "token") },
            fetchRecentTweets = { handle, cursor, token ->
                assertEquals("frank", handle)
                assertEquals(null, cursor)
                assertEquals("token", token)
                NetworkResponse(statusCode = 200, body = SEARCH_TWEETS_JSON, headers = emptyMap())
            },
        )

        val page = client.loadFeed(
            FeedQuery.SocialUsers(platformId = PlatformId.Twitter, users = listOf("@frank")),
        )

        assertEquals(1, page.items.size)
        val item = page.items.single()
        assertEquals("12345", item.itemId)
        assertEquals("Hello from X", item.body)
        assertEquals("Frank Harper", item.authorName)
        assertEquals("https://twitter.com/frank/status/12345", item.permalink)
        assertEquals("@frank", item.source.sourceId)
        assertEquals("next-1", page.nextCursor?.value)
    }

    @Test
    fun `loadProfile maps user response into social profile`() = runBlocking {
        val client = TwitterClient(
            sessionProvider = { AccountSession(accountId = "twitter-app", accessToken = "token") },
            fetchProfileByUsername = { handle, token ->
                assertEquals("frank", handle)
                assertEquals("token", token)
                NetworkResponse(statusCode = 200, body = USER_JSON, headers = emptyMap())
            },
        )

        val profile = client.loadProfile("frank")

        assertEquals("2244994945", profile.accountId)
        assertEquals("Frank Harper", profile.displayName)
        assertEquals("frank", profile.handle)
    }

    @Test
    fun `sessionState reflects configured access token`() = runBlocking {
        val client = TwitterClient(
            sessionProvider = { AccountSession(accountId = "twitter-app", accessToken = "token") },
        )

        val state = client.sessionState()

        assertIs<SessionState.SignedIn>(state)
        assertEquals("twitter-app", state.session.accountId)
    }

    @Test
    fun `loadFeed converts rate limits into client errors`() = runBlocking {
        val client = TwitterClient(
            sessionProvider = { AccountSession(accountId = "twitter-app", accessToken = "token") },
            fetchRecentTweets = { _, _, _ ->
                NetworkResponse(
                    statusCode = 429,
                    body = """{"detail":"Rate limit exceeded"}""",
                    headers = mapOf("retry-after" to "15"),
                )
            },
        )

        val error = try {
            client.loadFeed(
                FeedQuery.SocialUsers(platformId = PlatformId.Twitter, users = listOf("frank")),
            )
            error("Expected loadFeed to fail")
        } catch (error: Throwable) {
            error
        }

        val clientError = assertIs<TwitterClientException>(error).clientError
        val rateLimitError = assertIs<ClientError.RateLimitError>(clientError)
        assertEquals(15_000L, rateLimitError.retryAfterMillis)
    }

    private companion object {
        const val SEARCH_TWEETS_JSON = """
            {
              "data": [
                {
                  "id": "12345",
                  "text": "Hello from X",
                  "author_id": "2244994945",
                  "created_at": "2026-03-20T10:15:30Z"
                }
              ],
              "includes": {
                "users": [
                  {
                    "id": "2244994945",
                    "name": "Frank Harper",
                    "username": "frank"
                  }
                ]
              },
              "meta": {
                "next_token": "next-1"
              }
            }
        """

        const val USER_JSON = """
            {
              "data": {
                "id": "2244994945",
                "name": "Frank Harper",
                "username": "frank"
              }
            }
        """
    }
}
