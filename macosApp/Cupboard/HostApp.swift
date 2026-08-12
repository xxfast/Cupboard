// Design v3 layered window: the Compose canvas is full-bleed edge to edge and
// the sidebar, toolbar and inspector float over it as translucent glass.
// Only the canvas is Compose, bridged through EditorHost from CupboardCanvas.
// Build/run: ./macosApp/run.sh
import SwiftUI
import AppKit
import Observation
import CupboardCanvas

// MARK: - Chrome tokens

/// macOS chrome, transcribed from design/platform-theme.js. Chrome follows the
/// system appearance; slide content and thumbnails stay document-dark in both.
private struct Palette {
    let text: Color
    let title: Color
    let label: Color
    let icon: Color
    let subtle: Color
    let faint: Color
    let ctrl: Color
    let ctrlText: Color
    let segBg: Color
    let segOn: Color
    let segOnText: Color
    let segOff: Color
    let accent: Color
    let accentSoft: Color
    let hover: Color
    let hover2: Color
    /// The glass fill painted over the blur.
    let glassFill: LinearGradient
    let hairline: Color
    let innerHighlight: Color
    let topHighlight: Color

    static let dark = Palette(
        text: Color(rgb: 0xE8E8EA),
        title: Color(rgb: 0xD8D8DC),
        label: Color(rgb: 0xB8B8BE),
        icon: Color(rgb: 0xD0D0D5),
        subtle: Color(rgb: 0x98989F),
        faint: Color(rgb: 0x6E6E76),
        ctrl: Color(rgb: 0x414147),
        ctrlText: Color(rgb: 0xECECEE),
        segBg: Color(rgb: 0x313136),
        segOn: Color(rgb: 0x5C5C64),
        segOnText: .white,
        segOff: Color(rgb: 0xC8C8CC),
        accent: Color(rgb: 0x7F52FF),
        accentSoft: Color(rgb: 0xB9A3FF),
        hover: Color.white.opacity(0.07),
        hover2: Color.white.opacity(0.14),
        // rgba(44,44,50,0.56) to rgba(32,32,38,0.48)
        glassFill: LinearGradient(
            colors: [Color(rgb: 0x2C2C32).opacity(0.56), Color(rgb: 0x202026).opacity(0.48)],
            startPoint: .top,
            endPoint: .bottom
        ),
        hairline: Color.white.opacity(0.10),
        innerHighlight: Color.white.opacity(0.05),
        topHighlight: Color.white.opacity(0.06)
    )

    static let light = Palette(
        text: Color(rgb: 0x2A2A2C),
        title: Color(rgb: 0x3A3A3C),
        label: Color(rgb: 0x5C5C5E),
        icon: Color(rgb: 0x4A4A4C),
        subtle: Color(rgb: 0x7A7A7E),
        faint: Color(rgb: 0x9A9A9E),
        ctrl: .white,
        ctrlText: Color(rgb: 0x2A2A2C),
        segBg: Color(rgb: 0xE1E0DE),
        segOn: .white,
        segOnText: Color(rgb: 0x1D1D1F),
        segOff: Color(rgb: 0x5A5A5C),
        accent: Color(rgb: 0x7F52FF),
        accentSoft: Color(rgb: 0x6F42E0),
        hover: Color.black.opacity(0.06),
        hover2: Color.black.opacity(0.1),
        // rgba(252,251,249,0.64) to rgba(244,243,241,0.54)
        glassFill: LinearGradient(
            colors: [Color(rgb: 0xFCFBF9).opacity(0.64), Color(rgb: 0xF4F3F1).opacity(0.54)],
            startPoint: .top,
            endPoint: .bottom
        ),
        hairline: Color.black.opacity(0.10),
        innerHighlight: Color.white.opacity(0.7),
        topHighlight: Color.white.opacity(0.8)
    )

    static func of(_ scheme: ColorScheme) -> Palette { scheme == .dark ? .dark : .light }
}

private enum Layout {
    static let sidebar: CGFloat = 212
    static let inspector: CGFloat = 282
    static let header: CGFloat = 52
    /// Kept clear of the traffic lights when the sidebar is hidden.
    static let trafficLights: CGFloat = 86
}

private extension Color {
    init(rgb: UInt32) {
        self.init(
            .sRGB,
            red: Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >> 8) & 0xFF) / 255,
            blue: Double(rgb & 0xFF) / 255
        )
    }
}

// MARK: - Glass

