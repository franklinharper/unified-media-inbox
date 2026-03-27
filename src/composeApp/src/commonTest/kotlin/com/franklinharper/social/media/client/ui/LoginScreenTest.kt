package com.franklinharper.social.media.client.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.pressKey
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
    fun `login screen submits signup email and password`() = runComposeUiTest {
        var recorded: Pair<String, String>? = null

        setContent {
            LoginScreen(
                state = LoginUiState(),
                onSignUp = { email, password -> recorded = email to password },
            )
        }

        onNodeWithTag("login-email-field").performTextInput("new@example.com")
        onNodeWithTag("login-password-field").performTextInput("secret")
        onNodeWithTag("login-sign-up-button").performClick()

        assertEquals("new@example.com" to "secret", recorded)
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
        onNodeWithTag("login-error").assertExists()
    }

    @Test
    fun `login screen ime next moves focus from email to password`() = runComposeUiTest {
        setContent {
            LoginScreen(
                state = LoginUiState(),
            )
        }

        onNodeWithTag("login-email-field").performClick()
        onNodeWithTag("login-email-field").performImeAction()

        onNodeWithTag("login-password-field").assertIsFocused()
    }

    @Test
    fun `login screen keyboard navigation reaches both auth buttons`() = runComposeUiTest {
        setContent {
            LoginScreen(
                state = LoginUiState(),
            )
        }

        onNodeWithTag("login-email-field").performClick()
        onNodeWithTag("login-email-field").performImeAction()
        onNodeWithTag("login-password-field").performKeyInput {
            pressKey(Key.Tab)
        }
        onNodeWithTag("login-submit-button").assertIsFocused()

        onNodeWithTag("login-submit-button").performKeyInput {
            pressKey(Key.Tab)
        }
        onNodeWithTag("login-sign-up-button").assertIsFocused()
    }

    @Test
    fun `login screen enter on password submits sign in`() = runComposeUiTest {
        var recorded: Pair<String, String>? = null

        setContent {
            LoginScreen(
                state = LoginUiState(),
                onSignIn = { email, password -> recorded = email to password },
            )
        }

        onNodeWithTag("login-email-field").performTextInput("alice@example.com")
        onNodeWithTag("login-email-field").performImeAction()
        onNodeWithTag("login-password-field").performTextInput("secret")
        onNodeWithTag("login-password-field").performImeAction()

        assertEquals("alice@example.com" to "secret", recorded)
    }
}
