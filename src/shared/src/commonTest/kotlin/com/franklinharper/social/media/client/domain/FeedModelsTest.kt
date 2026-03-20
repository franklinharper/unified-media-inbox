package com.franklinharper.social.media.client.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedModelsTest {

    @Test
    fun `rss feed defaults to rss platform`() {
        val source = ConfiguredSource.RssFeed(url = "https://example.com/feed.xml")
        val query = FeedQuery.RssFeeds(urls = listOf(source.url))

        assertEquals(PlatformId.Rss, source.platformId)
        assertEquals(PlatformId.Rss, query.platformId)
    }

    @Test
    fun `feed request excludes seen items by default`() {
        val request = FeedRequest(
            sources = listOf(ConfiguredSource.SocialUser(PlatformId.Bluesky, "frank")),
        )

        assertFalse(request.includeSeen)
    }

    @Test
    fun `signed in session retains account data`() {
        val session = AccountSession(
            accountId = "user-123",
            accessToken = "token",
            expiresAtEpochMillis = 42L,
        )

        val state = SessionState.SignedIn(session)

        assertEquals("user-123", state.session.accountId)
        assertEquals("token", state.session.accessToken)
        assertTrue(state.session.expiresAtEpochMillis == 42L)
    }
}
