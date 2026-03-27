package com.franklinharper.social.media.client

import android.content.Context
import org.json.JSONObject
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry

data class AndroidE2eProgress(
    val screen: String,
    val step: String,
    val updatedAtEpochMillis: Long,
) {
    fun writeTo(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(
            JSONObject()
                .put("screen", screen)
                .put("step", step)
                .put("updatedAtEpochMillis", updatedAtEpochMillis)
                .toString(),
        )
    }

    companion object {
        private const val FILE_NAME = "android-e2e-progress.json"

        fun file(context: Context): File = File(context.filesDir, FILE_NAME)

        fun files(context: Context): List<File> =
            listOfNotNull(
                file(context),
                additionalOutputFile(FILE_NAME),
            )

        fun update(context: Context, screen: String, step: String) {
            val snapshot = AndroidE2eProgress(
                screen = screen,
                step = step,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            files(context).forEach(snapshot::writeTo)
        }

        private fun additionalOutputFile(fileName: String): File? {
            val outputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?.takeIf { it.isNotBlank() }
                ?: return null
            return File(outputDir, fileName)
        }
    }
}
