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
    /// Packed ARGB, the document model's colour format: what the background
    /// swatches carry, alpha included.
    init(argb: Int64) {
        self.init(
            .sRGB,
            red: Double((argb >> 16) & 0xFF) / 255,
            green: Double((argb >> 8) & 0xFF) / 255,
            blue: Double(argb & 0xFF) / 255,
            opacity: Double((argb >> 24) & 0xFF) / 255
        )
    }

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
        // A right-click on the canvas. The event is already away by the time
        // this fires, so the menu is all that is left to do.
        host.setContextClickCallback { [weak self] elementId in
            guard let self else { return }
            popCanvasMenu(host: self.host, elementId: elementId)
        }
    }

    deinit {
        unsubscribe?()
    }
}

// MARK: - Shared state snapshot

/// One read of the shared chrome state, taken per pass by whoever needs it.
/// Everything a click shows comes back through here, never from a local copy.
/// A value, so the window and the menus read the same editor the same way.
private struct Chrome {
    let sidebarOpen: Bool
    let inspectorOpen: Bool
    let tab: InspectorTab
    let showNotes: Bool
    let notes: String
    /// The primary of the selection, nil when nothing is selected.
    let element: Selection?
    let selectionCount: Int
    let canGroup: Bool
    let canUngroup: Bool
    /// The selected slide's own properties, what the Document panel edits.
    let slide: SlideProps

    init(_ host: EditorHost) {
        sidebarOpen = host.sidebarOpen()
        inspectorOpen = host.inspectorOpen()
        tab = host.inspectorTab()
        showNotes = host.showNotes()
        notes = host.slideNotes()
        element = host.selectedElement().map(Selection.init)
        selectionCount = Int(host.selectionCount())
        canGroup = host.canGroup()
        canUngroup = host.canUngroup()
        slide = SlideProps(host)
    }

    /// Everything but the unlock needs something unlocked, the same rule the
    /// presenter applies: a live item is never a silently dropped event.
    var editable: Bool { element.map { !$0.locked } ?? false }
}

/// The selected slide's own properties as a Swift value: what the Document
/// panel shows, read the same way the element properties are.
private struct SlideProps: Equatable {
    /// Whether the slide draws its place in the presentation on itself.
    let numberVisible: Bool
    /// 0 the deck's own background, 1 a flat colour, 2 a gradient.
    let backgroundKind: Int
    /// Packed ARGB. When the slide wears another kind these are what switching
    /// to this one would commit, so the controls never have to invent a value.
    let color: Int64
    let gradientStart: Int64
    let gradientEnd: Int64

    init(_ host: EditorHost) {
        numberVisible = host.slideNumberVisible()
        backgroundKind = Int(host.backgroundKind())
        color = host.backgroundColor()
        gradientStart = host.backgroundGradientStart()
        gradientEnd = host.backgroundGradientEnd()
    }
}

/// The primary selected element's properties as a Swift value. Kotlin hands back
/// a fresh object on every read, so a struct is what lets the inspector's fields
/// tell a real change from another pass over the same numbers.
private struct Selection: Equatable {
    let x: Double
    let y: Double
    let width: Double
    let height: Double
    let opacity: Double
    let rotation: Double
    let flippedHorizontally: Bool
    let flippedVertically: Bool
    let locked: Bool
    let kind: String

    init(_ props: ElementProps) {
        x = Double(props.x)
        y = Double(props.y)
        width = Double(props.width)
        height = Double(props.height)
        opacity = Double(props.opacity)
        rotation = Double(props.rotation)
        flippedHorizontally = props.flippedHorizontally
        flippedVertically = props.flippedVertically
        locked = props.locked
        kind = props.kind
    }
}

// MARK: - Menus

/// One row of a menu: a command, a submenu, or a separator. The Arrange items
/// are built once as these and rendered twice, as SwiftUI Buttons in the menu
/// bar and as NSMenuItems in the canvas menu, so the two can only differ in the
/// facts they were built from.
private struct MenuEntry: Identifiable {
    let id = UUID()
    /// Empty is a separator: nothing else in a menu has no title.
    let title: String
    var enabled = true
    /// Menu bar only. A context menu advertising shortcuts would be repeating
    /// what the bar above it already says.
    var shortcut: KeyboardShortcut? = nil
    var children: [MenuEntry]? = nil
    var action: (() -> Void)? = nil

    var isSeparator: Bool { title.isEmpty }

    static func separator() -> MenuEntry { MenuEntry(title: "") }
}

/// What the Arrange items grey themselves out by: either the live selection, or
/// the effective selection of the right-click opening a menu right now. One
/// shape, so one builder serves both.
private struct ArrangeFacts {
    /// Z-order, flip and align: one unlocked element is enough for all three.
    let canArrange: Bool
    let canGroup: Bool
    let canUngroup: Bool
    let canDistribute: Bool
    let canLock: Bool
    let lockLabel: String

