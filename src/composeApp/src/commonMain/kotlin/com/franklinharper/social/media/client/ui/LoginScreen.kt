package com.franklinharper.social.media.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.franklinharper.social.media.client.app.LoginUiState

@Composable
fun LoginScreen(
    state: LoginUiState,
    onSignIn: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Sign in")
        Text("Use your account to access your web feed.")
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login-email-field"),
            label = { Text("Email") },
            enabled = !state.isSubmitting,
            singleLine = true,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login-password-field"),
            label = { Text("Password") },
            enabled = !state.isSubmitting,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        if (state.message != null) {
            Text(state.message)
        }
        Button(
            onClick = { onSignIn(email.trim(), password) },
            modifier = Modifier.testTag("login-submit-button"),
            enabled = !state.isSubmitting && email.isNotBlank() && password.isNotBlank(),
        ) {
            Text(if (state.isSubmitting) "Signing in..." else "Sign in")
        }
    }
}
