package com.franklinharper.social.media.client.client

import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkHttpJvmTest {

    @Test
    fun `jvm network http uses cio engine`() {
        assertEquals("CIO", networkHttpEngineName())
    }
}
