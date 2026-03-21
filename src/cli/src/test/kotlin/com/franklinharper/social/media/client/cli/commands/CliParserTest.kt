package com.franklinharper.social.media.client.cli.commands

import com.franklinharper.social.media.client.domain.PlatformId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CliParserTest {

    @Test
    fun `parses list-new-items with multiple users and flags`() {
        val command = parseCommand(
            listOf(
                "list-new-items",
                "--platform",
                "bluesky",
                "--user",
                "frank",
                "--user",
                "sam",
                "--include-seen",
                "--mark-seen",
                "--verbose",
            ),
        )

        assertEquals(
            CliCommand.ListNewItems(
                platform = PlatformId.Bluesky,
                users = listOf("frank", "sam"),
                urls = emptyList(),
                includeSeen = true,
                markSeen = true,
                verbose = true,
            ),
            command,
        )
    }

    @Test
    fun `rejects rss list-new-items with user flag`() {
        assertNull(
            parseCommand(
                listOf(
                    "list-new-items",
                    "--platform",
                    "rss",
                    "--user",
                    "frank",
                ),
            ),
        )
    }

    @Test
    fun `parses add-user`() {
        assertEquals(
            CliCommand.AddUser(PlatformId.Twitter, "frank"),
            parseCommand(listOf("add-user", "twitter", "frank")),
        )
    }

    @Test
    fun `parses add-feed with positional url`() {
        assertEquals(
            CliCommand.AddFeed("https://hnrss.org/newest"),
            parseCommand(listOf("add-feed", "https://hnrss.org/newest")),
        )
    }

    @Test
    fun `rejects add-feed with legacy url flag`() {
        assertNull(parseCommand(listOf("add-feed", "--url", "https://hnrss.org/newest")))
    }

    @Test
    fun `parses import-opml with positional file path`() {
        assertEquals(
            CliCommand.ImportOpml("/tmp/feedly.opml"),
            parseCommand(listOf("import-opml", "/tmp/feedly.opml")),
        )
    }

    @Test
    fun `parses list-errors`() {
        assertEquals(CliCommand.ListErrors, parseCommand(listOf("list-errors")))
    }

    @Test
    fun `parses bluesky signin credentials`() {
        assertEquals(
            CliCommand.SignIn(
                platform = PlatformId.Bluesky,
                identifier = "frank.bsky.social",
                password = "app-password",
            ),
            parseCommand(
                listOf(
                    "signin",
                    "bluesky",
                    "frank.bsky.social",
                    "app-password",
                ),
            ),
        )
    }

    @Test
    fun `parses bluesky import follows`() {
        assertEquals(
            CliCommand.ImportFollows(PlatformId.Bluesky),
            parseCommand(listOf("import-follows", "bluesky")),
        )
    }

    @Test
    fun `rejects rss signin`() {
        assertNull(
            parseCommand(
                listOf(
                    "signin",
                    "rss",
                    "example",
                    "secret",
                ),
            ),
        )
    }
}
