package com.franklinharper.social.media.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusable
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.franklinharper.social.media.client.app.AuthAction
import com.franklinharper.social.media.client.app.LoginUiState

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(
    state: LoginUiState,
    onSignIn: (String, String) -> Unit = { _, _ -> },
    onSignUp: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val emailFocusRequester = FocusRequester()
    val passwordFocusRequester = FocusRequester()
    val signInFocusRequester = FocusRequester()
    val signUpFocusRequester = FocusRequester()
    val submitEnabled = !state.isSubmitting && email.isNotBlank() && password.isNotBlank()
    val submitSignIn = {
        if (submitEnabled) {
            onSignIn(email.trim(), password)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SelectionContainer {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Welcome")
                Text("Sign in or create an account to access your web feed.")
                if (state.message != null) {
                    Text(state.message)
                }
            }
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(emailFocusRequester)
                .onPreviewKeyEvent {
                    handleTabNavigation(
                        isTab = it.key == Key.Tab && it.type == KeyEventType.KeyDown,
                        isShiftPressed = it.isShiftPressed,
                        onNext = { passwordFocusRequester.requestFocus() },
                        onPrevious = { },
                    )
                }
                .testTag("login-email-field"),
            label = { Text("Email") },
            enabled = !state.isSubmitting,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { passwordFocusRequester.requestFocus() },
            ),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester)
                .onPreviewKeyEvent {
                    handleAuthFieldNavigation(
                        isTab = it.key == Key.Tab && it.type == KeyEventType.KeyDown,
                        isEnter = isEnterKeyDown(it),
                        isShiftPressed = it.isShiftPressed,
                        onNext = { signInFocusRequester.requestFocus() },
                        onPrevious = { emailFocusRequester.requestFocus() },
                        onEnter = submitSignIn,
                    )
                }
                .testTag("login-password-field"),
            label = { Text("Password") },
            enabled = !state.isSubmitting,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { submitSignIn() },
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { onSignIn(email.trim(), password) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(signInFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent {
                        handleTabNavigation(
                            isTab = it.key == Key.Tab && it.type == KeyEventType.KeyDown,
                            isShiftPressed = it.isShiftPressed,
                            onNext = { signUpFocusRequester.requestFocus() },
                            onPrevious = { passwordFocusRequester.requestFocus() },
                        )
                    }
                    .testTag("login-submit-button"),
                enabled = submitEnabled,
            ) {
                Text(
                    if (state.isSubmitting && state.activeAction == AuthAction.SignIn) "Signing in..." else "Sign in",
                )
            }
            OutlinedButton(
                onClick = { onSignUp(email.trim(), password) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(signUpFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent {
                        handleTabNavigation(
                            isTab = it.key == Key.Tab && it.type == KeyEventType.KeyDown,
                            isShiftPressed = it.isShiftPressed,
                            onNext = { focusManager.moveFocus(FocusDirection.Exit) },
                            onPrevious = { signInFocusRequester.requestFocus() },
                        )
                    }
                    .testTag("login-sign-up-button"),
                enabled = submitEnabled,
            ) {
                Text(
                    if (state.isSubmitting && state.activeAction == AuthAction.SignUp) {
                        "Creating account..."
                    } else {
                        "Create account"
                    },
                )
            }
        }
    }
}

private fun handleAuthFieldNavigation(
    isTab: Boolean,
    isEnter: Boolean,
    isShiftPressed: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onEnter: () -> Unit = {},
): Boolean {
    if (isEnter) {
        onEnter()
        return true
    }
    if (!isTab) return false
    if (isShiftPressed) {
        onPrevious()
    } else {
        onNext()
    }
    return true
}

private fun isEnterKeyDown(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return event.key == Key.Enter
}

private fun handleTabNavigation(
    isTab: Boolean,
    isShiftPressed: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
): Boolean = handleAuthFieldNavigation(
    isTab = isTab,
    isEnter = false,
    isShiftPressed = isShiftPressed,
    onNext = onNext,
    onPrevious = onPrevious,
)
