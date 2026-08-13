// Design v3 layered window: the Compose canvas is full-bleed edge to edge and
// the navigator card, notes strip, inspector and toolbar float over it. Layer
// order back to front: canvas, speaker notes, panels, toolbar, collapsed chrome.
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
    let dim: Color
    let faint: Color
    let ctrl: Color
    let ctrlText: Color
    let segBg: Color
    let segOn: Color
    let segOnText: Color
    let segOff: Color
    let track: Color
    let panel: Color
    let divider: Color
    let accent: Color
    let accentText: Color
    let accentSoft: Color
    let hover: Color
    let hover2: Color
    /// The glass fill painted over the blur.
    let glassFill: LinearGradient
    /// The raised-button fill, for the one button that is not a capsule.
    let buttonFill: LinearGradient
    let hairline: Color
    let innerHighlight: Color
    /// Active inspector tab: a grey pill, not a raised white one.
    let tabOn: Color
    let tabDivider: Color
    /// Navigator selection capsule and its specular top edge.
    let selection: Color
    let selectionEdge: Color
    /// The hairline around a thumbnail, identical selected or not.
    let thumbEdge: Color

    static let dark = Palette(
        text: Color(rgb: 0xE8E8EA),
        title: Color(rgb: 0xD8D8DC),
        label: Color(rgb: 0xB8B8BE),
        icon: Color(rgb: 0xD0D0D5),
        subtle: Color(rgb: 0x98989F),
        dim: Color(rgb: 0xA0A0A8),
        faint: Color(rgb: 0x6E6E76),
        ctrl: Color(rgb: 0x414147),
        ctrlText: Color(rgb: 0xECECEE),
        segBg: Color(rgb: 0x313136),
        segOn: Color(rgb: 0x5C5C64),
        segOnText: .white,
        segOff: Color(rgb: 0xC8C8CC),
        track: Color(rgb: 0x4A4A50),
        panel: Color(rgb: 0x28282C),
        divider: Color(rgb: 0x3A3A3E),
        accent: Color(rgb: 0x7F52FF),
        accentText: .white,
        accentSoft: Color(rgb: 0xB9A3FF),
        hover: Color.white.opacity(0.07),
        hover2: Color.white.opacity(0.14),
        // rgba(44,44,50,0.56) to rgba(32,32,38,0.48)
        glassFill: LinearGradient(
            colors: [Color(rgb: 0x2C2C32).opacity(0.56), Color(rgb: 0x202026).opacity(0.48)],
            startPoint: .top,
            endPoint: .bottom
        ),
        buttonFill: LinearGradient(
            colors: [Color(rgb: 0x525259), Color(rgb: 0x47474D)],
            startPoint: .top,
            endPoint: .bottom
        ),
        hairline: Color.white.opacity(0.10),
        innerHighlight: Color.white.opacity(0.05),
        tabOn: Color.white.opacity(0.16),
        tabDivider: Color.white.opacity(0.18),
        selection: Color.white.opacity(0.17),
        selectionEdge: Color.white.opacity(0.30),
        thumbEdge: Color.white.opacity(0.16)
    )

    static let light = Palette(
        text: Color(rgb: 0x2A2A2C),
        title: Color(rgb: 0x3A3A3C),
        label: Color(rgb: 0x5C5C5E),
        icon: Color(rgb: 0x4A4A4C),
        subtle: Color(rgb: 0x7A7A7E),
        dim: Color(rgb: 0x6A6A6E),
        faint: Color(rgb: 0x9A9A9E),
        ctrl: .white,
        ctrlText: Color(rgb: 0x2A2A2C),
        segBg: Color(rgb: 0xE1E0DE),
        segOn: .white,
        segOnText: Color(rgb: 0x1D1D1F),
        segOff: Color(rgb: 0x5A5A5C),
        track: Color(rgb: 0xCFCECC),
        panel: Color(rgb: 0xF1F0EE),
        divider: Color(rgb: 0xD8D7D5),
        accent: Color(rgb: 0x7F52FF),
        accentText: .white,
        accentSoft: Color(rgb: 0x6F42E0),
        hover: Color.black.opacity(0.06),
        hover2: Color.black.opacity(0.1),
        // rgba(252,251,249,0.64) to rgba(244,243,241,0.54)
        glassFill: LinearGradient(
            colors: [Color(rgb: 0xFCFBF9).opacity(0.64), Color(rgb: 0xF4F3F1).opacity(0.54)],
            startPoint: .top,
            endPoint: .bottom
        ),
        buttonFill: LinearGradient(
            colors: [.white, Color(rgb: 0xF1F1F1)],
            startPoint: .top,
            endPoint: .bottom
        ),
        hairline: Color.black.opacity(0.10),
        innerHighlight: Color.white.opacity(0.7),
        tabOn: Color.black.opacity(0.10),
        tabDivider: Color.black.opacity(0.14),
        selection: Color.white.opacity(0.68),
        selectionEdge: Color.white.opacity(0.95),
        thumbEdge: Color.black.opacity(0.14)
    )

    static func of(_ scheme: ColorScheme) -> Palette { scheme == .dark ? .dark : .light }
}

