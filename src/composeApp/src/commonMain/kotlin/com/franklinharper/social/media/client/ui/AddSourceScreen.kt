package com.franklinharper.social.media.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.franklinharper.social.media.client.app.AddSourceUiState
import com.franklinharper.social.media.client.app.SourceType

@Composable
fun AddSourceScreen(
    state: AddSourceUiState,
    onSelectType: (SourceType) -> Unit,
    onAddRssSource: (String) -> Unit,
    onAddBlueskySource: (String) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.selectedType) {
                null -> AddSourceTypePicker(onSelectType = onSelectType)
                SourceType.Rss -> AddRssSourceForm(
                    isAdding = state.isAdding,
                    addError = state.addError,
                    onAddSource = onAddRssSource,
                )
                SourceType.Bluesky -> AddBlueskySourceForm(
                    isAdding = state.isAdding,
                    addError = state.addError,
                    onAddSource = onAddBlueskySource,
                )
                SourceType.Twitter -> Text("Twitter support coming later")
            }
        }
    }
}
