package com.franklinharper.social.media.client.client

import com.franklinharper.social.media.client.domain.PlatformId

class ClientRegistry(
    clients: List<SocialPlatformClient>,
) {
    private val clientsByPlatform = clients.associateBy(SocialPlatformClient::id)

    fun get(platformId: PlatformId): SocialPlatformClient? = clientsByPlatform[platformId]

    fun require(platformId: PlatformId): SocialPlatformClient =
        clientsByPlatform[platformId] ?: error("No client registered for platform: $platformId")

    fun all(): Collection<SocialPlatformClient> = clientsByPlatform.values
}