private enum Layout {
    static let sidebar: CGFloat = 212
    static let inspector: CGFloat = 282
    static let header: CGFloat = 52
    /// The navigator is a floating card, so it is inset from the window edges.
    static let cardLeading: CGFloat = 10
    static let cardTop: CGFloat = 8
    static let cardBottom: CGFloat = 10
    static let cardRadius: CGFloat = 14
    /// Left edge of the toolbar strip: clear of the card, or of the traffic
    /// lights and the collapsed toggle alone.
    static let toolbarOpen: CGFloat = 232
    static let toolbarClosed: CGFloat = 112
    /// Where the window controls sit inside the header, and how wide the group
    /// runs at the system pitch. The real extent is read off the buttons; this
    /// is only what the layout around them reserves.
    static let trafficLightsInset: CGFloat = 14
    static let trafficLightsWidth: CGFloat = 54
    /// Collapsed, the group sits at 18 and the show-sidebar capsule follows it.
    static let collapsedToggle: CGFloat = inset + trafficLightsWidth + edge
    static let notes: CGFloat = 122
    static let notesLeading: CGFloat = 256
    static let notesTrailing: CGFloat = 314
    static let notesInset: CGFloat = 48
    static let thumbnail: CGFloat = 150
    /// Share and the tab group ride over the inspector glass in a fixed region.
    static let tabRegion: CGFloat = 254
    static let edge: CGFloat = 14
    static let inset: CGFloat = 18
    static let panelPadding: CGFloat = 16
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

private enum GlassEdge { case leading, trailing }

private struct Glass: ViewModifier {
    let material: NSVisualEffectView.Material
    let edge: GlassEdge
    let palette: Palette

    func body(content: Content) -> some View {
        content
            .background(GlassBackdrop(material: material))
            .background(palette.glassFill)
            .overlay(alignment: edge == .leading ? .leading : .trailing) { hairline }
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
        }
    }
}

/// The navigator's surface: the same glass, but as a rounded floating card with
/// a hairline all the way round and a soft shadow onto the canvas.
private struct GlassCard: ViewModifier {
    let palette: Palette

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: Layout.cardRadius, style: .continuous)
    }

    func body(content: Content) -> some View {
        content
            .background(GlassBackdrop(material: .sidebar))
            .background(palette.glassFill)
            .clipShape(shape)
            .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
            .shadow(color: .black.opacity(0.18), radius: 13, x: 0, y: 8)
    }
}

private extension View {
    func glass(_ material: NSVisualEffectView.Material, edge: GlassEdge, palette: Palette) -> some View {
        modifier(Glass(material: material, edge: edge, palette: palette))
    }

    func glassCard(palette: Palette) -> some View {
        modifier(GlassCard(palette: palette))
    }
}

// MARK: - Window

/// The window controls live inside the navigator card, so the real buttons get
/// moved there. Not hidden and redrawn: hover, hit testing and accessibility
/// stay the system's. AppKit relays them out behind our back on resize, on
/// becoming key and on leaving fullscreen, so each of those re-applies.
private final class TrafficLights {
    private weak var window: NSWindow?
    private var sidebarOpen = true
    private var grown = false
    private var observers: [NSObjectProtocol] = []

    func attach(to window: NSWindow) {
        guard self.window !== window else { return }
        self.window = window
        growTitlebar(window)
        let names: [Notification.Name] = [
            NSWindow.didResizeNotification,
            NSWindow.didBecomeKeyNotification,
            NSWindow.didExitFullScreenNotification,
        ]
        for name in names {
            let token = NotificationCenter.default.addObserver(
                forName: name,
                object: window,
                queue: .main
            ) { [weak self] _ in self?.apply() }
            observers.append(token)
        }
        apply()
    }

    deinit {
        for token in observers { NotificationCenter.default.removeObserver(token) }
    }

