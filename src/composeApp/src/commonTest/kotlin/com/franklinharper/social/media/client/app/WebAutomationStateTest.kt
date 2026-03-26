package com.franklinharper.social.media.client.app

import kotlin.test.Test
import kotlin.test.assertEquals

class WebAutomationStateTest {

    @Test
    fun `submit sign in forwards trimmed credentials through bound action`() {
        val state = WebAutomationState()
        var submitted: Pair<String, String>? = null

        state.bindActions(
            onSignIn = { email, password -> submitted = email to password },
            onSignUp = { _, _ -> },
            onRefreshFeed = {},
            onSignOut = {},
            onOpenAddSourceScreen = {},
            onSelectRssSourceType = {},
            onAddRssSource = {},
        )
        state.updateEmail(" alice@example.com ")
        state.updatePassword("secret123")

        state.submitSignIn()

        assertEquals("alice@example.com" to "secret123", submitted)
    }

    @Test
    fun `submit rss source opens add-source flow selects rss and forwards trimmed url`() {
        val state = WebAutomationState()
        val events = mutableListOf<String>()

        state.bindActions(
            onSignIn = { _, _ -> },
            onSignUp = { _, _ -> },
            onRefreshFeed = {},
            onSignOut = {},
            onOpenAddSourceScreen = { events += "open" },
            onSelectRssSourceType = { events += "select-rss" },
            onAddRssSource = { url -> events += "add:$url" },
        )
        state.updateRssUrl(" https://hnrss.org/frontpage ")

        state.submitRssSource()

        assertEquals(
            listOf(
                "open",
                "select-rss",
                "add:https://hnrss.org/frontpage",
            ),
            events,
        )
    }

    @Test
    fun `invalid automation inputs do not invoke bound actions`() {
        val state = WebAutomationState()
        val events = mutableListOf<String>()

        state.bindActions(
            onSignIn = { _, _ -> events += "sign-in" },
            onSignUp = { _, _ -> events += "sign-up" },
            onRefreshFeed = { events += "refresh" },
            onSignOut = { events += "sign-out" },
            onOpenAddSourceScreen = { events += "open" },
            onSelectRssSourceType = { events += "select-rss" },
            onAddRssSource = { events += "add:$it" },
        )

        state.submitSignIn()
        state.submitSignUp()
        state.submitRssSource()

        assertEquals(emptyList(), events)
    }

    @Test
    fun `environment updates expose feed metadata for automation assertions`() {
        val state = WebAutomationState()

        state.updateEnvironment(
            authVisible = false,
            feedVisible = true,
            isSubmitting = false,
            isAddingSource = false,
            feedItemCount = 2,
            feedSourceNames = listOf("Hacker News"),
            feedItemTitles = listOf("Story one", "Story two"),
        )

        assertEquals(2, state.uiState.value.feedItemCount)
        assertEquals(listOf("Hacker News"), state.uiState.value.feedSourceNames)
        assertEquals(listOf("Story one", "Story two"), state.uiState.value.feedItemTitles)
    }
}
