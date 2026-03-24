package com.franklinharper.social.media.client.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.franklinharper.social.media.client.domain.FeedSource

@Composable
fun SourceFilterDropdown(
    sources: List<FeedSource>,
    selectedSource: FeedSource?,
    onSelectSource: (FeedSource?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("source-filter-dropdown"),
    ) {
        Button(
            modifier = Modifier.testTag("source-filter-trigger"),
            onClick = { expanded = true },
        ) {
            Text(selectedSource?.displayName ?: "All items")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("All items") },
                onClick = {
                    expanded = false
                    onSelectSource(null)
                },
            )
            sources.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source.displayName) },
                    onClick = {
                        expanded = false
                        onSelectSource(source)
                    },
                )
            }
        }
    }
}