    /// Hit testing and tracking areas are bounds-based, so the container has to
    /// be genuinely taller: unclipping alone fixed the drawing and left the
    /// dropped half of each button dead to clicks and hover. A zero-width
    /// accessory grows it without putting any chrome in the titlebar.
    private func growTitlebar(_ window: NSWindow) {
        guard !grown else { return }
        grown = true
        let accessory = NSTitlebarAccessoryViewController()
        accessory.view = NSView(frame: NSRect(x: 0, y: 0, width: 0, height: 48))
        accessory.layoutAttribute = .right
        window.addTitlebarAccessoryViewController(accessory)
        // Installing it relays the buttons out, so take the next pass too.
        DispatchQueue.main.async { [weak self] in self?.apply() }
    }

    /// The target follows shared state: the card header when the navigator is
    /// showing, the bare top strip when it is not.
    func move(sidebarOpen: Bool) {
        self.sidebarOpen = sidebarOpen
        apply()
    }

    private func apply() {
        // Fullscreen is the system's to lay out, and a window that has not
        // handed us its buttons yet gets left alone rather than guessed at.
        guard let window, !window.styleMask.contains(.fullScreen) else { return }
        guard
            let close = window.standardWindowButton(.closeButton),
            let miniaturize = window.standardWindowButton(.miniaturizeButton),
            let zoom = window.standardWindowButton(.zoomButton),
            let titlebar = close.superview
        else { return }

        // Belt and braces next to the grown container: a layer-backed titlebar
        // would still mask what hangs past its bounds.
        unclip(titlebar)
        unclip(titlebar.superview)

        // Keep the system pitch rather than assuming 20pt between buttons.
        let offsets = [
            miniaturize.frame.minX - close.frame.minX,
            zoom.frame.minX - close.frame.minX,
        ]
        let leading = sidebarOpen
            ? Layout.cardLeading + Layout.trafficLightsInset
            : Layout.inset
        let centre = sidebarOpen
            ? Layout.cardTop + Layout.header / 2
            : Layout.header / 2

        // Every level between the buttons and the titlebar container has to
        // actually contain them: hit testing and tracking areas stop at each
        // view's bounds, so the accessory growing the container alone still
        // left the dropped half dead wherever an inner view stayed 28pt tall.
        let span = (offsets.last ?? 0) + zoom.frame.width
        let target = NSRect(
            x: leading,
            y: window.frame.height - centre - close.frame.height / 2,
            width: span,
            height: close.frame.height
        )
        // Padded for rollover slop.
        grow(titlebar, toContain: target.insetBy(dx: -6, dy: -6))

        // Measured from the window's top-left, then converted: the titlebar view
        // is not flipped and need not be flush with the top of the window.
        let anchor = titlebar.convert(
            NSPoint(x: leading, y: window.frame.height - centre),
            from: nil
        )
        for (button, dx) in zip([close, miniaturize, zoom], [0] + offsets) {
            button.setFrameOrigin(
                NSPoint(x: anchor.x + dx, y: anchor.y - button.frame.height / 2)
            )
            // Rollover follows the frame only once the tracking area is rebuilt.
            button.updateTrackingAreas()
        }
        titlebar.updateTrackingAreas()
        window.invalidateCursorRects(for: titlebar)
    }

    /// Walks the titlebar chain from [view] up to and including the container,
    /// growing any level that would clip [targetInWindow]. Outermost first,
    /// since growing a view moves everything inside it, and never shrinking:
    /// AppKit relays these out on its own schedule, so this runs every pass.
    /// The theme frame is never touched, hence the name check.
    private func grow(_ view: NSView, toContain targetInWindow: NSRect) {
        var chain: [NSView] = []
        var current: NSView? = view
        while let level = current {
            let name = String(describing: type(of: level))
            // Anything that is not titlebar furniture (the theme frame) is off
            // limits, so the walk stops rather than resizing it.
            guard name.contains("Titlebar") else { break }
            chain.append(level)
            if name.contains("Container") { break }
            current = level.superview
        }
        for level in chain.reversed() {
            guard let parent = level.superview else { continue }
            let needed = parent.convert(targetInWindow, from: nil)
            guard !level.frame.contains(needed) else { continue }
            level.frame = level.frame.union(needed)
        }
    }

    private func unclip(_ view: NSView?) {
        guard let view else { return }
        view.clipsToBounds = false
        view.layer?.masksToBounds = false
    }
}

/// There is no title bar: the traffic lights sit in the sidebar header instead,
/// so the content view has to run the full height of the window.
private struct WindowConfigurator: NSViewRepresentable {
    let lights: TrafficLights

