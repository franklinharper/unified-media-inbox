package com.franklinharper.social.media.client.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSSearchPathForDirectoriesInDomains

object IosDatabaseFactory {
    private const val DATABASE_NAME = "social-media-client.db"

    fun createSocialMediaDatabase(): SocialMediaDatabase {
        val databasePath = documentsDirectoryPath()?.let { "$it/$DATABASE_NAME" }
            ?: error("Unable to resolve the iOS documents directory")
        val driver = NativeSqliteDriver(SocialMediaDatabase.Schema, databasePath)
        return createSocialMediaDatabase(driver)
    }

    private fun documentsDirectoryPath(): String? =
        NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull()
}
