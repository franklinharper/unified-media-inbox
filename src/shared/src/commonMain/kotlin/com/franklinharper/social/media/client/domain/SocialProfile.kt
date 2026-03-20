package com.franklinharper.social.media.client.domain

data class SocialProfile(
    val accountId: String,
    val displayName: String,
    val handle: String? = null,
)
