package com.franklinharper.social.media.client.client

import com.franklinharper.social.media.client.domain.FeedCursor
import com.franklinharper.social.media.client.domain.FeedPage
import com.franklinharper.social.media.client.domain.FeedQuery
import com.franklinharper.social.media.client.domain.PlatformId
import com.franklinharper.social.media.client.domain.SessionState
import com.franklinharper.social.media.client.domain.SocialProfile

interface SocialPlatformClient {
    val id: PlatformId
    val displayName: String

    suspend fun sessionState(): SessionState
    suspend fun loadProfile(accountId: String): SocialProfile
    suspend fun loadFeed(query: FeedQuery, cursor: FeedCursor? = null): FeedPage
}
