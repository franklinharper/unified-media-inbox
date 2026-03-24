package com.franklinharper.social.media.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.franklinharper.social.media.client.domain.FeedSource

@Composable
fun SourcePanel(
    sources: List<FeedSource>,
    selectedSource: FeedSource?,
    onSelectSource: (FeedSource?) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .testTag("source-panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { onSelectSource(null) },
        ) {
            Text(selectedSource?.displayName ?: "All items")
        }
        sources.forEach { source ->
            Button(
                onClick = { onSelectSource(source) },
            ) {
                Text(source.displayName)
            }
        }
    }
}
