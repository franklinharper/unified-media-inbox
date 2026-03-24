package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.db.IosDatabaseFactory
import platform.Foundation.NSDate

internal actual fun createAppContainer(): AppContainer =
    buildAppContainer(
        database = IosDatabaseFactory.createSocialMediaDatabase(),
        clock = { currentTimeMillis() },
    )

private fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
