package com.franklinharper.social.media.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.franklinharper.social.media.client.domain.FeedItem
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun FeedItemList(
    items: List<FeedItem>,
    isWideLayout: Boolean,
    nowEpochMillis: Long,
    onOpenItem: (FeedItem) -> Unit = {},
    onOpenComments: (String) -> Unit = {},
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = FeedItem::itemId) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenItem(item) },
                colors = CardDefaults.cardColors(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(item.title ?: item.source.displayName)
                    item.authorName?.let { Text(it) }
                    Text(formatRelativeTimestamp(nowEpochMillis, item.publishedAtEpochMillis))
                    item.commentsPermalink?.let { commentsUrl ->
                        Button(
                            onClick = { onOpenComments(commentsUrl) },
                            modifier = Modifier.testTag("feed-item-comments-button-${item.itemId}"),
                        ) {
                            Text("Comments")
                        }
                    }
                }
            }
        }
    }
}

internal fun sortFeedItemsForDisplay(items: List<FeedItem>): List<FeedItem> =
    items.sortedWith(
        compareBy<FeedItem> {
            when (it.seenState) {
                com.franklinharper.social.media.client.domain.SeenState.Unseen -> 0
                com.franklinharper.social.media.client.domain.SeenState.Seen -> 1
            }
        }.thenBy { it.publishedAtEpochMillis }
    )

internal fun formatRelativeTimestamp(
    nowEpochMillis: Long,
    publishedAtEpochMillis: Long,
): String {
    val elapsed = (nowEpochMillis - publishedAtEpochMillis).coerceAtLeast(0L).milliseconds
    val totalSeconds = elapsed.inWholeSeconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "${hours.twoDigits()}:${minutes.twoDigits()}:${seconds.twoDigits()} ago"
}

private fun Long.twoDigits(): String = toString().padStart(2, '0')
