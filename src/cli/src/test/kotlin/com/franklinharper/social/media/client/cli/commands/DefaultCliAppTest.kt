package com.franklinharper.social.media.client.cli.commands

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.client.fake.FakeBlueskyClient
import com.franklinharper.social.media.client.client.fake.FakeTwitterClient
import com.franklinharper.social.media.client.client.rss.RssClient
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultCliAppTest {

    @Test
    fun `list-new-items uses persisted rss feeds`() = runBlocking {
        val dbFile = File.createTempFile("social-cli-test", ".db")
        dbFile.deleteOnExit()
        val app = DefaultCliApp(
            databasePath = dbFile,
            clientRegistry = ClientRegistry(
                listOf(
                    RssClient(fetcher = { RSS_XML }),
                    FakeBlueskyClient(itemsByUser = emptyMap()),
                    FakeTwitterClient(itemsByUser = emptyMap()),
                ),
            ),
        )

        app.run(listOf("add-feed", "--url", "https://example.com/feed.xml"))
        val result = app.run(listOf("list-new-items"))

        assertIs<CliResult.Success>(result)
        assertEquals(true, result.output.contains("Second item"))
        assertEquals(true, result.output.contains("First item"))
    }

    @Test
    fun `mark-seen hides items on the next run`() = runBlocking {
        val dbFile = File.createTempFile("social-cli-test", ".db")
        dbFile.deleteOnExit()
        val app = DefaultCliApp(
            databasePath = dbFile,
            clientRegistry = ClientRegistry(
                listOf(
                    RssClient(fetcher = { RSS_XML }),
                    FakeBlueskyClient(itemsByUser = emptyMap()),
                    FakeTwitterClient(itemsByUser = emptyMap()),
                ),
            ),
        )

        app.run(listOf("add-feed", "--url", "https://example.com/feed.xml"))
        val firstRun = app.run(listOf("list-new-items", "--mark-seen"))
        val secondRun = app.run(listOf("list-new-items"))

        assertIs<CliResult.Success>(firstRun)
        assertIs<CliResult.Success>(secondRun)
        assertEquals(true, firstRun.output.contains("Second item"))
        assertEquals("", secondRun.output)
    }

    private companion object {
        const val RSS_XML = """
            <rss version="2.0">
              <channel>
                <title>CLI Feed</title>
                <item>
                  <title>First item</title>
                  <guid>guid-1</guid>
                  <pubDate>Fri, 20 Mar 2026 10:00:00 GMT</pubDate>
                </item>
                <item>
                  <title>Second item</title>
                  <guid>guid-2</guid>
                  <pubDate>Fri, 20 Mar 2026 11:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
        """
    }
}
