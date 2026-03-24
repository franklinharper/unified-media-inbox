package com.franklinharper.social.media.client.app

import com.franklinharper.social.media.client.db.AndroidDatabaseFactory

internal actual fun createAppContainer(): AppContainer =
    buildAppContainer(
        database = AndroidDatabaseFactory.createSocialMediaDatabase(),
        clock = { System.currentTimeMillis() },
    )
