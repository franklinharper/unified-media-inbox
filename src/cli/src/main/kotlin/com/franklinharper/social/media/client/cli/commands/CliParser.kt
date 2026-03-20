package com.franklinharper.social.media.client.cli.commands

import com.franklinharper.social.media.client.domain.PlatformId

fun parseCommand(args: List<String>): CliCommand? {
    val command = args.firstOrNull() ?: return null
    val rest = args.drop(1)
    return when (command) {
        "list-new-items" -> parseListNewItems(rest)
        "signin" -> parsePlatformOnly(rest) { platform -> CliCommand.SignIn(platform) }
        "signout" -> parsePlatformOnly(rest) { platform -> CliCommand.SignOut(platform) }
        "add-user" -> parseUserCommand(rest) { platform, user -> CliCommand.AddUser(platform, user) }
        "remove-user" -> parseUserCommand(rest) { platform, user -> CliCommand.RemoveUser(platform, user) }
        "add-feed" -> parseUrlCommand(rest) { url -> CliCommand.AddFeed(url) }
        "remove-feed" -> parseUrlCommand(rest) { url -> CliCommand.RemoveFeed(url) }
        "list-sources" -> CliCommand.ListSources
        "clear-data" -> CliCommand.ClearData
        else -> null
    }
}

private fun parseListNewItems(args: List<String>): CliCommand.ListNewItems? {
    var platform: PlatformId? = null
    val users = mutableListOf<String>()
    val urls = mutableListOf<String>()
    var includeSeen = false
    var markSeen = false
    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--platform" -> platform = args.getOrNull(++index)?.toPlatformId() ?: return null
            "--user" -> users += args.getOrNull(++index) ?: return null
            "--url" -> urls += args.getOrNull(++index) ?: return null
            "--include-seen" -> includeSeen = true
            "--mark-seen" -> markSeen = true
            else -> return null
        }
        index++
    }
    if (platform == PlatformId.Rss && users.isNotEmpty()) return null
    if (platform != null && platform != PlatformId.Rss && urls.isNotEmpty()) return null
    return CliCommand.ListNewItems(platform, users, urls, includeSeen, markSeen)
}

private fun parsePlatformOnly(
    args: List<String>,
    factory: (PlatformId) -> CliCommand,
): CliCommand? {
    if (args.size != 2 || args[0] != "--platform") return null
    return factory(args[1].toPlatformId() ?: return null)
}

private fun parseUserCommand(
    args: List<String>,
    factory: (PlatformId, String) -> CliCommand,
): CliCommand? {
    if (args.size != 4) return null
    if (args[0] != "--platform" || args[2] != "--user") return null
    val platform = args[1].toPlatformId() ?: return null
    if (platform == PlatformId.Rss) return null
    return factory(platform, args[3])
}

private fun parseUrlCommand(
    args: List<String>,
    factory: (String) -> CliCommand,
): CliCommand? {
    if (args.size != 2 || args[0] != "--url") return null
    return factory(args[1])
}

private fun String.toPlatformId(): PlatformId? = when (lowercase()) {
    "rss" -> PlatformId.Rss
    "bluesky" -> PlatformId.Bluesky
    "twitter" -> PlatformId.Twitter
    else -> null
}
