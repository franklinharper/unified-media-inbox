package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.client.bluesky.BlueskyClient
import com.franklinharper.social.media.client.client.rss.RssClient
import com.franklinharper.social.media.client.client.twitter.TwitterClient
import com.franklinharper.social.media.client.db.JvmDatabaseFactory
import com.franklinharper.social.media.client.domain.AccountSession
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.repository.DefaultFeedRepository
import com.franklinharper.social.media.client.repository.SqlDelightConfiguredSourceRepository
import com.franklinharper.social.media.client.repository.SqlDelightFeedCacheRepository
import com.franklinharper.social.media.client.repository.SqlDelightSeenItemRepository
import com.franklinharper.social.media.client.repository.SqlDelightSessionRepository
import com.franklinharper.social.media.client.repository.SqlDelightSourceErrorRepository
import java.io.File

internal actual fun createAppContainer(): AppContainer = createJvmAppContainer()

internal fun createJvmAppContainer(
    databaseFile: File = defaultDatabaseFile(),
): AppContainer {
    val database = JvmDatabaseFactory.fileBacked(databaseFile)
    val configuredSourceRepository = SqlDelightConfiguredSourceRepository(database)
    val seenItemRepository = SqlDelightSeenItemRepository(database) { System.currentTimeMillis() }
    val sessionRepository = SqlDelightSessionRepository(database)
    val feedCacheRepository = SqlDelightFeedCacheRepository(database) { System.currentTimeMillis() }
    val sourceErrorRepository = SqlDelightSourceErrorRepository(database)
    val clientRegistry = ClientRegistry(
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
            TwitterClient(
                sessionProvider = {
                    when (val state = sessionRepository.getSessionState(PlatformId.Twitter)) {
                        is SessionState.SignedIn -> state.session
                        else -> twitterSessionFromEnvironment()
                    }
                },
            ),
        ),
    )
    val feedRepository = DefaultFeedRepository(
        clientRegistry = clientRegistry,
        seenItemRepository = seenItemRepository,
        feedCacheRepository = feedCacheRepository,
        sourceErrorRepository = sourceErrorRepository,
        clock = { System.currentTimeMillis() },
    )

    return object : AppContainer {
        override val dependencies: AppDependencies = AppDependencies(
            clientRegistry = clientRegistry,
            configuredSourceRepository = configuredSourceRepository,
            sessionRepository = sessionRepository,
            seenItemRepository = seenItemRepository,
            feedCacheRepository = feedCacheRepository,
            sourceErrorRepository = sourceErrorRepository,
            feedRepository = feedRepository,
        )
    }
}

private fun defaultDatabaseFile(): File = File(System.getProperty("user.dir"), "social-media-client.db")

private fun twitterSessionFromEnvironment(): AccountSession? {
    val token = sequenceOf("X_BEARER_TOKEN", "TWITTER_BEARER_TOKEN")
        .mapNotNull { key -> System.getenv(key)?.takeIf(String::isNotBlank) }
        .firstOrNull()
        ?: return null
    return AccountSession(
        accountId = "twitter-app",
        accessToken = token,
    )
}
