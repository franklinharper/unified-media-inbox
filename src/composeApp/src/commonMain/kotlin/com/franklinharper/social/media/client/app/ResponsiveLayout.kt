package com.franklinharper.social.media.client.app

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ResponsiveLayout {
    Narrow,
    Wide,
    ;

    companion object {
        val wideMinWidth: Dp = 840.dp

        fun forMaxWidth(maxWidth: Dp): ResponsiveLayout =
            if (maxWidth >= wideMinWidth) Wide else Narrow
    }
}

val ResponsiveLayout.isWide: Boolean
    get() = this == ResponsiveLayout.Wide
