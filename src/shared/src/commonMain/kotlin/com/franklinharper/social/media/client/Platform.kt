package com.franklinharper.social.media.client

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform