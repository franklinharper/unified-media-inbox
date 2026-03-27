package com.franklinharper.social.media.client

import android.content.Context
import org.json.JSONObject
import java.io.File

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

        fun update(context: Context, screen: String, step: String) {
            AndroidE2eProgress(
                screen = screen,
                step = step,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ).writeTo(file(context))
        }
    }
}
