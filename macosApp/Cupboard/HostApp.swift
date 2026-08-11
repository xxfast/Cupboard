// Spike: Keynote-shaped SwiftUI shell around the Compose canvas.
// Sidebar (navigator) is pure SwiftUI; only the canvas well is Compose,
// bridged through EditorHost from the CupboardCanvas Kotlin framework.
// Build/run: ./macosApp/run.sh
import SwiftUI
import AppKit
import Observation
import CupboardCanvas

struct ComposeCanvas: NSViewRepresentable {
    let host: EditorHost

    func makeNSView(context: Context) -> NSView { host.view }
    func updateNSView(_ nsView: NSView, context: Context) {}
}

/// Full-bleed play surface. The session owns a Compose scene, so it has to be
/// disposed when SwiftUI drops the view; the coordinator carries it there.
struct PlayCanvas: NSViewRepresentable {
    let session: PlaySession

    func makeCoordinator() -> Coordinator { Coordinator(session: session) }
    func makeNSView(context: Context) -> NSView { session.view }
    func updateNSView(_ nsView: NSView, context: Context) {}

    static func dismantleNSView(_ nsView: NSView, coordinator: Coordinator) {
        coordinator.session.dispose()
    }

    class Coordinator {
        let session: PlaySession
        init(session: PlaySession) { self.session = session }
    }
}

/// Observation bridge over the Kotlin store. Compose state is invisible to
/// SwiftUI, so the host tells us when anything changed and we bump [generation];
/// views that read it re-pull the outline and thumbnails. This is what makes
/// canvas-side edits show up in the sidebar, not just the other way around.
@Observable
final class EditorModel {
    let host = EditorHost()
    private(set) var generation: Int = 0
    @ObservationIgnored private var unsubscribe: (() -> Void)?

    init() {
        // Kotlin notifies synchronously on whichever thread mutated, which is
        // always the main thread here (SwiftUI calls, or Compose input).
        unsubscribe = host.onChange { [weak self] in self?.generation += 1 }
    }

    deinit {
        unsubscribe?()
    }
}

@main
struct CupboardHostApp: App {
    @State private var model = EditorModel()
    @State private var playSession: PlaySession?

    private var host: EditorHost { model.host }

    /// Selection lives in the store; this is a window onto it, no mirror to sync.
    private var selection: Binding<Int> {
        Binding(
            get: { Int(host.selectedSlideIndex()) },
            set: { host.selectSlide(index: Int32($0)) }
        )
    }

    var body: some Scene {
        WindowGroup("Cupboard") {
            if let session = playSession {
                PlayCanvas(session: session)
                    .background(Color.black)
                    .ignoresSafeArea()
            } else {
                editor
            }
        }
    }

    private var editor: some View {
        NavigationSplitView {
            List(selection: selection) {
                // Reading generation is what subscribes this list to store changes.
                let _ = model.generation
                ForEach(Array(host.outline().enumerated()), id: \.offset) { _, row in
                    if row.slideIndex < 0 {
                        Text(row.title)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(.secondary)
                            .padding(.leading, CGFloat(row.depth) * 14)
                    } else {
                        let selected = Int(row.slideIndex) == selection.wrappedValue
                        HStack(alignment: .top, spacing: 8) {
                            Text("\(row.slideIndex + 1)")
                                .font(.system(size: 11, design: .monospaced))
                                .foregroundStyle(.secondary)
                                .frame(width: 16, alignment: .trailing)
                            // Thumbnail rendered by the shared Compose renderer
                            if let thumb = host.thumbnail(index: row.slideIndex, width: 150) {
                                Image(nsImage: thumb)
                                    .resizable()
                                    .aspectRatio(contentMode: .fit)
                                    .clipShape(RoundedRectangle(cornerRadius: 5))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 5)
                                            .stroke(
                                                selected ? Color(red: 0.5, green: 0.32, blue: 1.0) : Color.gray.opacity(0.4),
                                                lineWidth: selected ? 2 : 1
                                            )
                                    )
                            } else {
                                Text(row.title).font(.system(size: 13))
                            }
                        }
                        .padding(.leading, CGFloat(row.depth) * 14)
                        .tag(Int(row.slideIndex))
                    }
                }
            }
            .navigationSplitViewColumnWidth(min: 180, ideal: 224)
        } detail: {
            ComposeCanvas(host: host)
                .padding(28)
                .background(Color(nsColor: NSColor(calibratedWhite: 0.09, alpha: 1)))
        }
        .frame(minWidth: 900, minHeight: 560)
        .toolbar {
            Button {
                startPlay()
            } label: {
                Label("Play", systemImage: "play.fill")
            }
            .keyboardShortcut(.return, modifiers: .command)
            .help("Play from the selected slide")
        }
    }

    private func startPlay() {
        // Kotlin calls onExit on the main thread, so touching @State is safe.
        playSession = host.startPlay(onExit: { playSession = nil })
    }
}
