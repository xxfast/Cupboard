import androidx.compose.ui.window.Window
import io.github.xxfast.cupboard.canvas.CanvasDemo
import platform.AppKit.NSApp
import platform.AppKit.NSApplication
import platform.AppKit.NSApplicationActivationPolicy

fun main() {
    NSApplication.sharedApplication()
    // Bare executables default to an accessory activation policy; without this
    // the window can't become key and keyboard input goes to the previous app.
    NSApp?.setActivationPolicy(NSApplicationActivationPolicy.NSApplicationActivationPolicyRegular)
    Window(title = "Cupboard") {
        CanvasDemo()
    }
    NSApp?.activateIgnoringOtherApps(true)
    NSApp?.run()
}