    init(_ ui: Chrome) {
        canArrange = ui.editable
        canGroup = ui.canGroup
        canUngroup = ui.canUngroup
        // Two elements have no gap between them to equalize.
        canDistribute = ui.editable && ui.selectionCount >= 3
        canLock = ui.element != nil
        lockLabel = ui.element?.locked == true ? "Unlock" : "Lock"
    }

    /// Kotlin's, computed against the selection a right-click settles on rather
    /// than the one in `states`, which that click has not reached yet.
    init(_ facts: ContextFacts) {
        canArrange = facts.canArrange
        canGroup = facts.canGroup
        canUngroup = facts.canUngroup
        canDistribute = facts.canDistribute
        canLock = facts.canLock
        lockLabel = facts.lockLabel
    }
}

/// The Arrange menu, stated once. The labels follow the primary element, the
/// actions carry the whole selection: the host's setters batch, so one pick is
/// one undo entry however many elements it moved.
private func arrangeEntries(_ facts: ArrangeFacts, _ host: EditorHost) -> [MenuEntry] {
    func command(
        _ title: String,
        _ enabled: Bool,
        shortcut: KeyboardShortcut? = nil,
        _ action: @escaping () -> Void
    ) -> MenuEntry {
        MenuEntry(title: title, enabled: enabled, shortcut: shortcut, action: action)
    }

    func reorder(_ title: String, _ move: ZOrderMove) -> MenuEntry {
        command(title, facts.canArrange) { host.reorderSelectedElement(move: move) }
    }

    return [
        reorder("Bring Forward", ZOrderMove.forward),
        reorder("Send Backward", ZOrderMove.backward),
        reorder("Bring to Front", ZOrderMove.tofront),
        reorder("Send to Back", ZOrderMove.toback),

        .separator(),

        command("Flip Horizontally", facts.canArrange) {
            host.flipSelectedElement(axis: FlipAxis.horizontal)
        },
        command("Flip Vertically", facts.canArrange) {
            host.flipSelectedElement(axis: FlipAxis.vertical)
        },

        .separator(),

        command(
            "Group",
            facts.canGroup,
            shortcut: KeyboardShortcut("g", modifiers: [.command, .option])
        ) { host.groupSelection() },
        command(
            "Ungroup",
            facts.canUngroup,
            shortcut: KeyboardShortcut("g", modifiers: [.command, .option, .shift])
        ) { host.ungroupSelection() },

        .separator(),

        // A lone element aligns to the slide, so one is enough.
        MenuEntry(
            title: "Align Objects",
            enabled: facts.canArrange,
            children: [
                command("Left", true) { host.alignSelection(edge: AlignEdge.left) },
                command("Center", true) { host.alignSelection(edge: AlignEdge.centerx) },
                command("Right", true) { host.alignSelection(edge: AlignEdge.right) },
                command("Top", true) { host.alignSelection(edge: AlignEdge.top) },
                command("Middle", true) { host.alignSelection(edge: AlignEdge.centery) },
                command("Bottom", true) { host.alignSelection(edge: AlignEdge.bottom) },
            ]
        ),
        MenuEntry(
            title: "Distribute Objects",
            enabled: facts.canDistribute,
            children: [
                command("Horizontally", true) { host.distributeSelection(axis: Axis.horizontal) },
                command("Vertically", true) { host.distributeSelection(axis: Axis.vertical) },
            ]
        ),

        .separator(),

        command(facts.lockLabel, facts.canLock) { host.toggleSelectedElementLock() },
    ]
}

/// The slide verbs, stated once. [slideId] nil is the Slide menu, which has no
/// row to point at and drives the selected-slide methods instead; a navigator
/// row passes its own id, so the verb acts on that row whatever is selected.
/// [includePaste] is the context menu's: in the bar, Edit > Paste owns it.
private func slideEntries(
    _ host: EditorHost,
    slideId: String?,
    includePaste: Bool
) -> [MenuEntry] {
    // Everything but Paste is always live: the core keeps the document
    // non-empty, so cutting or deleting the last slide leaves a blank one.
    func command(
        _ title: String,
        _ byId: @escaping (String) -> Void,
        _ bySelection: @escaping () -> Void
    ) -> MenuEntry {
        MenuEntry(title: title, action: {
            if let slideId { byId(slideId) } else { bySelection() }
        })
    }

    let skipped = slideId.map { host.isSlideSkipped(id: $0) } ?? host.isSelectedSlideSkipped()

    // doCopySlide is the exporter's doing: copy is a reserved ObjC method
    // family, so the host's copySlide arrives here renamed, the way every other
    // copyX on it does.
    var entries: [MenuEntry] = [
        command("New Slide", host.addSlideAfter(id:), host.addSlideAfterSelection),
        command("Duplicate Slide", host.duplicateSlide(id:), host.duplicateSelectedSlide),

        .separator(),

        command("Cut Slide", host.cutSlide(id:), host.cutSelectedSlide),
        command("Copy Slide", host.doCopySlide(id:), host.doCopySelectedSlide),
    ]

    if includePaste {
        entries.append(
            MenuEntry(title: "Paste", enabled: host.canPaste(), action: {
                if let slideId { host.pasteAfterSlide(id: slideId) } else { host.paste() }
            })
        )
    }

    entries += [
        .separator(),

        command("Delete Slide", host.deleteSlide(id:), host.deleteSelectedSlide),

        .separator(),

        // Keynote's titles, following the slide the menu is about: the row's own
        // when a row opened it, the selected one in the bar.
        MenuEntry(title: skipped ? "Don't Skip Slide" : "Skip Slide", action: {
            if let slideId {
                host.setSlideSkipped(id: slideId, skipped: !skipped)
            } else {
                host.toggleSelectedSlideSkipped()
            }
        }),
    ]
    return entries
}

