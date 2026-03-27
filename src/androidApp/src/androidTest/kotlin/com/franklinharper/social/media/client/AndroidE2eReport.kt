package com.franklinharper.social.media.client

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class AndroidE2eIssueType {
    warning,
    handled_error,
    assertion_failure,
    crash,
}

data class AndroidE2eIssue(
    val type: AndroidE2eIssueType,
    val screen: String,
    val step: String,
    val message: String? = null,
    val exception: String? = null,
    val logExcerpt: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type.name)
        .put("screen", screen)
        .put("step", step)
        .put("message", message)
        .put("exception", exception)
        .put("logExcerpt", logExcerpt)
}

data class AndroidE2eStepResult(
    val screen: String,
    val step: String,
    val status: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("screen", screen)
        .put("step", step)
        .put("status", status)
}

class AndroidE2eReport(
    private val outputFiles: List<File>,
) {
    private val steps = mutableListOf<AndroidE2eStepResult>()
    private val issues = mutableListOf<AndroidE2eIssue>()

    fun noteStepStarted(screen: String, step: String) {
        steps += AndroidE2eStepResult(screen = screen, step = step, status = "started")
        writeSnapshot()
    }

    fun noteStepPassed(screen: String, step: String) {
        steps += AndroidE2eStepResult(screen = screen, step = step, status = "passed")
        writeSnapshot()
    }

    fun noteWarning(screen: String, step: String, message: String) {
        issues += AndroidE2eIssue(
            type = AndroidE2eIssueType.warning,
            screen = screen,
            step = step,
            message = message,
        )
        writeSnapshot()
    }

    fun noteHandledError(screen: String, step: String, message: String) {
        issues += AndroidE2eIssue(
            type = AndroidE2eIssueType.handled_error,
            screen = screen,
            step = step,
            message = message,
        )
        writeSnapshot()
    }

    fun noteAssertionFailure(
        screen: String,
        step: String,
        expected: String,
        actual: String?,
        throwable: Throwable,
    ) {
        val detail = buildString {
            append("Expected: ")
            append(expected)
            if (!actual.isNullOrBlank()) {
                append(" | Actual: ")
                append(actual)
            }
        }
        issues += AndroidE2eIssue(
            type = AndroidE2eIssueType.assertion_failure,
            screen = screen,
            step = step,
            message = detail,
            exception = throwable.stackTraceToString(),
        )
        steps += AndroidE2eStepResult(screen = screen, step = step, status = "failed")
        writeSnapshot()
    }

    private fun writeSnapshot() {
        val snapshot = JSONObject()
            .put("steps", JSONArray(steps.map(AndroidE2eStepResult::toJson)))
            .put("issues", JSONArray(issues.map(AndroidE2eIssue::toJson)))
            .toString()

        outputFiles.forEach { outputFile ->
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(snapshot)
        }
    }

    companion object {
        private const val FILE_NAME = "android-e2e-report.json"

        fun file(context: Context): File = File(context.filesDir, FILE_NAME)

        fun files(context: Context): List<File> =
            listOfNotNull(
                file(context),
                additionalOutputFile(FILE_NAME),
            )

        private fun additionalOutputFile(fileName: String): File? {
            val outputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?.takeIf { it.isNotBlank() }
                ?: return null
            return File(outputDir, fileName)
        }
    }
}
