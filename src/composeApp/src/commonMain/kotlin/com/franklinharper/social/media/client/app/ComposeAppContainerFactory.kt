package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.client.bluesky.BlueskyClient
import com.franklinharper.social.media.client.client.rss.RssClient
import com.franklinharper.social.media.client.client.twitter.TwitterClient
import com.franklinharper.social.media.client.db.SocialMediaDatabase
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.repository.DefaultFeedRepository
import com.franklinharper.social.media.client.repository.LOCAL_OWNER_USER_ID
import com.franklinharper.social.media.client.repository.SqlDelightConfiguredSourceRepository
import com.franklinharper.social.media.client.repository.SqlDelightFeedCacheRepository
import com.franklinharper.social.media.client.repository.SqlDelightSeenItemRepository
import com.franklinharper.social.media.client.repository.SqlDelightSessionRepository
import com.franklinharper.social.media.client.repository.SqlDelightSourceErrorRepository
import com.franklinharper.social.media.client.remote.DefaultWebApiHttp
import com.franklinharper.social.media.client.remote.WebRemoteConfiguredSourceRepository
import com.franklinharper.social.media.client.remote.WebRemoteFeedCacheRepository
import com.franklinharper.social.media.client.remote.WebRemoteFeedRepository
import com.franklinharper.social.media.client.remote.WebRemoteSeenItemRepository
import com.franklinharper.social.media.client.remote.WebRemoteSessionRepository
import com.franklinharper.social.media.client.remote.WebRemoteSourceErrorRepository

fun buildAppContainer(
    database: SocialMediaDatabase,
    clock: () -> Long,
): AppContainer {
    val configuredSourceRepository = SqlDelightConfiguredSourceRepository(database, LOCAL_OWNER_USER_ID)
    val sessionRepository = SqlDelightSessionRepository(database)
    val seenItemRepository = SqlDelightSeenItemRepository(database, LOCAL_OWNER_USER_ID, clock = clock)
    val feedCacheRepository = SqlDelightFeedCacheRepository(database, LOCAL_OWNER_USER_ID, clock = clock)
    val sourceErrorRepository = SqlDelightSourceErrorRepository(database, LOCAL_OWNER_USER_ID)
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

fun createRemoteAppContainer(
    baseUrl: String = "",
): AppContainer {
    val http = DefaultWebApiHttp(baseUrl = baseUrl)
    val configuredSourceRepository = WebRemoteConfiguredSourceRepository(http)
    val sessionRepository = WebRemoteSessionRepository(http)
    val seenItemRepository = WebRemoteSeenItemRepository(http)
    val feedCacheRepository = WebRemoteFeedCacheRepository(http)
    val sourceErrorRepository = WebRemoteSourceErrorRepository(http)
    val feedRepository = WebRemoteFeedRepository(http)

    return object : AppContainer {
        override val dependencies: AppDependencies = AppDependencies(
            clientRegistry = ClientRegistry(emptyList()),
            configuredSourceRepository = configuredSourceRepository,
            sessionRepository = sessionRepository,
            seenItemRepository = seenItemRepository,
            feedCacheRepository = feedCacheRepository,
            sourceErrorRepository = sourceErrorRepository,
            feedRepository = feedRepository,
        )
    }
}
