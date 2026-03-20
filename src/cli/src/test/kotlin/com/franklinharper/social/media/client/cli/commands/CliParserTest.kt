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
            ),
        )

        assertEquals(
            CliCommand.ListNewItems(
                platform = PlatformId.Bluesky,
                users = listOf("frank", "sam"),
                urls = emptyList(),
                includeSeen = true,
                markSeen = true,
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
            parseCommand(listOf("add-user", "--platform", "twitter", "--user", "frank")),
        )
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
                    "--platform",
                    "bluesky",
                    "--identifier",
                    "frank.bsky.social",
                    "--app-password",
                    "app-password",
                ),
            ),
        )
    }

    @Test
    fun `rejects rss signin`() {
        assertNull(
            parseCommand(
                listOf(
                    "signin",
                    "--platform",
                    "rss",
                    "--identifier",
                    "example",
                    "--app-password",
                    "secret",
                ),
            ),
        )
    }
}
