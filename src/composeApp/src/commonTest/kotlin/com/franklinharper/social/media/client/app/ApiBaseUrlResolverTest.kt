package com.franklinharper.social.media.client.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiBaseUrlResolverTest {

    @Test
    fun `missing config falls back to same origin`() {
        assertEquals("", resolveApiBaseUrl(configuredValue = null))
    }

    @Test
    fun `blank config falls back to same origin`() {
        assertEquals("", resolveApiBaseUrl(configuredValue = "   "))
    }

    @Test
    fun `configured absolute url is trimmed and preserved`() {
        assertEquals("https://api.example.com", resolveApiBaseUrl(configuredValue = " https://api.example.com/ "))
    }
}
