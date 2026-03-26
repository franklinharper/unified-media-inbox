package com.franklinharper.social.media.client.app

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement

private const val AUTOMATION_QUERY_PARAM = "automationBridge"

internal fun createWebAutomationStateOrNull(): WebAutomationState? =
    if (window.location.search.contains("$AUTOMATION_QUERY_PARAM=1")) WebAutomationState() else null

internal fun installWebAutomationBridge(state: WebAutomationState?) {
    if (state == null) return

    val body = document.body ?: return
    val existing = document.getElementById("e2e-automation-bridge")
    if (existing != null) return

    val bridge = document.createElement("div") as HTMLDivElement
    bridge.id = "e2e-automation-bridge"
    bridge.setAttribute("data-testid", "e2e-automation-bridge")
    bridge.setAttribute(
        "style",
        """
        position:fixed;
        left:12px;
        top:12px;
        z-index:2147483647;
        display:flex;
        flex-direction:column;
        gap:8px;
        width:320px;
        max-width:320px;
        padding:12px;
        background:rgba(255,255,255,0.96);
        border:1px solid rgba(0,0,0,0.18);
        border-radius:12px;
        box-shadow:0 10px 30px rgba(0,0,0,0.18);
        font:12px/1.4 sans-serif;
        color:#111;
        visibility:visible;
        opacity:1;
        pointer-events:auto;
        """.trimIndent(),
    )
    bridge.hidden = false

    val authPanel = document.createElement("div") as HTMLDivElement
    authPanel.setAttribute("style", "display:flex; flex-direction:column; gap:8px;")

    val emailInput = createInput("email", "e2e-auth-email")
    val passwordInput = createInput("password", "e2e-auth-password")
    val signInButton = createButton("Sign in", "e2e-auth-sign-in")
    val signUpButton = createButton("Create account", "e2e-auth-sign-up")
    val authButtonRow = document.createElement("div") as HTMLDivElement
    authButtonRow.setAttribute("style", "display:flex; gap:8px;")
    authButtonRow.append(signInButton, signUpButton)
    authPanel.append(emailInput, passwordInput, authButtonRow)

    val feedPanel = document.createElement("div") as HTMLDivElement
    feedPanel.setAttribute("style", "display:flex; gap:8px; flex-wrap:wrap;")
    val refreshButton = createButton("Refresh", "e2e-feed-refresh")
    val signOutButton = createButton("Sign out", "e2e-feed-sign-out")
    val rssUrlInput = createInput("url", "e2e-add-source-rss-url")
    val rssAddButton = createButton("Add RSS", "e2e-add-source-submit-rss")
    feedPanel.append(refreshButton, signOutButton, rssUrlInput, rssAddButton)

    bridge.append(authPanel, feedPanel)
    body.appendChild(bridge)

    emailInput.oninput = {
        state.updateEmail(emailInput.value)
        null
    }
    passwordInput.oninput = {
        state.updatePassword(passwordInput.value)
        null
    }
    rssUrlInput.oninput = {
        state.updateRssUrl(rssUrlInput.value)
        null
    }
    signInButton.onclick = {
        state.submitSignIn()
        null
    }
    signUpButton.onclick = {
        state.submitSignUp()
        null
    }
    refreshButton.onclick = {
        state.refreshFeed()
        null
    }
    signOutButton.onclick = {
        state.signOut()
        null
    }
    rssAddButton.onclick = {
        state.submitRssSource()
        null
    }

    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        state.uiState.collectLatest { uiState ->
            emailInput.value = uiState.email
            passwordInput.value = uiState.password
            rssUrlInput.value = uiState.rssUrl

            signInButton.disabled = !uiState.canSubmitAuth
            signUpButton.disabled = !uiState.canSubmitAuth
            refreshButton.disabled = false
            signOutButton.disabled = false
            rssAddButton.disabled = !uiState.canSubmitRss
        }
    }
}

private fun createInput(type: String, testId: String): HTMLInputElement {
    val input = document.createElement("input") as HTMLInputElement
    input.type = type
    input.setAttribute("data-testid", testId)
    input.setAttribute(
        "style",
        """
        display:block;
        width:100%;
        min-height:36px;
        box-sizing:border-box;
        padding:8px 10px;
        background:#fff;
        border:1px solid rgba(0,0,0,0.18);
        border-radius:8px;
        visibility:visible;
        opacity:1;
        """.trimIndent(),
    )
    return input
}

private fun createButton(label: String, testId: String): HTMLButtonElement {
    val button = document.createElement("button") as HTMLButtonElement
    button.textContent = label
    button.setAttribute("data-testid", testId)
    button.setAttribute(
        "style",
        """
        display:inline-flex;
        align-items:center;
        justify-content:center;
        min-height:36px;
        padding:8px 10px;
        border:1px solid rgba(0,0,0,0.18);
        border-radius:999px;
        background:#fff;
        cursor:pointer;
        visibility:visible;
        opacity:1;
        """.trimIndent(),
    )
    return button
}
