package com.franklinharper.social.media.client.client.rss

import com.franklinharper.social.media.client.domain.FeedQuery
import com.franklinharper.social.media.client.domain.PlatformId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RssClientTest {

    @Test
    fun `parses rss feeds into normalized items`() = runBlocking {
        val client = RssClient(fetcher = { RSS_XML })

        val page = client.loadFeed(
            FeedQuery.RssFeeds(urls = listOf("https://example.com/feed.xml")),
        )

        assertEquals(2, page.items.size)
        assertEquals("Feed Title", page.items.first().source.displayName)
        assertEquals(PlatformId.Rss, page.items.first().platformId)
        assertEquals("guid-2", page.items.first().itemId)
        assertEquals("https://example.com/comments/2", page.items.first().commentsPermalink)
    }

    @Test
    fun `parses atom feeds into normalized items`() = runBlocking {
        val client = RssClient(fetcher = { ATOM_XML })

        val page = client.loadFeed(
            FeedQuery.RssFeeds(urls = listOf("https://example.com/atom.xml")),
        )

        assertEquals(1, page.items.size)
        assertEquals("Atom Feed", page.items.single().source.displayName)
        assertEquals("tag:example.com,2026:1", page.items.single().itemId)
        assertEquals("https://example.com/posts/1", page.items.single().permalink)
    }

    @Test
    fun `malformed xml fails with parsing error`() = runBlocking {
        val client = RssClient(fetcher = { MALFORMED_XML })
        assertFailsWith<RssClientException> {
            client.loadFeed(
                FeedQuery.RssFeeds(urls = listOf("https://example.com/bad.xml")),
            )
        }
        Unit
    }

    private companion object {
        const val RSS_XML = """
            <rss version="2.0">
              <channel>
                <title>Feed Title</title>
                <item>
                  <title>First item</title>
                  <link>https://example.com/posts/1</link>
                  <guid>guid-1</guid>
                  <pubDate>Fri, 20 Mar 2026 10:00:00 GMT</pubDate>
                  <description>Item one</description>
                </item>
                <item>
                  <title>Second item</title>
                  <link>https://example.com/posts/2</link>
                  <guid>guid-2</guid>
                  <pubDate>Fri, 20 Mar 2026 11:00:00 GMT</pubDate>
                  <description>Item two</description>
                  <comments>https://example.com/comments/2</comments>
                </item>
              </channel>
            </rss>
        """

        const val ATOM_XML = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Feed</title>
              <entry>
                <title>Atom item</title>
                <id>tag:example.com,2026:1</id>
                <updated>2026-03-20T11:00:00Z</updated>
                <summary>Atom summary</summary>
                <link rel="alternate" href="https://example.com/posts/1" />
                <author>
                  <name>Frank</name>
                </author>
              </entry>
            </feed>
        """

        const val MALFORMED_XML = """
            <rss version="2.0">
              <channel>
                <title>Broken Feed</title>
              </channe
            </rss>
        """
    }
}