/// The blur under a glass surface. Within-window so it samples the Compose
/// canvas layered behind it rather than the desktop.
private struct GlassBackdrop: NSViewRepresentable {
    let material: NSVisualEffectView.Material

    func makeNSView(context: Context) -> NSVisualEffectView {
        let view = NSVisualEffectView()
        view.material = material
        view.blendingMode = .withinWindow
        view.state = .active
        return view
    }

    func updateNSView(_ view: NSVisualEffectView, context: Context) {
        view.material = material
    }
}

private enum GlassEdge { case leading, trailing, bottom }

private struct Glass: ViewModifier {
    let material: NSVisualEffectView.Material
    let edge: GlassEdge
    let palette: Palette

    func body(content: Content) -> some View {
        content
            .background(GlassBackdrop(material: material))
            .background(palette.glassFill)
            .overlay(alignment: alignment) { hairline }
            .overlay(alignment: .top) { topHighlight }
    }

    private var alignment: Alignment {
        switch edge {
        case .leading: return .leading
        case .trailing: return .trailing
        case .bottom: return .bottom
        }
    }

    /// The inner edge carries the 1px hairline with a fainter highlight just
    /// inside it (CSS: border + an inset shadow offset one pixel inward).
    @ViewBuilder private var hairline: some View {
        switch edge {
        case .leading:
            HStack(spacing: 0) {
                palette.hairline.frame(width: 1)
                palette.innerHighlight.frame(width: 1)
            }
        case .trailing:
            HStack(spacing: 0) {
                palette.innerHighlight.frame(width: 1)
                palette.hairline.frame(width: 1)
            }
        case .bottom:
            palette.hairline.frame(height: 1)
        }
    }

    @ViewBuilder private var topHighlight: some View {
        if edge == .bottom { palette.topHighlight.frame(height: 1) }
    }
}

private extension View {
    func glass(_ material: NSVisualEffectView.Material, edge: GlassEdge, palette: Palette) -> some View {
        modifier(Glass(material: material, edge: edge, palette: palette))
    }
}

// MARK: - Window

/// There is no title bar: the traffic lights sit in the sidebar header instead,
/// so the content view has to run the full height of the window.
private struct WindowConfigurator: NSViewRepresentable {
    func makeNSView(context: Context) -> NSView {
        let view = NSView(frame: .zero)
        DispatchQueue.main.async {
            guard let window = view.window else { return }
            window.titlebarAppearsTransparent = true
            window.titleVisibility = .hidden
            window.styleMask.insert(.fullSizeContentView)
        }
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {}
}

// MARK: - Compose surfaces

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

// MARK: - App

@main
struct CupboardHostApp: App {
    @State private var model = EditorModel()
    @State private var playSession: PlaySession?

    private var host: EditorHost { model.host }

    var body: some Scene {
        WindowGroup("Cupboard") {
            Group {
                if let session = playSession {
                    PlayCanvas(session: session)
                        .background(Color.black)
                } else {
                    EditorView(model: model, playSession: $playSession)
                }
            }
            .ignoresSafeArea()
            .background(WindowConfigurator().frame(width: 0, height: 0))
        }
        .windowStyle(.hiddenTitleBar)
        .commands {
            // Ours, not AppKit's: the history lives in Kotlin, so the system
            // undo manager has nothing to say about it.
            CommandGroup(replacing: .undoRedo) {
                // Reading generation is what keeps these enabled states fresh.
                let _ = model.generation
                Button("Undo") { host.undo() }
                    .keyboardShortcut("z", modifiers: .command)
                    .disabled(!host.canUndo())
                Button("Redo") { host.redo() }
                    .keyboardShortcut("z", modifiers: [.command, .shift])
                    .disabled(!host.canRedo())
            }
        }
    }
}

// MARK: - Editor

private enum InspectorTab { case format, animate }

private struct EditorView: View {
    let model: EditorModel
    @Binding var playSession: PlaySession?

    @Environment(\.colorScheme) private var colorScheme
    @State private var sidebarVisible = true
    @State private var inspectorTab = InspectorTab.format
    /// Zoom is view-local in the Kotlin host, outside `states`, so the label
    /// reads from this mirror rather than waiting on a generation bump.
    @State private var zoomPercent: Int = 0

    private var host: EditorHost { model.host }
    private var palette: Palette { Palette.of(colorScheme) }

