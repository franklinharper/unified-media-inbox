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
import java.sql.DriverManager
import kotlin.test.assertContains
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

        app.run(listOf("add-feed", "https://example.com/feed.xml"))
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

        app.run(listOf("add-feed", "https://example.com/feed.xml"))
        val firstRun = app.run(listOf("list-new-items", "--mark-seen"))
        val secondRun = app.run(listOf("list-new-items"))

        assertIs<CliResult.Success>(firstRun)
        assertIs<CliResult.Success>(secondRun)
        assertEquals(true, firstRun.output.contains("Second item"))
        assertEquals("", secondRun.output)
    }

    @Test
    fun `verbose list-new-items shows zero item status lines`() = runBlocking {
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

        app.run(listOf("add-feed", "https://example.com/feed.xml"))
        app.run(listOf("list-new-items", "--mark-seen"))
        val verboseRun = app.run(listOf("list-new-items", "--verbose"))

        assertIs<CliResult.Success>(verboseRun)
        assertEquals(true, verboseRun.output.contains("STATUS rss https://example.com/feed.xml refreshed 0 item(s)"))
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
        initialApp.run(listOf("add-feed", source.sourceId))
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

        app.run(listOf("add-feed", "https://example.com/feed.xml"))
        app.run(listOf("add-user", "bluesky", "frank.bsky.social"))

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
    fun `import-opml adds rss feeds from file`() = runBlocking {
        val dbFile = File.createTempFile("social-cli-test", ".db")
        dbFile.deleteOnExit()
        val opmlFile = File.createTempFile("social-cli-import", ".opml")
        opmlFile.deleteOnExit()
        opmlFile.writeText(
            """
            <opml version="1.0">
              <body>
                <outline text="Feeds">
                  <outline text="HN" type="rss" xmlUrl="https://hnrss.org/newest"/>
                  <outline text="Kotlin" type="rss" xmlUrl="https://example.com/feed.xml"/>
                </outline>
              </body>
            </opml>
            """.trimIndent(),
        )
        val app = DefaultCliApp(databasePath = dbFile)

        val result = app.run(listOf("import-opml", opmlFile.absolutePath))
        val listedSources = app.run(listOf("list-sources"))

        assertEquals(
            CliResult.Success("Imported 2 RSS feed(s) from ${opmlFile.absolutePath}"),
            result,
        )
        assertIs<CliResult.Success>(listedSources)
        assertEquals(true, listedSources.output.contains("SOURCE rss https://hnrss.org/newest"))
        assertEquals(true, listedSources.output.contains("SOURCE rss https://example.com/feed.xml"))
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
    fun `import-opml fails when file has no rss feeds`() = runBlocking {
        val dbFile = File.createTempFile("social-cli-test", ".db")
        dbFile.deleteOnExit()
        val opmlFile = File.createTempFile("social-cli-import-empty", ".opml")
        opmlFile.deleteOnExit()
        opmlFile.writeText(
            """
            <opml version="1.0">
              <body>
                <outline text="Empty"/>
              </body>
            </opml>
            """.trimIndent(),
        )
        val app = DefaultCliApp(databasePath = dbFile)

        val result = app.run(listOf("import-opml", opmlFile.absolutePath))

        assertEquals(
            CliResult.Failure("No RSS feeds found in OPML file ${opmlFile.absolutePath}"),
            result,
        )
    }

    @Test
    fun `list-errors shows persisted feed refresh failures`() = runBlocking {
        val dbFile = File.createTempFile("social-cli-test", ".db")
        dbFile.deleteOnExit()
        val app = DefaultCliApp(
            databasePath = dbFile,
            clientRegistry = ClientRegistry(
                listOf(
                    FakeRssClient(
                        itemsByUrl = emptyMap(),
                        errorsByUrl = mapOf("https://example.com/feed.xml" to ClientError.ParsingError("bad xml")),
                    ),
                    FakeBlueskyClient(itemsByUser = emptyMap()),
                    FakeTwitterClient(itemsByUser = emptyMap()),
                ),
            ),
        )

        app.run(listOf("add-feed", "https://example.com/feed.xml"))
        app.run(listOf("list-new-items"))
        val result = app.run(listOf("list-errors"))

        assertIs<CliResult.Success>(result)
        assertContains(result.output, "ERROR ")
        assertContains(result.output, "rss https://example.com/feed.xml parsing none bad xml")
    }

    @Test
    fun `list-errors reports when no errors are logged`() = runBlocking {
        val dbFile = File.createTempFile("social-cli-test", ".db")
        dbFile.deleteOnExit()
        val app = DefaultCliApp(databasePath = dbFile)

        val result = app.run(listOf("list-errors"))

        assertEquals(CliResult.Success("No errors logged."), result)
    }

    @Test
    fun `clear-data deletes database file when schema is outdated`() = runBlocking {
        val dbFile = File.createTempFile("social-cli-legacy", ".db")
        createLegacyCliSchema(dbFile)
        val app = DefaultCliApp(databasePath = dbFile)

        val result = app.run(listOf("clear-data"))

        assertEquals(CliResult.Success("Cleared persisted data"), result)
        assertEquals(false, dbFile.exists())
    }

    @Test
    fun `clear-data vacuums reclaimed database pages`() = runBlocking {
        val dbFile = File.createTempFile("social-cli-vacuum", ".db")
        dbFile.deleteOnExit()
        val source = FeedSource(PlatformId.Rss, "https://example.com/feed.xml", "Feed")
        val app = DefaultCliApp(
            databasePath = dbFile,
            clientRegistry = ClientRegistry(
                listOf(
                    FakeRssClient(
                        itemsByUrl = mapOf(
                            source.sourceId to List(200) { index ->
                                feedItem("item-$index", source, index.toLong()).copy(body = "x".repeat(8_000))
                            },
                        ),
                    ),
                    FakeBlueskyClient(itemsByUser = emptyMap()),
                    FakeTwitterClient(itemsByUser = emptyMap()),
                ),
            ),
        )

        app.run(listOf("add-feed", source.sourceId))
        app.run(listOf("list-new-items"))
        val sizeBeforeClear = dbFile.length()

        val result = app.run(listOf("clear-data"))

        assertEquals(CliResult.Success("Cleared persisted data"), result)
        assertEquals(0L, tableCount(dbFile, "feed_items"))
        assertEquals(0L, tableCount(dbFile, "feed_sources"))
        assertEquals(true, dbFile.length() < sizeBeforeClear)
    }

    @Test
    fun `list-sources reports signed in platform without configured sources`() = runBlocking {
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

        val result = app.run(listOf("list-sources"))

        assertEquals(
            CliResult.Success(
                """
                SESSION rss not-required
                SESSION bluesky signed-in did:plc:abc
                SESSION twitter signed-out
                No sources configured for bluesky.
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
                "bluesky",
                "frank.bsky.social",
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
        assertEquals(true, listedSources.output.contains("No sources configured for bluesky."))
    }

    @Test
    fun `import-follows adds followed bluesky handles as sources`() = runBlocking {
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
                        followedProfilesByAccount = mapOf(
                            "did:plc:abc" to listOf(
                                com.franklinharper.social.media.client.domain.SocialProfile(
                                    accountId = "did:plc:alice",
                                    displayName = "Alice",
                                    handle = "alice.bsky.social",
                                ),
                                com.franklinharper.social.media.client.domain.SocialProfile(
                                    accountId = "did:plc:bob",
                                    displayName = "Bob",
                                    handle = "bob.bsky.social",
                                ),
                            ),
                        ),
                    ),
                    FakeTwitterClient(itemsByUser = emptyMap()),
                ),
            ),
        )

        app.run(
            listOf(
                "signin",
                "bluesky",
                "frank.bsky.social",
                "app-password",
            ),
        )

        val result = app.run(listOf("import-follows", "bluesky"))
        val listedSources = app.run(listOf("list-sources"))

        assertEquals(CliResult.Success("Imported 2 followed account(s) from bluesky"), result)
        assertIs<CliResult.Success>(listedSources)
        assertEquals(true, listedSources.output.contains("SOURCE bluesky alice.bsky.social"))
        assertEquals(true, listedSources.output.contains("SOURCE bluesky bob.bsky.social"))
    }

    @Test
    fun `import-follows requires signed in session`() = runBlocking {
        val dbFile = File.createTempFile("social-cli-test", ".db")
        dbFile.deleteOnExit()
        val app = DefaultCliApp(databasePath = dbFile)

        val result = app.run(listOf("import-follows", "bluesky"))

        assertEquals(
            CliResult.Failure("Import requires a signed-in bluesky session."),
            result,
        )
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
                "bluesky",
                "frank.bsky.social",
                "wrong-password",
            ),
        )

        assertEquals(
            CliResult.Failure("Signin failed for bluesky: authentication error: invalid credentials"),
            result,
        )
    }

    @Test
    fun `invalid add-feed usage shows positional syntax`() = runBlocking {
        val dbFile = File.createTempFile("social-cli-test", ".db")
        dbFile.deleteOnExit()
        val app = DefaultCliApp(databasePath = dbFile)

        val result = app.run(listOf("add-feed", "--url", "https://hnrss.org/newest"))

        assertIs<CliResult.Failure>(result)
        assertEquals(true, result.message.contains("social-cli add-feed <feed-url>"))
        assertEquals(true, result.message.contains("social-cli signin bluesky <handle> <app-password>"))
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
        fun createLegacyCliSchema(databaseFile: File) {
            DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(
                        """
                        CREATE TABLE configured_sources (
                          platform_id TEXT NOT NULL,
                          source_kind TEXT NOT NULL,
                          value TEXT NOT NULL,
                          PRIMARY KEY (platform_id, source_kind, value)
                        )
                        """.trimIndent(),
                    )
                    statement.executeUpdate(
                        """
                        CREATE TABLE seen_items (
                          item_key TEXT NOT NULL PRIMARY KEY,
                          seen_at_epoch_millis INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    statement.executeUpdate(
                        """
                        CREATE TABLE account_sessions (
                          platform_id TEXT NOT NULL PRIMARY KEY,
                          account_id TEXT NOT NULL,
                          access_token TEXT,
                          refresh_token TEXT,
                          expires_at_epoch_millis INTEGER
                        )
                        """.trimIndent(),
                    )
                    statement.executeUpdate(
                        """
                        CREATE TABLE feed_sources (
                          platform_id TEXT NOT NULL,
                          source_id TEXT NOT NULL,
                          display_name TEXT NOT NULL,
                          PRIMARY KEY (platform_id, source_id)
                        )
                        """.trimIndent(),
                    )
                    statement.executeUpdate(
                        """
                        CREATE TABLE feed_items (
                          item_key TEXT NOT NULL PRIMARY KEY,
                          item_id TEXT NOT NULL,
                          platform_id TEXT NOT NULL,
                          source_platform_id TEXT NOT NULL,
                          source_id TEXT NOT NULL,
                          author_name TEXT,
                          title TEXT,
                          body TEXT,
                          permalink TEXT,
                          published_at_epoch_millis INTEGER NOT NULL,
                          cached_at_epoch_millis INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    statement.executeUpdate(
                        """
                        CREATE TABLE sync_state (
                          source_platform_id TEXT NOT NULL,
                          source_id TEXT NOT NULL,
                          next_cursor_value TEXT,
                          last_refreshed_at_epoch_millis INTEGER,
                          PRIMARY KEY (source_platform_id, source_id)
                        )
                        """.trimIndent(),
                    )
                }
            }
        }

        fun tableCount(databaseFile: File, tableName: String): Long =
            DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT count(*) FROM $tableName").use { resultSet ->
                        resultSet.next()
                        resultSet.getLong(1)
                    }
                }
            }

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
