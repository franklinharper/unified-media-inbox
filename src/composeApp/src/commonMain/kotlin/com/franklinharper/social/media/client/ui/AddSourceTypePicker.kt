package com.franklinharper.social.media.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franklinharper.social.media.client.app.SourceType

@Composable
fun AddSourceTypePicker(
    onSelectType: (SourceType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Add a source")
        Button(onClick = { onSelectType(SourceType.Rss) }) {
            Text("RSS feed")
        }
        Button(onClick = { onSelectType(SourceType.Bluesky) }) {
            Text("Bluesky account")
        }
    }
}
