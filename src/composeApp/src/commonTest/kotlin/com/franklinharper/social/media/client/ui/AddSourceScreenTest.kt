package com.franklinharper.social.media.client.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.franklinharper.social.media.client.AppRoot
import com.franklinharper.social.media.client.app.AddSourceUiState
import com.franklinharper.social.media.client.app.FeedShellUiState
import com.franklinharper.social.media.client.app.SourceType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AddSourceScreenTest {

    @Test
    fun `empty state button opens add-source picker`() = runComposeUiTest {
        setContent {
            AppRoot(
                feedState = FeedShellUiState(),
                addSourceState = AddSourceUiState(),
            )
        }

        onNodeWithText("Add sources").performClick()
        onNodeWithText("Add a source").assertExists()
    }

    @Test
    fun `floating action button opens same add-source picker`() = runComposeUiTest {
        setContent {
            AppRoot(
                feedState = FeedShellUiState(),
                addSourceState = AddSourceUiState(),
            )
        }

        onNodeWithTag("feed-add-source-fab").performClick()
        onNodeWithText("Add a source").assertExists()
    }

    @Test
    fun `successful add returns to feed`() = runComposeUiTest {
        setContent {
            var addSourceState by remember { mutableStateOf(AddSourceUiState()) }
            AppRoot(
                feedState = FeedShellUiState(),
                addSourceState = addSourceState,
                onSelectAddSourceType = { type ->
                    addSourceState = addSourceState.copy(selectedType = type, didAddSource = false)
                },
                onAddRssSource = {
                    addSourceState = AddSourceUiState(didAddSource = true)
                },
            )
        }

        onNodeWithText("Add sources").performClick()
        onNodeWithText("RSS feed").performClick()
        onNodeWithText("Feed URL").performTextInput("https://hnrss.org/newest")
        onNodeWithText("Add source").performClick()

        onNodeWithText("Feed").assertExists()
        onNodeWithText("Add a source").assertDoesNotExist()
    }
}
