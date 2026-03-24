package com.franklinharper.social.media.client.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

object AndroidDatabaseFactory {
    private const val DATABASE_NAME = "social-media-client.db"

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun createSocialMediaDatabase(): SocialMediaDatabase {
        val context = appContext ?: error("AndroidDatabaseFactory.initialize(context) must be called before creating the app container")
        val driver = AndroidSqliteDriver(SocialMediaDatabase.Schema, context, DATABASE_NAME)
        return createSocialMediaDatabase(driver)
    }
}
