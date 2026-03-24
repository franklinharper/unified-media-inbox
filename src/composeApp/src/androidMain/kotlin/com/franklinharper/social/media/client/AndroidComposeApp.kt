package com.franklinharper.social.media.client

import android.content.Context
import androidx.compose.runtime.Composable
import com.franklinharper.social.media.client.db.AndroidDatabaseFactory

fun initializeComposeApp(context: Context) {
    AndroidDatabaseFactory.initialize(context)
}

@Composable
fun AndroidAppPreviewContent() {
    AppPreviewContent()
}
