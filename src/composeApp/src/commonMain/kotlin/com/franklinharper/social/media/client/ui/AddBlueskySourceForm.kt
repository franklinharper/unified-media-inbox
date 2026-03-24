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
import androidx.compose.ui.unit.dp

@Composable
fun AddBlueskySourceForm(
    isAdding: Boolean,
    addError: String?,
    onAddSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var handle by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Add Bluesky account")
        OutlinedTextField(
            value = handle,
            onValueChange = { handle = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Handle") },
            enabled = !isAdding,
        )
        if (addError != null) {
            Text(addError)
        }
        Button(
            onClick = { onAddSource(handle) },
            enabled = !isAdding && handle.isNotBlank(),
        ) {
            Text(if (isAdding) "Adding..." else "Add source")
        }
    }
}