/// The entries as SwiftUI. The NSMenu builder walks the same list.
private struct MenuEntries: View {
    let entries: [MenuEntry]

    var body: some View {
        ForEach(entries) { entry in
            if entry.isSeparator {
                Divider()
            } else if let children = entry.children {
                Menu(entry.title) { MenuEntries(entries: children) }
                    .disabled(!entry.enabled)
            } else {
                Button(entry.title) { entry.action?() }
                    .keyboardShortcut(entry.shortcut)
                    .disabled(!entry.enabled)
            }
        }
    }
}

/// NSMenuItem calls a selector on a target it does not retain, so the closure
/// rides as the item's represented object, which it does.
private final class MenuAction: NSObject {
    private let run: () -> Void

    init(_ run: @escaping () -> Void) { self.run = run }

    @objc func fire() { run() }
}

/// The entries as an NSMenu. Nothing autoenables: AppKit would ask a responder
/// chain that knows nothing about the Kotlin selection, and what these were
/// built from is already a step ahead of it.
private func nsMenu(_ entries: [MenuEntry]) -> NSMenu {
    let menu = NSMenu()
    menu.autoenablesItems = false
    for entry in entries {
        guard !entry.isSeparator else {
            menu.addItem(.separator())
            continue
        }
        let item = NSMenuItem(title: entry.title, action: nil, keyEquivalent: "")
        item.isEnabled = entry.enabled
        if let children = entry.children {
            item.submenu = nsMenu(children)
        } else if let action = entry.action {
            let handler = MenuAction(action)
            item.target = handler
            item.action = #selector(MenuAction.fire)
            item.representedObject = handler
        }
        menu.addItem(item)
    }
    return menu
}

/// The canvas context menu: the clipboard four, then Arrange. Built per click
/// from that click's own facts, and acting through the selection-based methods,
/// which have long settled by the time a human picks an item.
private func popCanvasMenu(host: EditorHost, elementId: String?) {
    let facts = host.contextFacts(elementId: elementId)
    let entries: [MenuEntry] = [
        MenuEntry(title: "Cut", enabled: facts.canCut, action: { host.cutSelection() }),
        MenuEntry(title: "Copy", enabled: facts.canCopy, action: { host.doCopySelection() }),
        MenuEntry(title: "Paste", enabled: facts.canPaste, action: { host.paste() }),
        MenuEntry(title: "Delete", enabled: facts.canDelete, action: { host.deleteSelection() }),
        .separator(),
    ] + arrangeEntries(ArrangeFacts(facts), host)

    let menu = nsMenu(entries)
    let view = host.view
    guard let window = view.window else { return }

    // Where the click landed, off the event itself; the pointer, converted, for
    // the case where there is no event to read.
    var inWindow = window.convertPoint(fromScreen: NSEvent.mouseLocation)
    if let event = NSApp.currentEvent, event.window === window {
        inWindow = event.locationInWindow
    }
    let location = view.convert(inWindow, from: nil)

    // Compose is still handling the click, so the menu's tracking loop waits for
    // the next turn of the main queue rather than running from inside it.
    DispatchQueue.main.async {
        menu.popUp(positioning: nil, at: location, in: view)
    }
}

// MARK: - App

/// Activates the app once it finishes launching. Launched by exec'ing the
/// binary, which is what run.sh does to keep logs on the terminal,
/// LaunchServices never activates the process, and SwiftUI holds the
/// WindowGroup's window back until the first activation (instrumented: zero
/// windows ever exist before it), so the window only appeared after a dock
/// click sent activate + reopen. A Finder or `open` launch never needed this;
/// it makes the dev loop behave like one.
private final class ActivationDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        if #available(macOS 14.0, *) {
            NSApp.activate()
        } else {
            NSApp.activate(ignoringOtherApps: true)
        }
    }
}

