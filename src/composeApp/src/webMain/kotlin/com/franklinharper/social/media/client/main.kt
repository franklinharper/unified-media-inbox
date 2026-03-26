package com.franklinharper.social.media.client

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.window.ComposeViewport
import com.franklinharper.social.media.client.app.createWebAutomationStateOrNull
import com.franklinharper.social.media.client.app.installWebAutomationBridge

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val automationState = createWebAutomationStateOrNull()
    ComposeViewport {
        SideEffect {
            installWebAutomationBridge(automationState)
        }
        App(automationState = automationState)
    }
}
