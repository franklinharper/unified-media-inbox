package com.franklinharper.social.media.client.persistence

import com.franklinharper.social.media.client.db.SocialMediaDatabase
import com.franklinharper.social.media.client.db.createSocialMediaDatabase
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.sql.DriverManager
import java.util.Properties

object ServerDatabaseFactory {
    fun inMemory(): SocialMediaDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(SocialMediaDatabase.Schema::create)
        return createSocialMediaDatabase(driver)
    }

    fun fileBacked(databaseFile: File): SocialMediaDatabase {
        val isNewDatabase = !databaseFile.exists() || databaseFile.length() == 0L
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

    fun vacuum(databaseFile: File) {
        if (!databaseFile.exists()) return
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("VACUUM")
            }
        }
    }
}
