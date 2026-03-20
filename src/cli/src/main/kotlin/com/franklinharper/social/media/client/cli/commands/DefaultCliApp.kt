package com.franklinharper.social.media.client.cli.commands

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.client.fake.FakeBlueskyClient
import com.franklinharper.social.media.client.client.fake.FakeTwitterClient
import com.franklinharper.social.media.client.client.rss.RssClient
import com.franklinharper.social.media.client.db.JvmDatabaseFactory
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.FeedRequest
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SourceLoadState
import com.franklinharper.social.media.client.repository.DefaultFeedRepository
import com.franklinharper.social.media.client.repository.SqlDelightConfiguredSourceRepository
import com.franklinharper.social.media.client.repository.SqlDelightFeedCacheRepository
import com.franklinharper.social.media.client.repository.SqlDelightSeenItemRepository
import com.franklinharper.social.media.client.repository.SqlDelightSessionRepository
import java.io.File

class DefaultCliApp(
    databasePath: File = File(System.getProperty("user.dir"), "social-media-client.db"),
    clientRegistry: ClientRegistry? = null,
) : CliApp {
    private val database = JvmDatabaseFactory.fileBacked(databasePath)
    private val sourceRepository = SqlDelightConfiguredSourceRepository(database)
    private val seenRepository = SqlDelightSeenItemRepository(database) { System.currentTimeMillis() }
    private val sessionRepository = SqlDelightSessionRepository(database)
    private val feedCacheRepository = SqlDelightFeedCacheRepository(database) { System.currentTimeMillis() }
    private val feedRepository = DefaultFeedRepository(
        clientRegistry = clientRegistry ?: ClientRegistry(
            listOf(
                RssClient(),
                FakeBlueskyClient(itemsByUser = emptyMap()),
                FakeTwitterClient(itemsByUser = emptyMap()),
            ),
        ),
        seenItemRepository = seenRepository,
    )

    override suspend fun run(args: List<String>): CliResult {
        val command = parseCommand(args) ?: return CliResult.Failure(usageText)
        return when (command) {
            is CliCommand.AddFeed -> {
                sourceRepository.addSource(ConfiguredSource.RssFeed(url = command.url))
                CliResult.Success("Added RSS feed ${command.url}")
            }
            is CliCommand.RemoveFeed -> {
                sourceRepository.removeSource(ConfiguredSource.RssFeed(url = command.url))
                CliResult.Success("Removed RSS feed ${command.url}")
            }
            is CliCommand.AddUser -> {
                sourceRepository.addSource(
                    ConfiguredSource.SocialUser(
                        platformId = command.platform,
                        user = command.user,
                    ),
                )
                CliResult.Success("Added user ${command.user} on ${command.platform.name.lowercase()}")
            }
            is CliCommand.RemoveUser -> {
                sourceRepository.removeSource(
                    ConfiguredSource.SocialUser(
                        platformId = command.platform,
                        user = command.user,
                    ),
                )
                CliResult.Success("Removed user ${command.user} on ${command.platform.name.lowercase()}")
            }
            CliCommand.ListSources -> {
                val sources = sourceRepository.listSources()
                CliResult.Success(
                    sources.joinToString("\n") { source ->
                        when (source) {
                            is ConfiguredSource.RssFeed -> "rss ${source.url}"
                            is ConfiguredSource.SocialUser -> "${source.platformId.name.lowercase()} ${source.user}"
                        }
                    },
                )
            }
            is CliCommand.ListNewItems -> {
                val explicitSources = buildList {
                    addAll(command.urls.map { ConfiguredSource.RssFeed(url = it) })
                    addAll(command.users.map { user ->
                        ConfiguredSource.SocialUser(platformId = command.platform ?: PlatformId.Bluesky, user = user)
                    })
                }
                val sources = if (explicitSources.isEmpty()) sourceRepository.listSources() else explicitSources
                val result = feedRepository.loadFeedItems(
                    FeedRequest(
                        sources = sources,
                        includeSeen = command.includeSeen,
                    ),
                )
                if (command.markSeen) {
                    seenRepository.markSeen(result.items.map { "${it.platformId.name.lowercase()}:${it.itemId}" })
                }
                val itemsOutput = result.items.joinToString("\n") { item ->
                    "${item.publishedAtEpochMillis} ${item.platformId.name.lowercase()} ${item.source.displayName} ${item.title ?: item.body ?: item.itemId}"
                }
                val errorOutput = result.sourceStatuses
                    .filter { it.state is SourceLoadState.Error }
                    .joinToString("\n") { status ->
                        val error = (status.state as SourceLoadState.Error).error
                        "ERROR ${status.source.displayName}: $error"
                    }
                CliResult.Success(listOf(itemsOutput, errorOutput).filter { it.isNotBlank() }.joinToString("\n"))
            }
            is CliCommand.SignIn -> CliResult.Failure("signin is not implemented yet for ${command.platform.name.lowercase()}")
            is CliCommand.SignOut -> {
                sessionRepository.signOut(command.platform)
                CliResult.Success("Signed out ${command.platform.name.lowercase()}")
            }
            CliCommand.ClearData -> {
                sessionRepository.clearAll()
                sourceRepository.clearAll()
                seenRepository.clearAll()
                feedCacheRepository.clearAll()
                CliResult.Success("Cleared persisted data")
            }
        }
    }
}

private const val usageText =
    "Usage: social-cli list-new-items|signin|signout|add-user|remove-user|add-feed|remove-feed|list-sources|clear-data"
