import androidx.compose.ui.window.Window
import io.github.xxfast.cupboard.App
import platform.AppKit.NSApp
import platform.AppKit.NSApplication

fun main() {
    NSApplication.sharedApplication()
    Window(title = "Cupboard") {
        App()
    }
    NSApp?.run()
}
