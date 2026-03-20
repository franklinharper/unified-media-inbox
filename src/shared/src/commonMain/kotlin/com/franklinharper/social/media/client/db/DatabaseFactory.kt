package com.franklinharper.social.media.client.db

import app.cash.sqldelight.db.SqlDriver

fun createSocialMediaDatabase(driver: SqlDriver): SocialMediaDatabase = SocialMediaDatabase(driver)
