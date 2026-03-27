package com.franklinharper.social.media.client.ui

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SelectableErrorText(
    message: String,
    modifier: Modifier = Modifier,
) {
    SelectionContainer {
        Text(
            text = message,
            modifier = modifier,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
