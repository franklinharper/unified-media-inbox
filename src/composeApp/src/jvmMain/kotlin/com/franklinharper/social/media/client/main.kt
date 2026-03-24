package com.franklinharper.social.media.client

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Social Media Client",
    ) {
        App()
    }
}
