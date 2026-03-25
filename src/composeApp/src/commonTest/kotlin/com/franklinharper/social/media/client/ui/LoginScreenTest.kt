package com.franklinharper.social.media.client.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.franklinharper.social.media.client.app.LoginUiState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LoginScreenTest {

    @Test
    fun `login screen submits email and password`() = runComposeUiTest {
        var recorded: Pair<String, String>? = null

        setContent {
            LoginScreen(
                state = LoginUiState(),
                onSignIn = { email, password -> recorded = email to password },
            )
        }

        onNodeWithTag("login-email-field").performTextInput("alice@example.com")
        onNodeWithTag("login-password-field").performTextInput("secret")
        onNodeWithTag("login-submit-button").performClick()

        assertEquals("alice@example.com" to "secret", recorded)
    }

    @Test
    fun `login screen shows session expired message`() = runComposeUiTest {
        setContent {
            LoginScreen(
                state = LoginUiState(
                    message = "Your session expired. Sign in again.",
                ),
            )
        }

        onNodeWithText("Your session expired. Sign in again.").assertExists()
    }
}
