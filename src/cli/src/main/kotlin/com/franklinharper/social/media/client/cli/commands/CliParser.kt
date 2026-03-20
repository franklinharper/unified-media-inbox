package com.franklinharper.social.media.client.cli.commands

import com.franklinharper.social.media.client.domain.PlatformId

fun parseCommand(args: List<String>): CliCommand? {
    val command = args.firstOrNull() ?: return null
    val rest = args.drop(1)
    return when (command) {
        "list-new-items" -> parseListNewItems(rest)
        "signin" -> parseSignIn(rest)
        "import-follows" -> parseImportFollows(rest)
        "signout" -> parsePositionalPlatformCommand(rest) { platform -> CliCommand.SignOut(platform) }
        "add-user" -> parseUserCommand(rest) { platform, user -> CliCommand.AddUser(platform, user) }
        "remove-user" -> parseUserCommand(rest) { platform, user -> CliCommand.RemoveUser(platform, user) }
        "add-feed" -> parsePositionalUrlCommand(rest) { url -> CliCommand.AddFeed(url) }
        "remove-feed" -> parsePositionalUrlCommand(rest) { url -> CliCommand.RemoveFeed(url) }
        "list-sources" -> CliCommand.ListSources
        "clear-data" -> CliCommand.ClearData
        else -> null
    }
}

private fun parseImportFollows(args: List<String>): CliCommand.ImportFollows? {
    val command = parsePositionalPlatformCommand(args) { platform -> CliCommand.ImportFollows(platform) } as? CliCommand.ImportFollows
        ?: return null
    return if (command.platform == PlatformId.Bluesky) command else null
}

private fun parseSignIn(args: List<String>): CliCommand.SignIn? {
    if (args.size != 3) return null
    val platform = args[0].toPlatformId() ?: return null
    val identifier = args[1]
    val password = args[2]
    if (platform == PlatformId.Rss || platform == PlatformId.Twitter) return null
    return CliCommand.SignIn(platform = platform, identifier = identifier, password = password)
}

private fun parseListNewItems(args: List<String>): CliCommand.ListNewItems? {
    var platform: PlatformId? = null
    val users = mutableListOf<String>()
    val urls = mutableListOf<String>()
    var includeSeen = false
    var markSeen = false
    var verbose = false
    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--platform" -> platform = args.getOrNull(++index)?.toPlatformId() ?: return null
            "--user" -> users += args.getOrNull(++index) ?: return null
            "--url" -> urls += args.getOrNull(++index) ?: return null
            "--include-seen" -> includeSeen = true
            "--mark-seen" -> markSeen = true
            "--verbose" -> verbose = true
            else -> return null
        }
        index++
    }
    if (platform == PlatformId.Rss && users.isNotEmpty()) return null
    if (platform != null && platform != PlatformId.Rss && urls.isNotEmpty()) return null
    return CliCommand.ListNewItems(platform, users, urls, includeSeen, markSeen, verbose)
}

private fun parsePositionalPlatformCommand(
    args: List<String>,
    factory: (PlatformId) -> CliCommand,
): CliCommand? {
    if (args.size != 1) return null
    return factory(args[0].toPlatformId() ?: return null)
}

private fun parseUserCommand(
    args: List<String>,
    factory: (PlatformId, String) -> CliCommand,
): CliCommand? {
    if (args.size != 2) return null
    val platform = args[0].toPlatformId() ?: return null
    if (platform == PlatformId.Rss) return null
    return factory(platform, args[1])
}

private fun parsePositionalUrlCommand(
    args: List<String>,
    factory: (String) -> CliCommand,
): CliCommand? {
    if (args.size != 1) return null
    return factory(args[0])
}

private fun String.toPlatformId(): PlatformId? = when (lowercase()) {
    "rss" -> PlatformId.Rss
    "bluesky" -> PlatformId.Bluesky
    "twitter" -> PlatformId.Twitter
    else -> null
}
