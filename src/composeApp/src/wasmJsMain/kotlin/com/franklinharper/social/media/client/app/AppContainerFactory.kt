package com.franklinharper.social.media.client.app

import app.cash.sqldelight.driver.worker.createDefaultWebWorkerDriver
import com.franklinharper.social.media.client.db.createSocialMediaDatabase
import kotlin.time.Clock

internal actual fun createAppContainer(): AppContainer =
    buildAppContainer(
        database = createSocialMediaDatabase(createDefaultWebWorkerDriver()),
        clock = { Clock.System.now().toEpochMilliseconds() },
    )
