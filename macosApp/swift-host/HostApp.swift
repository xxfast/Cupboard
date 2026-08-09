// Spike: SwiftUI app hosting the Compose canvas from the CupboardCanvas Kotlin framework.
// Build/run: ./macosApp/swift-host/run.sh
import SwiftUI
import AppKit
import CupboardCanvas

// Running as a bare executable (no .app bundle) defaults to an accessory
// activation policy: windows can't become key and typing goes elsewhere.
final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        NSApp.activate(ignoringOtherApps: true)
    }
}

struct ComposeCanvas: NSViewRepresentable {
    func makeNSView(context: Context) -> NSView {
        ComposeNSViewKt.createCanvasView()
    }

    func updateNSView(_ nsView: NSView, context: Context) {}
}

@main
struct CupboardHostApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @State private var slideName = "Rendering Pipeline"

    var body: some Scene {
        WindowGroup("Cupboard — SwiftUI host") {
            VStack(spacing: 0) {
                // Stand-in for the native chrome
                HStack(spacing: 12) {
                    Text("SwiftUI chrome").fontWeight(.semibold)
                    Spacer()
                    TextField("Slide name (SwiftUI)", text: $slideName)
                        .textFieldStyle(.roundedBorder)
                        .frame(width: 220)
                    Button("Native button") {}
                }
                .padding(10)
                Divider()
                // The shared Compose canvas
                ComposeCanvas()
            }
            .frame(minWidth: 800, minHeight: 560)
        }
    }
}
