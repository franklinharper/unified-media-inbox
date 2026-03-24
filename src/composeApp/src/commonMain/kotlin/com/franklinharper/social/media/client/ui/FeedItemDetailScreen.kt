package com.franklinharper.social.media.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.franklinharper.social.media.client.domain.FeedItem
import kotlin.time.Clock

@Composable
fun FeedItemDetailScreen(
    item: FeedItem,
    onClose: () -> Unit,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("feed-item-detail-close"),
            ) {
                Text("X")
            }
            Text(item.title ?: item.source.displayName)
            item.authorName?.let { Text(it) }
            Text(formatRelativeTimestamp(nowEpochMillis, item.publishedAtEpochMillis))
            item.body?.takeIf(String::isNotBlank)?.let { Text(it) }
        }
    }
}
