package com.franklinharper.social.media.client.cli.commands

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.client.PasswordAuthClient
import com.franklinharper.social.media.client.client.bluesky.BlueskyClient
import com.franklinharper.social.media.client.client.fake.FakeTwitterClient
import com.franklinharper.social.media.client.client.rss.RssClient
import com.franklinharper.social.media.client.db.JvmDatabaseFactory
import com.franklinharper.social.media.client.domain.ConfiguredSource
import com.franklinharper.social.media.client.domain.ClientFailure
import com.franklinharper.social.media.client.domain.FeedRequest
import com.franklinharper.social.media.client.domain.FeedSourceStatus
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.domain.SourceContentOrigin
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
    private val resolvedClientRegistry = clientRegistry ?: ClientRegistry(
        listOf(
            RssClient(),
            BlueskyClient(
                sessionProvider = {
                    when (val state = sessionRepository.getSessionState(PlatformId.Bluesky)) {
                        is SessionState.SignedIn -> state.session
                        else -> null
                    }
                },
            ),
            FakeTwitterClient(itemsByUser = emptyMap()),
        ),
    )
    private val feedRepository = DefaultFeedRepository(
        clientRegistry = resolvedClientRegistry,
        seenItemRepository = seenRepository,
        feedCacheRepository = feedCacheRepository,
        clock = { System.currentTimeMillis() },
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
                if (sources.isEmpty()) {
                    return CliResult.Failure("No sources configured. Use add-feed or add-user, or pass --url/--user.")
                }
                val result = feedRepository.loadFeedItems(
                    FeedRequest(
                        sources = sources,
                        includeSeen = command.includeSeen,
                    ),
                )
                if (command.markSeen) {
                    seenRepository.markSeen(result.items.map { "${it.platformId.name.lowercase()}:${it.itemId}" })
                }
                val itemCountBySource = result.items.groupingBy { it.source.cacheKey }.eachCount()
                val statusOutput = result.sourceStatuses.joinToString("\n") { status ->
                    formatStatusLine(status, itemCountBySource[status.source.cacheKey] ?: 0)
                }
                val itemsOutput = result.items.joinToString("\n") { item ->
                    "${item.publishedAtEpochMillis} ${item.platformId.name.lowercase()} ${item.source.displayName} ${item.title ?: item.body ?: item.itemId}"
                }
                CliResult.Success(listOf(statusOutput, itemsOutput).filter { it.isNotBlank() }.joinToString("\n"))
            }
            is CliCommand.SignIn -> {
                val client = resolvedClientRegistry.require(command.platform) as? PasswordAuthClient
                    ?: return CliResult.Failure("signin is not supported for ${command.platform.name.lowercase()}")
                val session = try {
                    client.signIn(command.identifier, command.password)
                } catch (error: Throwable) {
                    val message = when (error) {
                        is ClientFailure -> formatClientError(error.clientError)
                        else -> error.message ?: "unknown error"
                    }
                    return CliResult.Failure("Signin failed for ${command.platform.name.lowercase()}: $message")
                }
                sessionRepository.upsertSession(command.platform, session)
                CliResult.Success("Signed in ${command.platform.name.lowercase()} as ${command.identifier}")
            }
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

private fun formatStatusLine(status: FeedSourceStatus, itemCount: Int): String {
    val sourceLabel = "${status.source.platformId.name.lowercase()} ${status.source.cliLabel}"
    return when (val state = status.state) {
        SourceLoadState.Loading -> "STATUS $sourceLabel loading"
        SourceLoadState.Success -> "STATUS $sourceLabel refreshed $itemCount item(s)"
        is SourceLoadState.Error -> when (status.contentOrigin) {
            SourceContentOrigin.Cache -> "STATUS $sourceLabel using cache with $itemCount item(s) after ${formatClientError(state.error)}"
            SourceContentOrigin.None -> "STATUS $sourceLabel failed with ${formatClientError(state.error)}"
            SourceContentOrigin.Refresh -> "STATUS $sourceLabel refreshed $itemCount item(s)"
        }
    }
}

private val com.franklinharper.social.media.client.domain.FeedSource.cacheKey: String
    get() = "${platformId.name.lowercase()}:$sourceId"

private val com.franklinharper.social.media.client.domain.FeedSource.cliLabel: String
    get() = when (platformId) {
        PlatformId.Rss -> sourceId
        else -> displayName
    }

private fun formatClientError(error: com.franklinharper.social.media.client.domain.ClientError): String = when (error) {
    is com.franklinharper.social.media.client.domain.ClientError.AuthenticationError -> "authentication error${error.message?.let { ": $it" }.orEmpty()}"
    is com.franklinharper.social.media.client.domain.ClientError.NetworkError -> "network error${error.message?.let { ": $it" }.orEmpty()}"
    is com.franklinharper.social.media.client.domain.ClientError.ParsingError -> "parsing error${error.message?.let { ": $it" }.orEmpty()}"
    is com.franklinharper.social.media.client.domain.ClientError.PermanentFailure -> "permanent failure${error.message?.let { ": $it" }.orEmpty()}"
    is com.franklinharper.social.media.client.domain.ClientError.RateLimitError -> "rate limited${error.retryAfterMillis?.let { " (retry after ${it}ms)" }.orEmpty()}"
    is com.franklinharper.social.media.client.domain.ClientError.TemporaryFailure -> "temporary failure${error.message?.let { ": $it" }.orEmpty()}"
}