@main
struct CupboardHostApp: App {
    @NSApplicationDelegateAdaptor(ActivationDelegate.self) private var activation
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
            // Same menu, below Undo/Redo. Backspace is the key equivalent, which
            // is also what makes it fire for a selection made on the canvas.
            CommandGroup(after: .undoRedo) {
                let _ = model.generation
                Divider()
                Button("Delete") { host.deleteSelection() }
                    .keyboardShortcut(.delete, modifiers: [])
                    .disabled(!host.canDelete())
                Button("Clear All") { host.clearAll() }
                    .disabled(!host.canClearAll())
            }
            // Replacing rather than adding: the system Cut/Copy/Paste items own
            // Cmd+X/C/V and would shadow ours. The clipboard is the app's own,
            // so nothing here has any business talking to NSPasteboard.
            // doCopy... is the exporter's doing: copy is a reserved ObjC method
            // family, so every copyX on the host arrives here as doCopyX.
            CommandGroup(replacing: .pasteboard) {
                let _ = model.generation
                Button("Cut") { host.cutSelection() }
                    .keyboardShortcut("x", modifiers: .command)
                    .disabled(!host.canCut())
                Button("Copy") { host.doCopySelection() }
                    .keyboardShortcut("c", modifiers: .command)
                    .disabled(!host.canCopy())
                Button("Paste") { host.paste() }
                    .keyboardShortcut("v", modifiers: .command)
                    .disabled(!host.canPaste())
                Button("Duplicate") { host.duplicateSelection() }
                    .keyboardShortcut("d", modifiers: .command)
                    .disabled(!host.canDuplicate())

                Divider()

                Button("Copy Style") { host.doCopyStyle() }
                    .keyboardShortcut("c", modifiers: [.command, .option])
                    .disabled(!host.canCopyStyle())
                Button("Paste Style") { host.pasteStyle() }
                    .keyboardShortcut("v", modifiers: [.command, .option])
                    .disabled(!host.canPasteStyle())
            }
            CommandGroup(after: .sidebar) {
                let _ = model.generation
                Toggle("Show Speaker Notes", isOn: Binding(
                    get: { host.showNotes() },
                    set: { _ in host.toggleNotes() }
                ))
            }
            slideMenu
            arrangeMenu
        }
    }

    /// Acts on the slide as a whole rather than what is on it. No key equivalents:
    /// Cmd+X/C/V belong to the elements on the canvas, and a slide-level clipboard
    /// stealing them would make the common case unreachable.
    private var slideMenu: some Commands {
        CommandMenu("Slide") {
            // Reading generation is what keeps these acting on the slide that is
            // selected now. Off the same list the navigator rows render, minus
            // Paste: in the bar that verb is Edit > Paste's.
            let _ = model.generation
            MenuEntries(entries: slideEntries(host, slideId: nil, includePaste: false))
        }
    }

    /// The Compose shell's Arrange menu, natively, off the same entry list the
    /// canvas context menu renders. One statement of the items, two renderings:
    /// the two menus cannot drift apart, only be built from different facts.
    private var arrangeMenu: some Commands {
        CommandMenu("Arrange") {
            // Reading generation is what keeps these enabled states fresh.
            let _ = model.generation
            MenuEntries(entries: arrangeEntries(ArrangeFacts(Chrome(host)), host))
        }
    }
}

// MARK: - Editor

/// The navigator's scroll content, as a coordinate space: row frames, the drag
/// and the drop line are all measured in it, so they cannot disagree.
private enum NavigatorSpace {
    static let name = "navigator"
}

/// Where each row sits in [NavigatorSpace], keyed by slide id rather than by
/// index: a reorder moves rows between indices, and the drag has to keep
/// following the row it picked up.
private struct RowFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]

    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue()) { _, latest in latest }
    }
}

/// A navigator row on the move, and the gap it is over. SwiftUI state, unlike
/// the Compose canvas's gestures: this tree redraws off `@State` the way it is
/// built to, and nothing but the drop is the document's business.
private struct SlideDrag: Equatable {
    let slideId: String
    /// The row the drop lands after, nil for the gap above the first row.
    let afterId: String?
    /// Over `afterId`'s own row rather than the gap under it: the drop nests.
    let nest: Bool
    /// Where the drop line draws, in [NavigatorSpace]. Unused when nesting.
    let lineY: CGFloat
    /// How far the pointer has carried the row: it travels with the cursor.
    let translationY: CGFloat
}

private struct EditorView: View {
    let model: EditorModel
    @Binding var playSession: PlaySession?

    @Environment(\.colorScheme) private var colorScheme
    /// Zoom is view-local in the Kotlin host, outside `states`, so the label
    /// reads from this mirror rather than waiting on a generation bump.
    @State private var zoomPercent: Int = 0
    /// The row being dragged, and where each row sits for the gap maths.
    @State private var slideDrag: SlideDrag?
    @State private var rowFrames: [String: CGRect] = [:]

    private var host: EditorHost { model.host }
    private var palette: Palette { Palette.of(colorScheme) }

