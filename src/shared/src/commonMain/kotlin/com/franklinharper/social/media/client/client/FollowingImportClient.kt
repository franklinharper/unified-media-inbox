package com.franklinharper.social.media.client.client

import com.franklinharper.social.media.client.domain.SocialProfile

interface FollowingImportClient {
    suspend fun loadFollowedProfiles(accountId: String): List<SocialProfile>
}
