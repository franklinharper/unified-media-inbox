package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.repository.ConfiguredSourceRepository
import com.franklinharper.social.media.client.repository.FeedCacheRepository
import com.franklinharper.social.media.client.repository.FeedRepository
import com.franklinharper.social.media.client.repository.SeenItemRepository
import com.franklinharper.social.media.client.repository.SessionRepository
import com.franklinharper.social.media.client.repository.SourceErrorRepository

interface AppContainer {
    val dependencies: AppDependencies

    val clientRegistry: ClientRegistry
        get() = dependencies.clientRegistry

    val configuredSourceRepository: ConfiguredSourceRepository
        get() = dependencies.configuredSourceRepository

    val sessionRepository: SessionRepository
        get() = dependencies.sessionRepository

    val seenItemRepository: SeenItemRepository
        get() = dependencies.seenItemRepository

    val feedCacheRepository: FeedCacheRepository
        get() = dependencies.feedCacheRepository

    val sourceErrorRepository: SourceErrorRepository
        get() = dependencies.sourceErrorRepository

    val feedRepository: FeedRepository
        get() = dependencies.feedRepository
}
