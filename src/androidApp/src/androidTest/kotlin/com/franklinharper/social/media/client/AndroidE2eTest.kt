package com.franklinharper.social.media.client

import android.content.Intent
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidE2eTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun signUpAddSourceSignOutSignInRefreshFlow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        targetContext.deleteDatabase("social-media-client.db")
        AndroidE2eProgress.file(targetContext).delete()
        AndroidE2eReport.file(targetContext).delete()

        val launchIntent = requireNotNull(
            targetContext.packageManager.getLaunchIntentForPackage("com.franklinharper.social.media.client"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        instrumentation.startActivitySync(launchIntent)

        val report = AndroidE2eReport(AndroidE2eReport.file(targetContext))
        val email = "android-e2e-${System.currentTimeMillis()}@example.com"
        val password = "secret123"

        step(report, screen = "login", name = "sign_up", expected = "sign up reaches feed screen") {
            waitForTag("login-email-field")
            replaceText("login-email-field", email)
            replaceText("login-password-field", password)
            composeRule.onNodeWithTag("login-sign-up-button").performClick()
            waitForTag("feed-add-source-fab")
            assertNoHandledErrors(screen = "login", step = "sign_up", report = report)
        }

        step(report, screen = "add_source", name = "add_rss_source", expected = "adding the RSS source returns to feed") {
            composeRule.onNodeWithTag("feed-add-source-fab").performClick()
            composeRule.onNodeWithText("RSS feed").performClick()
            waitForTag("add-source-rss-url-field")
            replaceText("add-source-rss-url-field", FIXTURE_RSS_URL)
            composeRule.onNodeWithTag("add-source-rss-submit-button").performClick()
            waitForTag("feed-refresh-button")
            assertNoHandledErrors(screen = "add_source", step = "add_rss_source", report = report)
        }

        step(report, screen = "feed", name = "verify_initial_feed", expected = "refresh loads the expected fixture feed titles") {
            composeRule.onNodeWithTag("feed-refresh-button").performClick()
            waitForText(FIXTURE_TITLES.first())
            assertFeedTitles()
            assertNoHandledErrors(screen = "feed", step = "verify_initial_feed", report = report)
        }

        step(report, screen = "feed", name = "sign_out", expected = "sign out returns to login") {
            composeRule.onNodeWithTag("feed-sign-out-button").performClick()
            waitForTag("login-submit-button")
            assertNoHandledErrors(screen = "feed", step = "sign_out", report = report)
        }

        step(report, screen = "login", name = "sign_in", expected = "sign in returns to feed") {
            replaceText("login-email-field", email)
            replaceText("login-password-field", password)
            composeRule.onNodeWithTag("login-submit-button").performClick()
            waitForTag("feed-refresh-button")
            assertNoHandledErrors(screen = "login", step = "sign_in", report = report)
        }

        step(report, screen = "feed", name = "refresh", expected = "refresh completes without handled errors") {
            composeRule.onNodeWithTag("feed-refresh-button").performClick()
            waitForText(FIXTURE_TITLES.first())
            assertNoHandledErrors(screen = "feed", step = "refresh", report = report)
        }

        step(report, screen = "feed", name = "verify_final_feed", expected = "final feed still shows the expected fixture titles") {
            assertFeedTitles()
            assertNoHandledErrors(screen = "feed", step = "verify_final_feed", report = report)
        }
    }

    private fun step(
        report: AndroidE2eReport,
        screen: String,
        name: String,
        expected: String,
        block: () -> Unit,
    ) {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        AndroidE2eProgress.update(targetContext, screen = screen, step = name)
        report.noteStepStarted(screen = screen, step = name)
        try {
            block()
            report.noteStepPassed(screen = screen, step = name)
        } catch (throwable: Throwable) {
            val actual = currentVisibleErrorMessage()
            report.noteAssertionFailure(
                screen = screen,
                step = name,
                expected = expected,
                actual = actual,
                throwable = throwable,
            )
            throw throwable
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag(tag).hasAnyNodes() || currentVisibleErrorMessage() != null
        }
        composeRule.onNodeWithTag(tag).assertExists()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(text, substring = false).hasAnyNodes() || currentVisibleErrorMessage() != null
        }
    }

    private fun replaceText(tag: String, value: String) {
        composeRule.onNodeWithTag(tag).performTextClearance()
        composeRule.onNodeWithTag(tag).performTextInput(value)
    }

    private fun assertFeedTitles() {
        FIXTURE_TITLES.forEach { title ->
            composeRule.onNodeWithText(title).assertExists()
        }
    }

    private fun assertNoHandledErrors(screen: String, step: String, report: AndroidE2eReport) {
        currentVisibleErrorMessage()?.let { message ->
            report.noteHandledError(screen = screen, step = step, message = message)
            throw AssertionError("Handled error visible on $screen/$step: $message")
        }
    }

    private fun currentVisibleErrorMessage(): String? {
        val tags = listOf("login-error", "add-source-error", "feed-load-error")
        return tags.firstNotNullOfOrNull { tag ->
            val nodes = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodesSafely()
            nodes.firstOrNull()
                ?.config
                ?.takeIf { it.contains(SemanticsProperties.Text) }
                ?.get(SemanticsProperties.Text)
                ?.joinToString(separator = "") { annotated -> annotated.text }
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun SemanticsNodeInteractionCollection.hasAnyNodes(): Boolean =
        fetchSemanticsNodesSafely().isNotEmpty()

    private fun SemanticsNodeInteractionCollection.fetchSemanticsNodesSafely() =
        try {
            fetchSemanticsNodes(atLeastOneRootRequired = false)
        } catch (_: AssertionError) {
            emptyList()
        }

    companion object {
        private const val FIXTURE_RSS_URL = "http://10.0.2.2:9090/feeds/hn-frontpage.xml"
        private val FIXTURE_TITLES = listOf(
            "Launch HN: Deterministic Feed Fixtures",
            "Ask HN: How do you de-flake end-to-end tests?",
        )
    }
}
