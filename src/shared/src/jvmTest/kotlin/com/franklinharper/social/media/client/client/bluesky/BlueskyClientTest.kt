package com.franklinharper.social.media.client.client.bluesky

import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.FeedQuery
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SessionState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BlueskyClientTest {

    @Test
    fun `loadFeed maps author feed response into normalized feed items`() = runBlocking {
        val client = BlueskyClient(
            fetchAuthorFeed = { actor, _ ->
                assertEquals("frank", actor)
                AUTHOR_FEED_JSON
            },
        )

        val page = client.loadFeed(
            FeedQuery.SocialUsers(platformId = PlatformId.Bluesky, users = listOf("frank")),
        )

        assertEquals(1, page.items.size)
        val item = page.items.single()
        assertEquals("at://did:plc:abc/app.bsky.feed.post/3jt7", item.itemId)
        assertEquals("Hello from Bluesky", item.body)
        assertEquals("Frank Harper", item.authorName)
        assertEquals("https://bsky.app/profile/frank.bsky.social/post/3jt7", item.permalink)
        assertEquals("frank", item.source.sourceId)
    }

    @Test
    fun `signIn maps createSession response into account session`() = runBlocking {
        val client = BlueskyClient(
            createSession = { identifier, password ->
                assertEquals("frank.bsky.social", identifier)
                assertEquals("app-password", password)
                CREATE_SESSION_JSON
            },
        )

        val session = client.signIn("frank.bsky.social", "app-password")

        assertEquals("did:plc:abc", session.accountId)
        assertEquals("access-token", session.accessToken)
        assertEquals("refresh-token", session.refreshToken)
    }

    @Test
    fun `sessionState reflects configured session provider`() = runBlocking {
        val client = BlueskyClient(
            sessionProvider = {
                com.franklinharper.social.media.client.domain.AccountSession(
                    accountId = "did:plc:abc",
                    accessToken = "access-token",
                )
            },
        )

        val state = client.sessionState()

        assertIs<SessionState.SignedIn>(state)
        assertEquals("did:plc:abc", state.session.accountId)
    }

    @Test
    fun `loadFeed converts request failures into client errors`() = runBlocking {
        val client = BlueskyClient(
            fetchAuthorFeed = { _, _ -> throw IllegalStateException("offline") },
        )

        val error = try {
            client.loadFeed(
                FeedQuery.SocialUsers(platformId = PlatformId.Bluesky, users = listOf("frank")),
            )
            error("Expected loadFeed to fail")
        } catch (error: Throwable) {
            error
        }

        val clientError = assertIs<BlueskyClientException>(error).clientError
        assertIs<ClientError.NetworkError>(clientError)
        assertEquals("offline", clientError.message)
    }

    private companion object {
        const val AUTHOR_FEED_JSON = """
            {
              "cursor": "cursor-1",
              "feed": [
                {
                  "post": {
                    "uri": "at://did:plc:abc/app.bsky.feed.post/3jt7",
                    "author": {
                      "did": "did:plc:abc",
                      "handle": "frank.bsky.social",
                      "displayName": "Frank Harper"
                    },
                    "record": {
                      "text": "Hello from Bluesky",
                      "createdAt": "2026-03-20T10:15:30Z"
                    },
                    "indexedAt": "2026-03-20T10:15:35Z"
                  }
                }
              ]
            }
        """

        const val CREATE_SESSION_JSON = """
            {
              "did": "did:plc:abc",
              "handle": "frank.bsky.social",
              "accessJwt": "access-token",
              "refreshJwt": "refresh-token"
            }
        """
    }
}
