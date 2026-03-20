package com.franklinharper.social.media.client.cli.commands

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.client.fake.FakeBlueskyClient
import com.franklinharper.social.media.client.client.fake.FakeRssClient
import com.franklinharper.social.media.client.client.fake.FakeTwitterClient
import com.franklinharper.social.media.client.client.rss.RssClient
import com.franklinharper.social.media.client.db.JvmDatabaseFactory
import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.ClientError
import com.franklinharper.social.media.client.domain.FeedItem
import com.franklinharper.social.media.client.domain.FeedSource
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SeenState
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.repository.SqlDelightSessionRepository
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
        assertEquals(true, result.output.contains("STATUS rss https://example.com/feed.xml refreshed 2 item(s)"))
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
        assertEquals(true, secondRun.output.contains("STATUS rss https://example.com/feed.xml refreshed 0 item(s)"))
    }

    @Test
    fun `list-new-items reports cached fallback when refresh fails`() = runBlocking {
        val dbFile = File.createTempFile("social-cli-test", ".db")
        dbFile.deleteOnExit()
        val source = FeedSource(PlatformId.Rss, "https://example.com/feed.xml", "CLI Feed")
        val initialApp = DefaultCliApp(
            databasePath = dbFile,
            clientRegistry = ClientRegistry(
                listOf(
                    FakeRssClient(
                        itemsByUrl = mapOf(
                            source.sourceId to listOf(feedItem("cached-1", source, 100L)),
                        ),
                    ),
                    FakeBlueskyClient(itemsByUser = emptyMap()),
                    FakeTwitterClient(itemsByUser = emptyMap()),
                ),
            ),
        )
        initialApp.run(listOf("add-feed", "--url", source.sourceId))
        initialApp.run(listOf("list-new-items"))

        val failingApp = DefaultCliApp(
            databasePath = dbFile,
            clientRegistry = ClientRegistry(
                listOf(
                    FakeRssClient(
                        itemsByUrl = emptyMap(),
                        errorsByUrl = mapOf(source.sourceId to ClientError.NetworkError("offline")),
                    ),
                    FakeBlueskyClient(itemsByUser = emptyMap()),
                    FakeTwitterClient(itemsByUser = emptyMap()),
                ),
            ),
        )

        val result = failingApp.run(listOf("list-new-items"))

        assertIs<CliResult.Success>(result)
        assertEquals(true, result.output.contains("STATUS rss https://example.com/feed.xml using cache with 1 item(s) after network error: offline"))
        assertEquals(true, result.output.contains("cached-1"))
    }

    @Test
    fun `list-new-items fails when no sources are configured`() = runBlocking {
        val dbFile = File.createTempFile("social-cli-test", ".db")
        dbFile.deleteOnExit()
        val app = DefaultCliApp(databasePath = dbFile)

        val result = app.run(listOf("list-new-items"))

        assertEquals(
            CliResult.Failure("No sources configured. Use add-feed or add-user, or pass --url/--user."),
            result,
        )
    }

    @Test
    fun `list-sources shows sessions and configured sources`() = runBlocking {
        val dbFile = File.createTempFile("social-cli-test", ".db")
        dbFile.deleteOnExit()
        val app = DefaultCliApp(
            databasePath = dbFile,
            clientRegistry = ClientRegistry(
                listOf(
                    RssClient(fetcher = { RSS_XML }),
                    FakeBlueskyClient(
                        itemsByUser = emptyMap(),
                        sessionStateProvider = {
                            SessionState.SignedIn(
                                AccountSession(
                                    accountId = "did:plc:abc",
                                    accessToken = "access",
                                ),
                            )
                        },
                    ),
                    FakeTwitterClient(itemsByUser = emptyMap()),
                ),
            ),
        )

        app.run(listOf("add-feed", "--url", "https://example.com/feed.xml"))
        app.run(listOf("add-user", "--platform", "bluesky", "--user", "frank.bsky.social"))

        val result = app.run(listOf("list-sources"))

        assertEquals(
            CliResult.Success(
                """
                SESSION rss not-required
                SESSION bluesky signed-in did:plc:abc
                SESSION twitter signed-out
                SOURCE bluesky frank.bsky.social
                SOURCE rss https://example.com/feed.xml
                """.trimIndent(),
            ),
            result,
        )
    }

    @Test
    fun `list-sources reports when no sources are configured`() = runBlocking {
        val dbFile = File.createTempFile("social-cli-test", ".db")
        dbFile.deleteOnExit()
        val app = DefaultCliApp(databasePath = dbFile)

        val result = app.run(listOf("list-sources"))

        assertEquals(
            CliResult.Success(
                """
                SESSION rss not-required
                SESSION bluesky signed-out
                SESSION twitter signed-out
                No sources configured.
                """.trimIndent(),
            ),
            result,
        )
    }

    @Test
    fun `signin stores bluesky session`() = runBlocking {
        val dbFile = File.createTempFile("social-cli-test", ".db")
        dbFile.deleteOnExit()
        val app = DefaultCliApp(
            databasePath = dbFile,
            clientRegistry = ClientRegistry(
                listOf(
                    RssClient(fetcher = { RSS_XML }),
                    FakeBlueskyClient(
                        itemsByUser = emptyMap(),
                        sessionsByIdentifier = mapOf(
                            "frank.bsky.social" to AccountSession(
                                accountId = "did:plc:abc",
                                accessToken = "access",
                                refreshToken = "refresh",
                            ),
                        ),
                    ),
                    FakeTwitterClient(itemsByUser = emptyMap()),
                ),
            ),
        )

        val result = app.run(
            listOf(
                "signin",
                "--platform",
                "bluesky",
                "--identifier",
                "frank.bsky.social",
                "--app-password",
                "app-password",
            ),
        )

        val persistedState = SqlDelightSessionRepository(JvmDatabaseFactory.fileBacked(dbFile))
            .getSessionState(PlatformId.Bluesky)
        val listedSources = DefaultCliApp(databasePath = dbFile).run(listOf("list-sources"))

        assertEquals(CliResult.Success("Signed in bluesky as frank.bsky.social"), result)
        assertIs<SessionState.SignedIn>(persistedState)
        assertEquals("did:plc:abc", persistedState.session.accountId)
        assertEquals(true, (listedSources as CliResult.Success).output.contains("SESSION bluesky signed-in did:plc:abc"))
    }

    @Test
    fun `signin reports invalid bluesky credentials`() = runBlocking {
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

        val result = app.run(
            listOf(
                "signin",
                "--platform",
                "bluesky",
                "--identifier",
                "frank.bsky.social",
                "--app-password",
                "wrong-password",
            ),
        )

        assertEquals(
            CliResult.Failure("Signin failed for bluesky: authentication error: invalid credentials"),
            result,
        )
    }

    private fun feedItem(id: String, source: FeedSource, publishedAt: Long): FeedItem = FeedItem(
        itemId = id,
        platformId = source.platformId,
        source = source,
        authorName = source.displayName,
        title = id,
        body = id,
        permalink = null,
        publishedAtEpochMillis = publishedAt,
        seenState = SeenState.Unseen,
    )

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
