package com.franklinharper.social.media.client

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidE2eSmokeTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun launchesLoginScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.targetContext.deleteDatabase("social-media-client.db")

        val launchIntent = requireNotNull(
            instrumentation.targetContext.packageManager.getLaunchIntentForPackage(
                "com.franklinharper.social.media.client",
            ),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        instrumentation.startActivitySync(launchIntent)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("login-email-field").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("login-email-field").assertExists()
    }
}
