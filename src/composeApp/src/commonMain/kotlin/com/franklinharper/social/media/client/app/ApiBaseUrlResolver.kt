package com.franklinharper.social.media.client.app

internal fun resolveApiBaseUrl(configuredValue: String?): String {
    val normalized = configuredValue?.trim().orEmpty()
    if (normalized.isBlank()) {
        return ""
    }

    return normalized.removeSuffix("/")
}