    /// Canvas at the back, edge to edge; the three glass surfaces over it. The
    /// toolbar spans exactly the gap the two full-height panels leave.
    var body: some View {
        ZStack {
            ComposeCanvas(host: host)

            HStack(alignment: .top, spacing: 0) {
                if sidebarVisible {
                    sidebar.transition(.move(edge: .leading))
                }
                toolbar
                inspector
            }
        }
        .frame(minWidth: 1100, minHeight: 640)
        // The well is painted Kotlin-side, so the appearance has to be pushed in.
        .onAppear { host.setDarkChrome(dark: colorScheme == .dark) }
        .onChange(of: colorScheme) { _, scheme in host.setDarkChrome(dark: scheme == .dark) }
    }

    // MARK: Sidebar

    private var sidebar: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                // The traffic lights live here, drawn by the window itself.
                Spacer(minLength: 80)
                sidebarToggle
            }
            .padding(.leading, 18)
            .padding(.trailing, 14)
            .frame(height: Layout.header)

            navigator
        }
        .frame(width: Layout.sidebar)
        .frame(maxHeight: .infinity)
        .glass(.sidebar, edge: .trailing, palette: palette)
    }

    private var sidebarToggle: some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) { sidebarVisible.toggle() }
        } label: {
            Image(systemName: "sidebar.left")
                .font(.system(size: 15, weight: .regular))
                .foregroundStyle(palette.icon)
                .frame(width: 26, height: 24)
        }
        .buttonStyle(.plain)
        .help(sidebarVisible ? "Hide sidebar" : "Show sidebar")
    }

    private var navigator: some View {
        // Reading generation is what subscribes the rows to store changes.
        let _ = model.generation
        let selected = host.selectedSlideIndex()
        return ScrollView {
            LazyVStack(alignment: .leading, spacing: 6) {
                ForEach(Array(host.outline().enumerated()), id: \.offset) { _, row in
                    if row.slideIndex < 0 {
                        Text(row.title)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(palette.subtle)
                            .padding(.leading, CGFloat(row.depth) * 14)
                    } else {
                        navigatorRow(row, selected: row.slideIndex == selected)
                    }
                }
            }
            .padding(EdgeInsets(top: 2, leading: 6, bottom: 16, trailing: 10))
        }
        .scrollContentBackground(.hidden)
    }

    private func navigatorRow(_ row: OutlineRow, selected: Bool) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text("\(row.slideIndex + 1)")
                .font(.system(size: 11, design: .monospaced))
                .foregroundStyle(palette.faint)
                .frame(width: 16, alignment: .trailing)
            // Thumbnail rendered by the shared Compose renderer
            if let thumb = host.thumbnail(index: row.slideIndex, width: 140) {
                Image(nsImage: thumb)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: 5))
                    .overlay(
                        RoundedRectangle(cornerRadius: 5)
                            .stroke(
                                selected ? palette.accent : Color.gray.opacity(0.4),
                                lineWidth: selected ? 2 : 1
                            )
                    )
            } else {
                Text(row.title)
                    .font(.system(size: 13))
                    .foregroundStyle(palette.text)
            }
        }
        .padding(.leading, CGFloat(row.depth) * 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
        .onTapGesture { host.selectSlide(index: row.slideIndex) }
    }

    // MARK: Toolbar

    private var toolbar: some View {
        HStack(spacing: 14) {
            if !sidebarVisible { sidebarToggle }
            documentName
            Spacer(minLength: 8)
            toolbarCluster
            Spacer(minLength: 8)
            zoomPill
        }
        .padding(.leading, sidebarVisible ? 18 : Layout.trafficLights)
        .padding(.trailing, 14)
        .frame(maxWidth: .infinity)
        .frame(height: Layout.header)
        .glass(.headerView, edge: .bottom, palette: palette)
    }

    private var documentName: some View {
        HStack(spacing: 5) {
            Text("Untitled")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(palette.title)
            Image(systemName: "chevron.down")
                .font(.system(size: 8, weight: .semibold))
                .foregroundStyle(palette.faint)
        }
        .fixedSize()
    }

    private var toolbarCluster: some View {
        HStack(spacing: 10) {
            Button { startPlay() } label: {
                pill { Image(systemName: "play.fill").font(.system(size: 12)) }
            }
            .buttonStyle(.plain)
            .keyboardShortcut(.return, modifiers: .command)
            .help("Play from the selected slide")

            placeholderPill("plus.rectangle", help: "Add slide")

            // Insert group: recessed and accent-tinted, per the mock.
            recessedGroup(tint: palette.accentSoft, symbols: [
                ("rectangle.badge.plus", "Add slide from layout"),
                ("rectangle.split.1x2", "Add section"),
                ("character.textbox", "Add text slide"),
                ("photo.badge.plus", "Add image slide"),
                ("square.grid.3x3.fill", "Light table"),
            ])

            recessedGroup(tint: palette.icon, symbols: [
                ("tablecells", "Table"),
                ("chart.pie", "Chart"),
                ("textformat", "Text"),
                ("square.on.circle", "Shape"),
                ("paperclip", "Attach"),
            ])

            placeholderPill("bubble.left", help: "Comment")
        }
        .fixedSize()
    }

    private func pill<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        content()
            .foregroundStyle(palette.icon)
            .frame(width: 36, height: 30)
            .background(palette.ctrl, in: Capsule())
    }

    private func placeholderPill(_ symbol: String, help: String) -> some View {
        Button {} label: {
            pill { Image(systemName: symbol).font(.system(size: 13)) }
        }
        .buttonStyle(.plain)
        .disabled(true)
        .opacity(0.45)
        .help(help)
    }

    private func recessedGroup(tint: Color, symbols: [(String, String)]) -> some View {
        HStack(spacing: 2) {
            ForEach(symbols, id: \.0) { symbol, help in
                Button {} label: {
                    Image(systemName: symbol)
                        .font(.system(size: 13))
                        .foregroundStyle(tint)
                        .frame(width: 30, height: 24)
                        .contentShape(RoundedRectangle(cornerRadius: 7))
                }
                .buttonStyle(.plain)
                .disabled(true)
                .help(help)
            }
        }
        .padding(3)
        .background(palette.segBg, in: RoundedRectangle(cornerRadius: 10))
        .opacity(0.45)
    }

    // MARK: Zoom

    private static let zoomSteps = [25, 50, 75, 100, 125, 150, 200]

    private var zoomPill: some View {
        Menu {
            zoomOption("Fit in Window", percent: 0)
            Divider()
            ForEach(Self.zoomSteps, id: \.self) { zoomOption("\($0)%", percent: $0) }
        } label: {
            HStack(spacing: 5) {
                Text(zoomPercent == 0 ? "Fit" : "\(zoomPercent)%")
                    .font(.system(size: 12.5))
                    .foregroundStyle(palette.ctrlText)
                Image(systemName: "chevron.down")
                    .font(.system(size: 8, weight: .semibold))
                    .foregroundStyle(palette.faint)
            }
            .padding(.horizontal, 12)
            .frame(height: 30)
            .background(palette.ctrl, in: Capsule())
        }
        .menuStyle(.borderlessButton)
        .menuIndicator(.hidden)
        .fixedSize()
    }

    private func zoomOption(_ label: String, percent: Int) -> some View {
        Button {
            zoomPercent = percent
            host.setZoomPercent(percent: Int32(percent))
        } label: {
            if zoomPercent == percent {
                Label(label, systemImage: "checkmark")
            } else {
                Text(label)
            }
        }
    }

    // MARK: Inspector

    private var inspector: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Button {} label: {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 14))
                        .foregroundStyle(palette.icon)
                        .frame(width: 28, height: 26)
                }
                .buttonStyle(.plain)
                .disabled(true)
                .opacity(0.45)
                .help("Share")

                Spacer(minLength: 0)
                inspectorTabs
            }
            .padding(.horizontal, 14)
            .frame(height: Layout.header)

            // Panel body lands in a later pass.
            Spacer(minLength: 0)
        }
        .frame(width: Layout.inspector)
        .frame(maxHeight: .infinity)
        .glass(.sidebar, edge: .leading, palette: palette)
    }

    private var inspectorTabs: some View {
        HStack(spacing: 2) {
            inspectorTabButton("paintbrush", tab: .format, help: "Format")
            inspectorTabButton("diamond", tab: .animate, help: "Animate")
        }
        .padding(2)
        .background(palette.segBg, in: RoundedRectangle(cornerRadius: 8))
    }

    private func inspectorTabButton(_ symbol: String, tab: InspectorTab, help: String) -> some View {
        let on = inspectorTab == tab
        return Button { inspectorTab = tab } label: {
            Image(systemName: symbol)
                .font(.system(size: 13))
                .foregroundStyle(on ? palette.segOnText : palette.segOff)
                .frame(width: 30, height: 24)
                .background(on ? palette.segOn : .clear, in: RoundedRectangle(cornerRadius: 6))
        }
        .buttonStyle(.plain)
        .help(help)
    }

    private func startPlay() {
        // Kotlin calls onExit on the main thread, so touching @State is safe.
        playSession = host.startPlay(onExit: { playSession = nil })
    }
}