    func makeNSView(context: Context) -> NSView {
        let view = NSView(frame: .zero)
        DispatchQueue.main.async {
            guard let window = view.window else { return }
            window.titlebarAppearsTransparent = true
            window.titleVisibility = .hidden
            window.styleMask.insert(.fullSizeContentView)
            lights.attach(to: window)
        }
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {}
}

/// Pushes the current target at the buttons. Lives in the editor view so it
/// re-runs whenever the collected `sidebarOpen` changes.
private struct TrafficLightTarget: NSViewRepresentable {
    let lights: TrafficLights
    let sidebarOpen: Bool

    func makeNSView(context: Context) -> NSView {
        let view = NSView(frame: .zero)
        DispatchQueue.main.async { lights.move(sidebarOpen: sidebarOpen) }
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {
        lights.move(sidebarOpen: sidebarOpen)
    }
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
    /// Shared so the window setup and the editor view drive the same buttons.
    @ObservationIgnored fileprivate let lights = TrafficLights()
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
            .background(WindowConfigurator(lights: model.lights).frame(width: 0, height: 0))
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
            CommandGroup(after: .sidebar) {
                let _ = model.generation
                Toggle("Show Speaker Notes", isOn: Binding(
                    get: { host.showNotes() },
                    set: { _ in host.toggleNotes() }
                ))
            }
        }
    }
}

// MARK: - Editor

private struct EditorView: View {
    let model: EditorModel
    @Binding var playSession: PlaySession?

    @Environment(\.colorScheme) private var colorScheme
    /// Zoom is view-local in the Kotlin host, outside `states`, so the label
    /// reads from this mirror rather than waiting on a generation bump.
    @State private var zoomPercent: Int = 0

    private var host: EditorHost { model.host }
    private var palette: Palette { Palette.of(colorScheme) }

    /// One read of the shared chrome state per body pass. Everything a click
    /// shows comes back through here, never from a local copy.
    private struct Chrome {
        let sidebarOpen: Bool
        let inspectorOpen: Bool
        let tab: InspectorTab
        let showNotes: Bool
        let notes: String
    }

    /// Touching `generation` is what subscribes this view to store changes.
    private var chrome: Chrome {
        let _ = model.generation
        return Chrome(
            sidebarOpen: host.sidebarOpen(),
            inspectorOpen: host.inspectorOpen(),
            tab: host.inspectorTab(),
            showNotes: host.showNotes(),
            notes: host.slideNotes()
        )
    }

