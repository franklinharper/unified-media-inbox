package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.client.ClientRegistry
import com.franklinharper.social.media.client.repository.ConfiguredSourceRepository
import com.franklinharper.social.media.client.repository.FeedCacheRepository
import com.franklinharper.social.media.client.repository.FeedRepository
import com.franklinharper.social.media.client.repository.SeenItemRepository
import com.franklinharper.social.media.client.repository.SessionRepository
import com.franklinharper.social.media.client.repository.SourceErrorRepository

interface AppContainer {
    val clientRegistry: ClientRegistry
    val configuredSourceRepository: ConfiguredSourceRepository
    val sessionRepository: SessionRepository
    val seenItemRepository: SeenItemRepository
    val feedCacheRepository: FeedCacheRepository
    val sourceErrorRepository: SourceErrorRepository
    val feedRepository: FeedRepository
}
