package com.franklinharper.social.media.client.cli.commands

import com.franklinharper.social.media.client.domain.PlatformId

sealed interface CliCommand {
    data class ListNewItems(
        val platform: PlatformId?,
        val users: List<String>,
        val urls: List<String>,
        val includeSeen: Boolean,
        val markSeen: Boolean,
        val verbose: Boolean,
    ) : CliCommand

    data class SignIn(
        val platform: PlatformId,
        val identifier: String,
        val password: String,
    ) : CliCommand
    data class ImportFollows(val platform: PlatformId) : CliCommand
    data class SignOut(val platform: PlatformId) : CliCommand
    data class AddUser(val platform: PlatformId, val user: String) : CliCommand
    data class RemoveUser(val platform: PlatformId, val user: String) : CliCommand
    data class AddFeed(val url: String) : CliCommand
    data class RemoveFeed(val url: String) : CliCommand
    data object ListSources : CliCommand
    data object ClearData : CliCommand
}