    /// Canvas at the back, edge to edge; the notes strip over it; the two panels
    /// over that (the notes pass visibly behind the navigator card); the toolbar
    /// over the panels, so its right-hand controls float on the inspector glass.
    var body: some View {
        let ui = chrome
        return ZStack {
            ComposeCanvas(host: host)

            if ui.showNotes {
                notesStrip(ui)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .transition(.move(edge: .bottom))
            }

            panels(ui)

            toolbar(ui)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)

            if !ui.sidebarOpen {
                collapsedChrome
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
        .frame(minWidth: 1100, minHeight: 640)
        .background {
            TrafficLightTarget(lights: model.lights, sidebarOpen: ui.sidebarOpen)
                .frame(width: 0, height: 0)
        }
        .animation(.easeInOut(duration: 0.2), value: ui.sidebarOpen)
        .animation(.easeInOut(duration: 0.2), value: ui.inspectorOpen)
        .animation(.easeInOut(duration: 0.2), value: ui.showNotes)
        // The well is painted Kotlin-side, so the appearance has to be pushed in.
        .onAppear { host.setDarkChrome(dark: colorScheme == .dark) }
        .onChange(of: colorScheme) { _, scheme in host.setDarkChrome(dark: scheme == .dark) }
    }

    private func panels(_ ui: Chrome) -> some View {
        HStack(spacing: 0) {
            if ui.sidebarOpen {
                navigatorCard
                    .frame(width: Layout.sidebar)
                    .padding(.leading, Layout.cardLeading)
                    .padding(.top, Layout.cardTop)
                    .padding(.bottom, Layout.cardBottom)
                    .transition(.move(edge: .leading))
            }
            Spacer(minLength: 0)
            if ui.inspectorOpen {
                inspector(ui).transition(.move(edge: .trailing))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: Navigator

    private var navigatorCard: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                // The window's own buttons sit here, moved into place by
                // TrafficLights; this reserves the room they take.
                Spacer(minLength: Layout.trafficLightsInset + Layout.trafficLightsWidth)
                sidebarToggle
            }
            .padding(.trailing, Layout.edge)
            .frame(height: Layout.header)

            navigator
        }
        .frame(maxHeight: .infinity)
        .glassCard(palette: palette)
    }

    private var sidebarToggle: some View {
        Button { host.toggleSidebar() } label: {
            Image(systemName: "sidebar.left")
                .font(.system(size: 15, weight: .regular))
                .foregroundStyle(palette.icon)
                .frame(width: 26, height: 24)
                .contentShape(RoundedRectangle(cornerRadius: 6))
        }
        .buttonStyle(.plain)
        .help("Hide sidebar")
    }

    private var navigator: some View {
        // Reading generation is what subscribes the rows to store changes.
        let _ = model.generation
        let selected = host.selectedSlideIndex()
        let rows = host.outline()
        return ScrollView {
            // Rows are identified by their absolute slide index, so collapsing a
            // group reads as those rows leaving and everything after sliding up,
            // not as every row changing in place.
            LazyVStack(alignment: .leading, spacing: 2) {
                ForEach(rows, id: \.slideIndex) { row in
                    NavigatorRow(
                        row: row,
                        selected: row.slideIndex == selected,
                        palette: palette,
                        host: host
                    )
                    .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
            .padding(EdgeInsets(top: 2, leading: 8, bottom: 16, trailing: 8))
            .frame(maxWidth: .infinity, alignment: .leading)
            .animation(.easeInOut(duration: 0.14), value: rows.map(\.slideIndex))
        }
        .scrollContentBackground(.hidden)
    }

    /// Keynote's row: a fixed leading gutter, then the thumbnail, both inside a
    /// selection capsule that hugs them. The gutter runs the thumbnail's height
    /// and carries the disclosure chevron centred in it plus the slide number
    /// tucked to its bottom, so every thumbnail starts at the same offset from
    /// its own row whether or not the slide has children. Depth indents the
    /// whole capsule and takes the same step off the thumbnail's width.
    private struct NavigatorRow: View {
        let row: OutlineRow
        let selected: Bool
        let palette: Palette
        let host: EditorHost

        @State private var hovering = false
        @State private var chevronHovering = false

        private var thumbWidth: CGFloat { Layout.thumbnail - 12 * CGFloat(min(row.depth, 3)) }

        var body: some View {
            HStack(spacing: 4) {
                gutter
                thumbnail
            }
            .padding(.vertical, 5)
            .padding(.horizontal, 6)
            .background { capsule }
            .contentShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .onTapGesture { host.selectSlide(index: row.slideIndex) }
            .onHover { hovering = $0 }
            .padding(.leading, CGFloat(row.depth) * 12)
            .frame(maxWidth: .infinity, alignment: .leading)
        }

        /// Sized off the thumbnail, not the row, so the number sits on the
        /// thumbnail's lower edge and the chevron on its middle.
        private var gutter: some View {
            ZStack {
                if row.hasChildren { chevron }
            }
            .frame(width: 15, height: thumbWidth * 9 / 16)
            .overlay(alignment: .bottomTrailing) {
                Text("\(row.slideIndex + 1)")
                    .font(.system(size: 11))
                    .foregroundStyle(palette.faint)
                    .padding(.bottom, 1)
            }
        }

        private var chevron: some View {
            Button { host.toggleCollapsed(index: row.slideIndex) } label: {
                ChevronGlyph()
                    .stroke(
                        palette.icon,
                        style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
                    )
                    .frame(width: 9, height: 9)
                    .rotationEffect(.degrees(row.collapsed ? 0 : 90))
                    .animation(.easeInOut(duration: 0.14), value: row.collapsed)
                    .frame(width: 16, height: 20)
                    .background {
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .fill(chevronHovering ? palette.hover2 : .clear)
                    }
                    .contentShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
            }
            .buttonStyle(.plain)
            .onHover { chevronHovering = $0 }
            .help(row.collapsed ? "Expand" : "Collapse")
        }

        @ViewBuilder private var capsule: some View {
            let shape = RoundedRectangle(cornerRadius: 10, style: .continuous)
            if selected {
                shape
                    .fill(palette.selection)
                    .overlay {
                        shape
                            .inset(by: 0.5)
                            .stroke(palette.selectionEdge, lineWidth: 1)
                            .mask(
                                LinearGradient(
                                    colors: [.white, .clear],
                                    startPoint: .top,
                                    endPoint: .center
                                )
                            )
                    }
            } else if hovering {
                shape.fill(palette.hover)
            }
        }

        /// Rendered by the shared Compose renderer, so a thumbnail is the slide.
        /// No accent ring and no shadow: the capsule alone marks selection.
        @ViewBuilder private var thumbnail: some View {
            let shape = RoundedRectangle(cornerRadius: 4, style: .continuous)
            if let image = host.thumbnail(index: row.slideIndex, width: Int32(thumbWidth)) {
                Image(nsImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: thumbWidth)
                    .clipShape(shape)
                    .overlay { shape.inset(by: 0.5).stroke(palette.thumbEdge, lineWidth: 1) }
            } else {
                shape
                    .fill(Color.black.opacity(0.2))
                    .frame(width: thumbWidth, height: thumbWidth * 9 / 16)
            }
        }
    }

    /// The disclosure glyph: a stroked chevron on a 9x9 box, pointing right.
    /// A path, not a text character, so it reads as a control at any size.
    private struct ChevronGlyph: Shape {
        func path(in rect: CGRect) -> Path {
            let unit = min(rect.width, rect.height) / 9
            var path = Path()
            path.move(to: CGPoint(x: rect.minX + 2.6 * unit, y: rect.minY + 1.1 * unit))
            path.addLine(to: CGPoint(x: rect.minX + 6.4 * unit, y: rect.minY + 4.5 * unit))
            path.addLine(to: CGPoint(x: rect.minX + 2.6 * unit, y: rect.minY + 7.9 * unit))
            return path
        }
    }

    // MARK: Collapsed chrome

    private var collapsedChrome: some View {
        Button { host.toggleSidebar() } label: {
            pill { Image(systemName: "sidebar.left").font(.system(size: 15)) }
        }
        .buttonStyle(.plain)
        .help("Show sidebar")
        .padding(.leading, Layout.collapsedToggle)
        .frame(height: Layout.header)
    }

    // MARK: Toolbar

    /// No bar of its own: no fill, no blur, no hairline. Only the capsules are
    /// opaque, and they float straight over the canvas and the inspector glass.
    private func toolbar(_ ui: Chrome) -> some View {
        HStack(spacing: Layout.edge) {
            documentName
            Spacer(minLength: 8)
            toolbarCluster
            Spacer(minLength: 8)
            zoomPill
            if ui.inspectorOpen {
                // Fixed 254pt region over the inspector, laid out space-between:
                // Share 14 inside the panel's left edge, tabs 14 from the window.
                HStack(spacing: Layout.edge) {
                    shareButton
                    Spacer(minLength: 0)
                    tabGroup(ui)
                }
                .frame(width: Layout.tabRegion)
                .padding(.leading, Layout.edge)
            } else {
                shareButton
                tabGroup(ui)
            }
        }
        .padding(.leading, (ui.sidebarOpen ? Layout.toolbarOpen : Layout.toolbarClosed) + Layout.inset)
        .padding(.trailing, Layout.edge)
        .frame(height: Layout.header)
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

            insertCapsule

            placeholderPill("bubble.left", help: "Comment")
        }
        .fixedSize()
    }

    private var shareButton: some View {
        placeholderPill("square.and.arrow.up", help: "Share")
    }

    private func pill<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        content()
            .foregroundStyle(palette.icon)
            .frame(width: 36, height: 30)
            .background(palette.ctrl, in: Capsule())
    }

    private func placeholderPill(_ symbol: String, help: String) -> some View {
        Button {} label: {
            pill { Image(systemName: symbol).font(.system(size: 13)).opacity(0.45) }
        }
        .buttonStyle(.plain)
        .disabled(true)
        .help(help)
    }

    /// The one insert cluster: raised capsule, 30x24 items. Placeholders until
    /// the element library lands.
    private var insertCapsule: some View {
        HStack(spacing: 2) {
            let items = [
                ("tablecells", "Table"),
                ("chart.pie", "Chart"),
                ("textformat", "Text"),
                ("square.on.circle", "Shape"),
                ("paperclip", "Media"),
            ]
            ForEach(items, id: \.0) { symbol, help in
                Button {} label: {
                    Image(systemName: symbol)
                        .font(.system(size: 13))
                        .foregroundStyle(palette.icon.opacity(0.45))
                        .frame(width: 30, height: 24)
                        .contentShape(RoundedRectangle(cornerRadius: 7))
                }
                .buttonStyle(.plain)
                .disabled(true)
                .help(help)
            }
        }
        .padding(3)
        .background(palette.ctrl, in: Capsule())
    }

    // MARK: Inspector tabs

    /// Three tabs in one raised capsule. The active one is a grey pill; all
    /// three icons keep the same ink, and the divider next to the active tab
    /// hides so the pill reads as one shape.
    private func tabGroup(_ ui: Chrome) -> some View {
        HStack(spacing: 0) {
            tabButton(InspectorTab.format, symbol: "paintbrush", help: "Format", ui: ui)
            tabDivider(InspectorTab.format, InspectorTab.animate, ui: ui)
            tabButton(InspectorTab.animate, symbol: "diamond", help: "Animate", ui: ui)
            tabDivider(InspectorTab.animate, InspectorTab.document, ui: ui)
            tabButton(InspectorTab.document, symbol: "rectangle.fill", help: "Document", ui: ui)
        }
        .padding(2)
        .background(palette.ctrl, in: Capsule())
    }

    private func tabButton(_ tab: InspectorTab, symbol: String, help: String, ui: Chrome) -> some View {
        let on = ui.inspectorOpen && ui.tab == tab
        return Button {
            // Clicking the tab you are already on closes the inspector.
            if on { host.closeInspector() } else { host.selectInspectorTab(tab: tab) }
        } label: {
            Image(systemName: symbol)
                .font(.system(size: 13))
                .foregroundStyle(palette.ctrlText)
                .frame(width: 34, height: 26)
                .background(
                    on ? palette.tabOn : .clear,
                    in: RoundedRectangle(cornerRadius: 13, style: .continuous)
                )
                .contentShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
        }
        .buttonStyle(.plain)
        .help(help)
    }

    private func tabDivider(_ before: InspectorTab, _ after: InspectorTab, ui: Chrome) -> some View {
        let active = ui.inspectorOpen ? ui.tab : nil
        let hidden = active == before || active == after
        return (hidden ? Color.clear : palette.tabDivider).frame(width: 1, height: 16)
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

    /// Header is bare: Share and the tabs moved to the toolbar, which floats
    /// over this glass, so the panel only owns the title under them.
    private func inspector(_ ui: Chrome) -> some View {
        VStack(spacing: 0) {
            Color.clear.frame(height: Layout.header)

            Text(inspectorTitle(ui.tab))
                .font(.system(size: 13))
                .foregroundStyle(palette.subtle)
                .frame(maxWidth: .infinity)
                .padding(.top, 4)
                .padding(.horizontal, Layout.panelPadding)
                .padding(.bottom, 12)

            if ui.tab == InspectorTab.document {
                documentPanel
            } else {
                // Format and Animate bodies land in a later pass.
                Spacer(minLength: 0)
            }
        }
        .frame(width: Layout.inspector)
        .frame(maxHeight: .infinity)
        .glass(.sidebar, edge: .leading, palette: palette)
    }

    private func inspectorTitle(_ tab: InspectorTab) -> String {
        if tab == InspectorTab.animate { return "Build" }
        if tab == InspectorTab.document { return "Slide" }
        return "Text"
    }

    // MARK: Document panel

    /// Static for now: the slide's layout, appearance and background are not in
    /// the document model yet, so every control here is inert.
    private var documentPanel: some View {
        VStack(alignment: .leading, spacing: Layout.panelPadding) {
            slideLayoutCard
            appearanceSection
            palette.divider.frame(height: 1)
            backgroundSection
            Spacer(minLength: 0)
            editLayoutButton
        }
        .padding(Layout.panelPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    private var slideLayoutCard: some View {
        HStack(spacing: 12) {
            layoutPreview
            VStack(alignment: .leading, spacing: 0) {
                Text("Slide Layout")
                    .font(.system(size: 11))
                    .foregroundStyle(palette.subtle)
                Text("Title")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(palette.text)
            }
            Spacer(minLength: 0)
            Text("\u{2304}")
                .font(.system(size: 10))
                .foregroundStyle(palette.subtle)
        }
        .padding(10)
        .background(palette.ctrl, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
    }

    /// A slide the way a layout picker draws one: white paper, three grey bars.
    private var layoutPreview: some View {
        let shape = RoundedRectangle(cornerRadius: 3, style: .continuous)
        return VStack(alignment: .leading, spacing: 0) {
            previewBar(width: 34, height: 4, color: Color(rgb: 0x2A2630))
            previewBar(width: 23, height: 3, color: Color(rgb: 0x9A958D))
                .padding(.top, 3)
            previewBar(width: 17, height: 2.5, color: Color(rgb: 0xC9C4BD))
                .padding(.top, 10)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 5)
        .frame(width: 62, height: 35, alignment: .topLeading)
        .background(Color.white, in: shape)
        .overlay { shape.inset(by: 0.5).stroke(Color.black.opacity(0.12), lineWidth: 1) }
    }

    private func previewBar(width: CGFloat, height: CGFloat, color: Color) -> some View {
        RoundedRectangle(cornerRadius: 1).fill(color).frame(width: width, height: height)
    }

    private var appearanceSection: some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Appearance")
            checkRow("Title", on: true)
            checkRow("Body", on: true)
            checkRow("Slide Number", on: false)
        }
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(palette.subtle)
    }

    private func checkRow(_ label: String, on: Bool) -> some View {
        HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(on ? palette.accent : palette.track)
                .frame(width: 15, height: 15)
                .overlay {
                    if on {
                        Image(systemName: "checkmark")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(palette.accentText)
                    }
                }
            Text(label)
                .font(.system(size: 13))
                .foregroundStyle(palette.text)
        }
    }

    private var backgroundSection: some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Background")

            HStack(spacing: 2) {
                segment("Standard", on: true)
                segment("Dynamic", on: false)
            }
            .padding(2)
            .frame(height: 26)
            .background(palette.segBg, in: RoundedRectangle(cornerRadius: 6, style: .continuous))

            HStack(spacing: 0) {
                Text("Colour Fill")
                    .font(.system(size: 12.5))
                    .foregroundStyle(palette.ctrlText)
                Spacer(minLength: 0)
                Text("\u{2304}")
                    .font(.system(size: 9))
                    .foregroundStyle(palette.subtle)
            }
            .padding(.horizontal, 11)
            .frame(height: 24)
            .background(palette.ctrl, in: RoundedRectangle(cornerRadius: 5, style: .continuous))

            HStack(spacing: 8) {
                RoundedRectangle(cornerRadius: 5, style: .continuous)
                    .fill(Color.white)
                    .frame(height: 24)
                    .overlay {
                        RoundedRectangle(cornerRadius: 5, style: .continuous)
                            .inset(by: 0.5)
                            .stroke(Color.black.opacity(0.14), lineWidth: 1)
                    }
                colourWheel
            }
        }
    }

