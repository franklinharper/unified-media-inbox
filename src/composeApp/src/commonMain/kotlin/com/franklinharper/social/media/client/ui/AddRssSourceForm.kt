package com.franklinharper.social.media.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun AddRssSourceForm(
    isAdding: Boolean,
    addError: String?,
    onAddSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Add RSS feed")
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add-source-rss-url-field"),
            label = { Text("Feed URL") },
            enabled = !isAdding,
        )
        addError?.let { message ->
            SelectableErrorText(
                message = message,
                modifier = Modifier.testTag("add-source-error"),
            )
        }
        Button(
            onClick = { onAddSource(url) },
            enabled = !isAdding && url.isNotBlank(),
            modifier = Modifier.testTag("add-source-rss-submit-button"),
        ) {
            Text(if (isAdding) "Adding..." else "Add source")
        }
    }
}
