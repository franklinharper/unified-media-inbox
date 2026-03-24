package com.franklinharper.social.media.client.app

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ResponsiveLayoutTest {

    @Test
    fun `widths below breakpoint use narrow layout`() {
        assertEquals(ResponsiveLayout.Narrow, ResponsiveLayout.forMaxWidth(839.dp))
    }

    @Test
    fun `widths at breakpoint use wide layout`() {
        assertEquals(ResponsiveLayout.Wide, ResponsiveLayout.forMaxWidth(840.dp))
    }
}
