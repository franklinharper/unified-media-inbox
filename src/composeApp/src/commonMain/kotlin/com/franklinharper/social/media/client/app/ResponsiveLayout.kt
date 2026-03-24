package com.franklinharper.social.media.client.app

enum class ResponsiveLayout {
    Narrow,
    Wide,
}

val ResponsiveLayout.isWide: Boolean
    get() = this == ResponsiveLayout.Wide
