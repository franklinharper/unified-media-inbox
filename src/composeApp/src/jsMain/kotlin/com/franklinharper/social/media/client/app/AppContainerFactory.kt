package com.franklinharper.social.media.client.app

import app.cash.sqldelight.driver.worker.expected.Worker
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import com.franklinharper.social.media.client.db.createSocialMediaDatabase

private val sqlJsWorkerUrl = js("""new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url)""")

internal actual fun createAppContainer(): AppContainer =
    buildAppContainer(
        database = createSocialMediaDatabase(WebWorkerDriver(Worker(sqlJsWorkerUrl))),
        clock = { kotlin.js.Date.now().toLong() },
    )