    private func segment(_ label: String, on: Bool) -> some View {
        Text(label)
            .font(.system(size: 12, weight: on ? .semibold : .regular))
            .foregroundStyle(on ? palette.accentText : palette.subtle)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(
                on ? AnyShapeStyle(palette.accent) : AnyShapeStyle(Color.clear),
                in: RoundedRectangle(cornerRadius: 5, style: .continuous)
            )
    }

    private var colourWheel: some View {
        Circle()
            .fill(
                AngularGradient(
                    colors: [
                        Color(rgb: 0xF0357B), Color(rgb: 0xFFC24B), Color(rgb: 0x43C57E),
                        Color(rgb: 0x3FA9F5), Color(rgb: 0x7F52FF), Color(rgb: 0xF0357B),
                    ],
                    center: .center
                )
            )
            .frame(width: 22, height: 22)
            .overlay { Circle().inset(by: 0.5).stroke(Color.black.opacity(0.14), lineWidth: 1) }
    }

    private var editLayoutButton: some View {
        Text("Edit Slide Layout")
            .font(.system(size: 12.5))
            .foregroundStyle(palette.ctrlText)
            .frame(maxWidth: .infinity)
            .frame(height: 26)
            .background(palette.buttonFill, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
    }

    // MARK: Speaker notes

    /// Full window width and pinned to the bottom, so it passes behind the
    /// navigator card. Its text insets clear whichever panel is open.
    private func notesStrip(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("SPEAKER NOTES")
                .font(.system(size: 10.5, weight: .bold))
                .tracking(1.2)
                .foregroundStyle(palette.faint)
            Text(ui.notes)
                .font(.system(size: 13.5))
                .foregroundStyle(palette.dim)
                .lineSpacing(6.75)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(.top, 18)
        .padding(.bottom, 20)
        .padding(.leading, ui.sidebarOpen ? Layout.notesLeading : Layout.notesInset)
        .padding(.trailing, ui.inspectorOpen ? Layout.notesTrailing : Layout.notesInset)
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(height: Layout.notes)
        .background(palette.panel)
        .overlay(alignment: .top) { palette.divider.frame(height: 1) }
    }

    private func startPlay() {
        // Kotlin calls onExit on the main thread, so touching @State is safe.
        playSession = host.startPlay(onExit: { playSession = nil })
    }
}
