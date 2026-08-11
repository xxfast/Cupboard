// Spike: Keynote-shaped SwiftUI shell around the Compose canvas.
// Sidebar (navigator) is pure SwiftUI; only the canvas well is Compose,
// bridged through EditorHost from the CupboardCanvas Kotlin framework.
// Build/run: ./macosApp/run.sh
import SwiftUI
import AppKit
import CupboardCanvas

struct ComposeCanvas: NSViewRepresentable {
    let host: EditorHost

    func makeNSView(context: Context) -> NSView { host.view }
    func updateNSView(_ nsView: NSView, context: Context) {}
}

@main
struct CupboardHostApp: App {
    private let host = EditorHost()
    @State private var selection: Int = 0

    var body: some Scene {
        WindowGroup("Cupboard") {
            NavigationSplitView {
                List(selection: $selection) {
                    ForEach(Array(host.outline().enumerated()), id: \.offset) { _, row in
                        if row.slideIndex < 0 {
                            Text(row.title)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(.secondary)
                                .padding(.leading, CGFloat(row.depth) * 14)
                        } else {
                            let selected = Int(row.slideIndex) == selection
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
                .onAppear { selection = Int(host.selectedSlideIndex()) }
                .onChange(of: selection) { _, index in
                    host.selectSlide(index: Int32(index))
                }
            } detail: {
                ComposeCanvas(host: host)
                    .padding(28)
                    .background(Color(nsColor: NSColor(calibratedWhite: 0.09, alpha: 1)))
            }
            .frame(minWidth: 900, minHeight: 560)
        }
    }
}
