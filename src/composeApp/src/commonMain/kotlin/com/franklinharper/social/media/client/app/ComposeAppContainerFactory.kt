package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.client.bluesky.BlueskyClient
import com.franklinharper.social.media.client.client.rss.RssClient
import com.franklinharper.social.media.client.client.twitter.TwitterClient
import com.franklinharper.social.media.client.db.SocialMediaDatabase
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.repository.DefaultFeedRepository
import com.franklinharper.social.media.client.repository.SqlDelightConfiguredSourceRepository
import com.franklinharper.social.media.client.repository.SqlDelightFeedCacheRepository
import com.franklinharper.social.media.client.repository.SqlDelightSeenItemRepository
import com.franklinharper.social.media.client.repository.SqlDelightSessionRepository
import com.franklinharper.social.media.client.repository.SqlDelightSourceErrorRepository

fun buildAppContainer(
    database: SocialMediaDatabase,
    clock: () -> Long,
): AppContainer {
    val configuredSourceRepository = SqlDelightConfiguredSourceRepository(database)
    val sessionRepository = SqlDelightSessionRepository(database)
    val seenItemRepository = SqlDelightSeenItemRepository(database, clock = clock)
    val feedCacheRepository = SqlDelightFeedCacheRepository(database, clock = clock)
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
                        else -> null
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
        clock = clock,
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
