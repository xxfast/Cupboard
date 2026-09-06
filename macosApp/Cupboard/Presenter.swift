import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Presenter display

/// The keys the presenter window drives the show with, by AppKit key code.
/// Spelled out because NSEvent has no names for them and the presenter window
/// is not a Compose surface, so nothing else here is holding this table.
private enum PresenterKey {
    static let forward: Set<UInt16> = [124, 125, 49, 36] // right, down, space, return
    static let back: Set<UInt16> = [123, 126, 51] // left, up, delete
    static let escape: UInt16 = 53
}

/// The show's second window: whatever the presenter looks at while the audience
/// looks at the deck. AppKit's, not SwiftUI's, because its content is a Compose
/// view the session owns and disposes.
///
/// Playback keys pressed here go to the same session the show is playing on, so
/// which window happens to be key never changes what an arrow does.
final class PresenterKeyWindow: NSWindow {
    /// True when the session took the key, which is what stops AppKit beeping.
    var onKey: ((NSEvent) -> Bool)?

    override func keyDown(with event: NSEvent) {
        if onKey?(event) == true { return }
        super.keyDown(with: event)
    }
}

/// Owns the presenter window across a show: opens one when a session that has a
/// presenter starts, closes it when the show ends or the menu toggle goes off.
///
/// Driven by [PresenterBridge] on every view update rather than by a callback,
/// the way the traffic lights are: one place decides whether the window should
/// be up, off the state that is on screen right now.
final class PresenterWindow: NSObject, NSWindowDelegate {
    private var window: PresenterKeyWindow?
    private var session: PlaySession?
    /// The show whose presenter the user shut with the red button. Reopening it
    /// is the menu toggle's job, not the next stray view update's.
    private var dismissed: PlaySession?
    /// Where the X key goes. Set by [ShowDisplays], which is what knows whether
    /// there is a second screen to swap with.
    var onSwap: (() -> Void)?

    /// [session] is the show that is playing, or nil for none; [enabled] is what
    /// the View menu says, and [screen] the display to fill, nil for a window
    /// that opens at a size and sits where the user puts it.
    func sync(session: PlaySession?, enabled: Bool, screen: NSScreen?) {
        guard enabled, let session, session.hasPresenter else {
            close()
            dismissed = nil
            return
        }
        if session !== self.session { close() }
        guard dismissed !== session else { return }
        open(session)
        place(on: screen)
    }

    /// Fills [screen], or leaves the window where it is with none: on one display
    /// the presenter is a window like any other, opened centred and moved by hand.
    func place(on screen: NSScreen?) {
        guard let window, let screen else { return }
        window.setFrame(screen.visibleFrame, display: true)
    }

    private func open(_ session: PlaySession) {
        guard window == nil else { return }
        self.session = session

        let window = PresenterKeyWindow(
            contentRect: NSRect(x: 0, y: 0, width: 1280, height: 720),
            styleMask: [.titled, .closable, .miniaturizable, .resizable],
            backing: .buffered,
            defer: false
        )
        window.title = "Cupboard Presenter"
        // We hold the only reference and drop it in [close]; letting AppKit
        // release it under ARC is the classic over-release.
        window.isReleasedWhenClosed = false
        window.delegate = self
        window.contentView = session.presenterView
        window.onKey = { [weak self, weak session] event in
            guard let session else { return false }
            if event.keyCode == ShowKey.swap {
                self?.onSwap?()
                return true
            }
            if PresenterKey.forward.contains(event.keyCode) {
                session.next()
                return true
            }
            if PresenterKey.back.contains(event.keyCode) {
                session.previous()
                return true
            }
            if event.keyCode == PresenterKey.escape {
                session.exit()
                return true
            }
            return false
        }
        window.center()
        // Front, but not key: the show keeps the keyboard, which is where the
        // presenter is driving from. Clicking this window still works, the keys
        // land on the same session either way.
        window.orderFront(nil)
        self.window = window
    }

    /// Tears the window down and, with it, the Compose scene behind it. Safe to
    /// call with nothing up.
    private func close() {
        guard let window else {
            session = nil
            return
        }
        window.delegate = nil
        window.onKey = nil
        // Off the window before the scene closes, so nothing is left drawing a
        // view that has been disposed.
        window.contentView = NSView()
        window.close()
        self.window = nil
        session?.disposePresenter()
        session = nil
    }

    /// The red button. The show carries on without it; the menu toggle is how
    /// it comes back.
    func windowWillClose(_ notification: Notification) {
        dismissed = session
        // Not from inside the close notification: the window is mid-teardown.
        DispatchQueue.main.async { [weak self] in self?.close() }
    }
}
