package io.github.xxfast.slides

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "slides-kt",
    ) {
        App()
    }
}