    /// Touching `generation` is what subscribes this view to store changes.
    private var chrome: Chrome {
        let _ = model.generation
        return Chrome(host)
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
        let dragged = draggedRun(in: rows)
        return ScrollView {
            // Rows are identified by their slide, so collapsing a group reads as
            // those rows leaving and everything after sliding up, and a reorder
            // as one row moving, not as every row changing in place.
            LazyVStack(alignment: .leading, spacing: 2) {
                ForEach(rows, id: \.slideId) { row in
                    NavigatorRow(
                        row: row,
                        selected: row.slideIndex == selected,
                        dragging: dragged.contains(row.slideId),
                        nestTarget: slideDrag?.nest == true && slideDrag?.afterId == row.slideId,
                        liftY: dragged.contains(row.slideId) ? slideDrag?.translationY ?? 0 : 0,
                        palette: palette,
                        host: host,
                        onDrag: { point, translation in dragSlide(row, to: point, by: translation, rows: rows) },
                        onDrop: dropSlide
                    )
                    .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
            .padding(EdgeInsets(top: 2, leading: 8, bottom: 16, trailing: 8))
            .frame(maxWidth: .infinity, alignment: .leading)
            .coordinateSpace(name: NavigatorSpace.name)
            .onPreferenceChange(RowFrames.self) { frames in rowFrames = frames }
            .overlay(alignment: .topLeading) { dropLine }
            .animation(.easeInOut(duration: 0.22), value: rows.map(\.slideId))
        }
        .scrollContentBackground(.hidden)
    }

    /// The dragged row and every row nested under it: a parent drags as a group
    /// (`moveSlide` lands it as one), so the whole run steps back together.
    private func draggedRun(in rows: [OutlineRow]) -> Set<String> {
        guard let drag = slideDrag else { return [] }
        return draggedRun(in: rows, from: drag.slideId)
    }

    private func draggedRun(in rows: [OutlineRow], from slideId: String) -> Set<String> {
        guard let start = rows.firstIndex(where: { $0.slideId == slideId }) else { return [] }
        let depth = rows[start].depth
        var ids: Set<String> = [slideId]
        for row in rows[(start + 1)...] {
            if row.depth <= depth { break }
            ids.insert(row.slideId)
        }
        return ids
    }

    /// The spot the drag is over. The middle half of a row that is not itself
    /// on the move nests the drop under that row. Otherwise a gap: above the
    /// first row's midpoint is the front of the deck, else after the last row
    /// whose midpoint the pointer has passed.
    private func dragSlide(_ row: OutlineRow, to point: CGPoint, by translation: CGSize, rows: [OutlineRow]) {
        let placed: [(row: OutlineRow, frame: CGRect)] = rows.compactMap { candidate in
            rowFrames[candidate.slideId].map { (candidate, $0) }
        }
        guard let first = placed.first else { return }
        let dragged = draggedRun(in: rows, from: row.slideId)

        var afterId: String?
        // Half the 2pt row gap above the first row, so the line sits in the gap
        // rather than on a row's edge.
        var lineY: CGFloat = first.frame.minY - 1
        for (candidate, frame) in placed {
            let quarter = frame.height / 4
            if !dragged.contains(candidate.slideId),
               (frame.minY + quarter...frame.maxY - quarter).contains(point.y) {
                slideDrag = SlideDrag(
                    slideId: row.slideId, afterId: candidate.slideId, nest: true, lineY: 0,
                    translationY: translation.height
                )
                return
            }
            if frame.midY >= point.y { break }
            afterId = candidate.slideId
            lineY = frame.maxY + 1
        }
        slideDrag = SlideDrag(
            slideId: row.slideId, afterId: afterId, nest: false, lineY: lineY,
            translationY: translation.height
        )
    }

    /// The core no-ops a drop back into the row's own gap, so every drop is sent.
    /// The lift and the reorder settle under one animation, so the row glides
    /// from under the pointer straight into its new slot.
    private func dropSlide() {
        guard let drag = slideDrag else { return }
        withAnimation(.easeInOut(duration: 0.22)) {
            slideDrag = nil
            host.moveSlide(id: drag.slideId, afterId: drag.afterId, nest: drag.nest)
        }
    }

    @ViewBuilder private var dropLine: some View {
        if let drag = slideDrag, !drag.nest {
            palette.accent
                .frame(height: 2)
                .padding(.horizontal, 8)
                .offset(y: drag.lineY - 1)
        }
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
        /// This row is the one being dragged, so it steps back while it travels.
        let dragging: Bool
        /// The drag is over this row's body: dropping nests under it.
        let nestTarget: Bool
        /// How far this row has been carried by the drag, 0 when it hasn't.
        let liftY: CGFloat
        let palette: Palette
        let host: EditorHost
        /// A drag sample, in [NavigatorSpace] plus its travel, and the release
        /// that drops it.
        let onDrag: (CGPoint, CGSize) -> Void
        let onDrop: () -> Void

        @State private var hovering = false
        @State private var chevronHovering = false

        private var thumbWidth: CGFloat { Layout.thumbnail - 12 * CGFloat(min(row.depth, 3)) }

        var body: some View {
            HStack(spacing: 4) {
                gutter
                // Skipped is a slide out of the presentation, not out of the
                // deck: the gutter keeps its width, the slide reads back.
                thumbnail.opacity(row.skipped ? 0.4 : 1)
            }
            .padding(.vertical, 5)
            .padding(.horizontal, 6)
            .background { capsule }
            .overlay {
                if nestTarget {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .inset(by: 1)
                        .stroke(palette.accent, lineWidth: 2)
                }
            }
            // Lifted: a touch translucent to show the rows it passes over, and
            // above them while it travels.
            .opacity(dragging ? 0.85 : 1)
            .offset(y: liftY)
            .zIndex(dragging ? 1 : 0)
            .contentShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .onTapGesture { host.selectSlide(index: row.slideIndex) }
            // Enough slop that a click is still a click: the drag only takes
            // over once the pointer has actually travelled.
            .gesture(
                DragGesture(minimumDistance: 6, coordinateSpace: .named(NavigatorSpace.name))
                    .onChanged { value in onDrag(value.location, value.translation) }
                    .onEnded { _ in onDrop() }
            )
            .background {
                GeometryReader { proxy in
                    Color.clear.preference(
                        key: RowFrames.self,
                        value: [row.slideId: proxy.frame(in: .named(NavigatorSpace.name))]
                    )
                }
            }
            .contextMenu {
                // No selecting the row first, unlike the canvas menu: every entry
                // carries this row's id, and the ones that end in a selection
                // (new, duplicate, delete, cut, paste) settle it in the core.
                MenuEntries(entries: slideEntries(host, slideId: row.slideId, includePaste: true))
            }
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
                // The presentation number, which a skipped slide has none of.
                Text(row.numberLabel)
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

            VStack(spacing: 1) {
                Text(inspectorTitle(ui))
                    .font(.system(size: 13))
                    .foregroundStyle(palette.subtle)
                // The title names the primary element, so with more than one
                // selected it has to say what else the controls are editing.
                if ui.tab == InspectorTab.format && ui.selectionCount > 1 {
                    Text("\(ui.selectionCount) selected")
                        .font(.system(size: 11))
                        .foregroundStyle(palette.faint)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 4)
            .padding(.horizontal, Layout.panelPadding)
            .padding(.bottom, 12)

            if ui.tab == InspectorTab.document {
                documentPanel(ui)
            } else if ui.tab == InspectorTab.format {
                formatPanel(ui)
            } else {
                // The Animate body lands with the build editor.
                Spacer(minLength: 0)
            }
        }
        .frame(width: Layout.inspector)
        .frame(maxHeight: .infinity)
        .glass(.sidebar, edge: .leading, palette: palette)
    }

    /// Format names what it is formatting, so the title follows the selection.
    private func inspectorTitle(_ ui: Chrome) -> String {
        if ui.tab == InspectorTab.animate { return "Build" }
        if ui.tab == InspectorTab.document { return "Slide" }
        return ui.element?.kind ?? "Text"
    }

    // MARK: Format panel

    /// The selected element's shared properties. Everything shown here comes
    /// back through `states`, so a typed value, a canvas drag and an undo all
    /// land in the same place.
    @ViewBuilder private func formatPanel(_ ui: Chrome) -> some View {
        if let element = ui.element {
            VStack(alignment: .leading, spacing: Layout.panelPadding) {
                VStack(alignment: .leading, spacing: Layout.panelPadding) {
                    positionSection(element)
                    palette.divider.frame(height: 1)
                    rotateSection(element)
                    palette.divider.frame(height: 1)
                    opacitySection(element)
                    palette.divider.frame(height: 1)
                    arrangeSection
                }
                // A locked element ignores every edit but the button below, so
                // the panel says so rather than swallowing them silently.
                .disabled(element.locked)
                .opacity(element.locked ? 0.45 : 1)

                Spacer(minLength: 0)

                // Two unlocked elements make a group; a lone group comes apart
                // again. Neither button is here when it has nothing to do.
                if ui.canGroup {
                    panelButton("Group", symbol: "square.on.square") { host.groupSelection() }
                }
                if ui.canUngroup {
                    panelButton("Ungroup", symbol: "square.split.2x2") { host.ungroupSelection() }
                }

                lockButton(element)
            }
            .padding(Layout.panelPadding)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        } else {
            Text("Select an element to edit it")
                .font(.system(size: 12))
                .foregroundStyle(palette.faint)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(Layout.panelPadding)
        }
    }

    private func positionSection(_ element: Selection) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Position & Size")

            HStack(spacing: 8) {
                ValueField(label: "X", value: element.x, palette: palette) {
                    setFrame(element, x: $0)
                }
                ValueField(label: "Y", value: element.y, palette: palette) {
                    setFrame(element, y: $0)
                }
            }

            HStack(spacing: 8) {
                ValueField(label: "W", value: element.width, palette: palette) {
                    setFrame(element, width: $0)
                }
                ValueField(label: "H", value: element.height, palette: palette) {
                    setFrame(element, height: $0)
                }
            }
        }
    }

    /// A frame commits whole, so a field that edits one number sends the other
    /// three back as they stand.
    private func setFrame(
        _ element: Selection,
        x: Double? = nil,
        y: Double? = nil,
        width: Double? = nil,
        height: Double? = nil
    ) {
        host.setSelectedElementFrame(
            x: Float(x ?? element.x),
            y: Float(y ?? element.y),
            width: Float(width ?? element.width),
            height: Float(height ?? element.height)
        )
    }

    private func rotateSection(_ element: Selection) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Rotate")

            HStack(spacing: 8) {
                ValueField(label: "\u{00B0}", value: element.rotation, palette: palette) {
                    host.setSelectedElementRotation(degrees: Float($0))
                }
                .frame(width: 104)

                Spacer(minLength: 0)

                flipButton(
                    "arrow.left.and.right.righttriangle.left.righttriangle.right",
                    help: "Flip Horizontally"
                ) { host.flipSelectedElement(axis: FlipAxis.horizontal) }

                flipButton(
                    "arrow.up.and.down.righttriangle.up.righttriangle.down",
                    help: "Flip Vertically"
                ) { host.flipSelectedElement(axis: FlipAxis.vertical) }
            }
        }
    }

    private func flipButton(
        _ symbol: String,
        help: String,
        action: @escaping () -> Void
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 12))
                .foregroundStyle(palette.ctrlText)
                .frame(width: 32, height: 22)
                .background(palette.ctrl, in: shape)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
        .help(help)
    }

    private func opacitySection(_ element: Selection) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Opacity")

            HStack(spacing: 10) {
                Slider(
                    value: Binding(
                        get: { element.opacity },
                        set: { host.setSelectedElementOpacity(opacity: Float($0), commit: false) }
                    ),
                    in: 0...1,
                    onEditingChanged: { editing in
                        // The release commits whatever the previews left in the
                        // document, so the whole drag is one undo entry. Read it
                        // back rather than trusting this pass's snapshot.
                        guard !editing, let live = host.selectedElement() else { return }
                        host.setSelectedElementOpacity(opacity: live.opacity, commit: true)
                    }
                )
                .controlSize(.small)
                .tint(palette.accent)

                Text("\(Int((element.opacity * 100).rounded()))%")
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(palette.ctrlText)
                    .frame(width: 40, alignment: .trailing)
            }
        }
    }

    private var arrangeSection: some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Arrange")

            HStack(spacing: 8) {
                arrangeButton("Bring Forward", ZOrderMove.forward)
                arrangeButton("Send Backward", ZOrderMove.backward)
            }

            HStack(spacing: 8) {
                arrangeButton("Bring to Front", ZOrderMove.tofront)
                arrangeButton("Send to Back", ZOrderMove.toback)
            }
        }
    }

    private func arrangeButton(_ label: String, _ move: ZOrderMove) -> some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return Button { host.reorderSelectedElement(move: move) } label: {
            Text(label)
                .font(.system(size: 11.5))
                .foregroundStyle(palette.ctrlText)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .frame(maxWidth: .infinity)
                .frame(height: 22)
                .background(palette.ctrl, in: shape)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
    }

    /// Full width and always live: it is the only way back into a locked element.
    private func lockButton(_ element: Selection) -> some View {
        panelButton(
            element.locked ? "Unlock" : "Lock",
            symbol: element.locked ? "lock.fill" : "lock.open"
        ) { host.toggleSelectedElementLock() }
    }

    /// The panel's raised full-width button: the lock, and the group pair above it.
    private func panelButton(
        _ label: String,
        symbol: String,
        action: @escaping () -> Void
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: 6, style: .continuous)
        return Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: symbol)
                    .font(.system(size: 11))
                Text(label)
                    .font(.system(size: 12.5))
            }
            .foregroundStyle(palette.ctrlText)
            .frame(maxWidth: .infinity)
            .frame(height: 26)
            .background(palette.buttonFill, in: shape)
            .contentShape(shape)
        }
        .buttonStyle(.plain)
    }

    /// A small bordered value field: whole document units, mono, committed on
    /// Enter or on losing focus. Anything that is not a number reverts to what
    /// the document holds, so a half-typed field cannot push nonsense in.
    private struct ValueField: View {
        let label: String
        let value: Double
        let palette: Palette
        let onCommit: (Double) -> Void

        @State private var text: String = ""
        @FocusState private var focused: Bool

        private var shape: RoundedRectangle {
            RoundedRectangle(cornerRadius: 5, style: .continuous)
        }

        var body: some View {
            HStack(spacing: 6) {
                Text(label)
                    .font(.system(size: 11))
                    .foregroundStyle(palette.subtle)
                    .frame(width: 11, alignment: .leading)

                TextField("", text: $text)
                    .textFieldStyle(.plain)
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(palette.ctrlText)
                    .multilineTextAlignment(.trailing)
                    .focused($focused)
                    .padding(.horizontal, 7)
                    .frame(height: 22)
                    .background(palette.ctrl, in: shape)
                    .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
                    .onSubmit { commit() }
                    .onChange(of: focused) { _, now in if !now { commit() } }
            }
            .onAppear { text = Self.whole(value) }
            // A canvas drag or an undo moves the element under the field. The
            // one being typed in is left alone until it loses focus.
            .onChange(of: value) { _, latest in if !focused { text = Self.whole(latest) } }
        }

        private func commit() {
            guard let typed = Double(text.trimmingCharacters(in: .whitespaces)) else {
                text = Self.whole(value)
                return
            }
            text = Self.whole(typed)
            onCommit(typed)
        }

        private static func whole(_ value: Double) -> String { String(Int(value.rounded())) }
    }

    // MARK: Document panel

    /// The selected slide, as far as the document model goes: the number switch
    /// and the background are real and ride through `states`; the layout card
    /// above them is still static, layouts not being modelled yet.
    private func documentPanel(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: Layout.panelPadding) {
            slideLayoutCard
            appearanceSection(ui)
            palette.divider.frame(height: 1)
            backgroundSection(ui)
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

    /// Title and Body are still inert: what a layout puts on a slide is the
    /// layout's, and layouts are not modelled yet. The number is the slide's own.
    private func appearanceSection(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Appearance")
            checkRow("Title", on: true)
            checkRow("Body", on: true)
            checkRow("Slide Number", on: ui.slide.numberVisible) {
                host.setSlideNumberVisible(visible: !ui.slide.numberVisible)
            }
        }
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(palette.subtle)
    }

    /// [action] nil is a row that only reports: the ones whose fact the document
    /// model does not hold yet stay untouchable rather than lying about a toggle.
    @ViewBuilder private func checkRow(
        _ label: String,
        on: Bool,
        action: (() -> Void)? = nil
    ) -> some View {
        let row = HStack(spacing: 8) {
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
            Spacer(minLength: 0)
        }

        if let action {
            Button(action: action) { row.contentShape(Rectangle()) }
                .buttonStyle(.plain)
        } else {
            row
        }
    }

    /// What the slide paints behind its elements: the deck's own, a flat colour,
    /// or a two-stop gradient. Switching to a kind the slide is not wearing
    /// commits it there and then, so the swatches below always have something to
    /// mark, and every pick is one edit and one undo entry.
    @ViewBuilder private func backgroundSection(_ ui: Chrome) -> some View {
        let slide = ui.slide
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Background")

            HStack(spacing: 2) {
                segment("Default", on: slide.backgroundKind == 0) { host.setBackgroundDefault() }
                segment("Color", on: slide.backgroundKind == 1) {
                    host.setBackgroundColor(argb: slide.color)
                }
                segment("Gradient", on: slide.backgroundKind == 2) {
                    host.setBackgroundGradient(start: slide.gradientStart, end: slide.gradientEnd)
                }
            }
            .padding(2)
            .frame(height: 26)
            .background(palette.segBg, in: RoundedRectangle(cornerRadius: 6, style: .continuous))

            switch slide.backgroundKind {
            case 1:
                swatchGrid(selected: slide.color) { host.setBackgroundColor(argb: $0) }
            case 2:
                stopRow("Start", selected: slide.gradientStart) {
                    host.setBackgroundGradient(start: $0, end: slide.gradientEnd)
                }
                stopRow("End", selected: slide.gradientEnd) {
                    host.setBackgroundGradient(start: slide.gradientStart, end: $0)
                }
            default:
                Text("The deck's own background.")
                    .font(.system(size: 12))
                    .foregroundStyle(palette.faint)
            }
        }
    }

    /// One end of the gradient: the same swatches, said whose they are.
    private func stopRow(
        _ label: String,
        selected: Int64,
        onPick: @escaping (Int64) -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.system(size: 11))
                .foregroundStyle(palette.subtle)
            swatchGrid(selected: selected, onPick: onPick)
        }
    }

    /// The palette, six to a row. No colour panel yet: one tap is one colour and
    /// one undo entry, which a live picker would spend a hundred entries on.
    private func swatchGrid(selected: Int64, onPick: @escaping (Int64) -> Void) -> some View {
        LazyVGrid(
            columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 6),
            spacing: 6
        ) {
            ForEach(Self.backgroundSwatches, id: \.self) { argb in
                swatch(argb, selected: argb == selected, onPick: onPick)
            }
        }
    }

    private func swatch(
        _ argb: Int64,
        selected: Bool,
        onPick: @escaping (Int64) -> Void
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return Button { onPick(argb) } label: {
            shape
                .fill(Color(argb: argb))
                .frame(height: 22)
                .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
                .overlay {
                    if selected { shape.inset(by: -2.5).stroke(palette.accent, lineWidth: 2) }
                }
                .contentShape(shape)
        }
        .buttonStyle(.plain)
    }

    /// The deck's inks first, then the accents, then two paper tones. Packed
    /// ARGB, the document model's colour format.
    private static let backgroundSwatches: [Int64] = [
        0xFF000000, 0xFF17181C, 0xFF23262E, 0xFF101223, 0xFF2A2452, 0xFF4C2FA8,
        0xFF0F3B39, 0xFF10391F, 0xFF58151D, 0xFF6B4A0E, 0xFFD7D9DE, 0xFFFFFFFF,
    ]

    private func segment(_ label: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12, weight: on ? .semibold : .regular))
                .foregroundStyle(on ? palette.accentText : palette.subtle)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(
                    on ? AnyShapeStyle(palette.accent) : AnyShapeStyle(Color.clear),
                    in: RoundedRectangle(cornerRadius: 5, style: .continuous)
                )
                .contentShape(RoundedRectangle(cornerRadius: 5, style: .continuous))
        }
        .buttonStyle(.plain)
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
