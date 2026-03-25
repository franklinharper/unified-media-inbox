package com.franklinharper.social.media.client.app

import kotlinx.browser.document
import org.w3c.dom.HTMLMetaElement

private const val API_BASE_URL_META_NAME = "social-media-client-api-base-url"

internal fun createWebAppContainer(): AppContainer =
    createRemoteAppContainer(baseUrl = resolveApiBaseUrl(readConfiguredApiBaseUrl()))

private fun readConfiguredApiBaseUrl(): String? =
    (document.querySelector("meta[name='$API_BASE_URL_META_NAME']") as? HTMLMetaElement)?.content
