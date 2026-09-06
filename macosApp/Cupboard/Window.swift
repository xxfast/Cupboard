import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Window

/// The window controls live inside the navigator card, so the real buttons get
/// moved there. Not hidden and redrawn: hover, hit testing and accessibility
/// stay the system's. AppKit relays them out behind our back on resize, on
/// becoming key and on leaving fullscreen, so each of those re-applies.
final class TrafficLights {
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
struct WindowConfigurator: NSViewRepresentable {
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
struct TrafficLightTarget: NSViewRepresentable {
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
    /// The editor this window is on. A var because Save As opens a new one on
    /// the new bundle: swapping it here means every view and every menu already
    /// pointing at this model follows, without the window being rebuilt.
    private(set) var host: EditorHost
    private(set) var generation: Int = 0
    /// File > Rename's alert. The command is in the menu bar and the alert is in
    /// the window, so the flag has to live where both can see it.
    var renaming = false
    /// What this window is playing, or nil for the editor. Here rather than in
    /// the view for the same reason: Play is a toolbar button and Preview Slide
    /// is a menu item, and one of them is not in the window.
    var show: Show?
    /// Shared so the window setup and the editor view drive the same buttons.
    @ObservationIgnored let lights = TrafficLights()
    /// The show's windows. One arrangement per window, settled when its show
    /// starts; the View menu's Swap Displays drives this window's.
    @ObservationIgnored let displays = ShowDisplays()
    @ObservationIgnored private var unsubscribe: (() -> Void)?

    init(host: EditorHost = EditorHost()) {
        self.host = host
        listen()
    }

    /// Moves this window onto [host] and stops the one it was on. Save As is the
    /// only caller: a view model's bundle is fixed for its life, so saving
    /// elsewhere is a new editor rather than a moved one.
    func adopt(_ host: EditorHost) {
        let previous = self.host
        unsubscribe?()
        self.host = host
        listen()
        generation += 1
        previous.close()
    }

    /// Stops the editor, autosave included, for a window that has gone.
    func close() {
        unsubscribe?()
        unsubscribe = nil
        host.close()
    }

    private func listen() {
        // Kotlin notifies synchronously on whichever thread mutated, which is
        // always the main thread here (SwiftUI calls, or Compose input).
        unsubscribe = host.onChange { [weak self] in self?.generation += 1 }
        // A right-click on the canvas. The event is already away by the time
        // this fires, so the menu is all that is left to do.
        host.setContextClickCallback { [weak self] elementId in
            guard let self else { return }
            popCanvasMenu(host: self.host, elementId: elementId)
        }
        // The same click, but inside a text box or code block: the caret's menu
        // rather than the slide's, and nothing to settle first.
        // Kotlin hands a closure's arguments over boxed, hence the unwrap.
        host.setFieldMenuCallback { [weak self] x, y in
            guard let self else { return }
            popFieldMenu(host: self.host, x: x.doubleValue, y: y.doubleValue)
        }
    }

    deinit {
        unsubscribe?()
    }
}

/// Which window the menu bar is about. Every command acts on the editor with
/// focus, so the deck being typed into is the one Cmd+Z undoes.
struct FocusedEditorKey: FocusedValueKey {
    typealias Value = EditorModel
}

extension FocusedValues {
    var editor: EditorModel? {
        get { self[FocusedEditorKey.self] }
        set { self[FocusedEditorKey.self] = newValue }
    }
}
