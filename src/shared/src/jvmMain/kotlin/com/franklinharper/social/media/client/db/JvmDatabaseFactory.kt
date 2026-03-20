package com.franklinharper.social.media.client.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

object JvmDatabaseFactory {
    fun inMemory(): SocialMediaDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(SocialMediaDatabase.Schema::create)
        return createSocialMediaDatabase(driver)
    }

    fun fileBacked(databaseFile: File): SocialMediaDatabase {
        val isNewDatabase = !databaseFile.exists()
        databaseFile.parentFile?.mkdirs()
        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:${databaseFile.absolutePath}",
            properties = Properties().apply {
                put("foreign_keys", "true")
            },
        )
        if (isNewDatabase) {
            SocialMediaDatabase.Schema.create(driver)
        }
        return createSocialMediaDatabase(driver)
    }
}
