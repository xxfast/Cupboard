import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Show

/// Where a show plays. Keynote's two answers, off the Play menu: over the
/// displays, or in the editor's own window.
enum Stage {
    /// The editor window becomes the player, where it already is. The presenter
    /// display, if any, is a window of its own.
    case window
    /// The displays: the deck fills one, and with a second the presenter display
    /// fills the other. One display still gets covered, editor and all.
    case screens
}

/// A running presentation, and where it is playing.
///
/// Settled when the show starts rather than read per frame: a display waking up
/// or sleeping mid-show is no reason to move the deck out from under a speaker.
struct Show {
    let session: PlaySession
    let stage: Stage

    /// A show, where the Play menu says: the editor window, or the displays
    /// however many there are.
    static func play(_ session: PlaySession, inWindow: Bool) -> Show {
        Show(session: session, stage: inWindow ? .window : .screens)
    }

    /// One slide on its own. Always in the editor window, whatever is plugged in:
    /// a preview is something you look at while editing, not something you show.
    static func preview(_ session: PlaySession) -> Show {
        Show(session: session, stage: .window)
    }

    /// A run-through for the speaker alone: the presenter display, and no deck
    /// in front of anyone. [ShowDisplays] arranges it, which is what makes it the
    /// displays' rather than the window's, though there is no audience screen.
    static func rehearse(_ session: PlaySession) -> Show {
        Show(session: session, stage: .screens)
    }

    /// Whether this show is a rehearsal, which is the session's own fact.
    var rehearsal: Bool { session.isRehearsal }
}

/// The keys the play windows handle themselves, by AppKit key code. Playback is
/// the session's; this is what is left over.
enum ShowKey {
    static let swap: UInt16 = 7 // x
}

/// The show's own window on a display: borderless, over the menu bar and the
/// dock, filling the screen the audience is looking at.
///
/// Playback keys are the Compose player's, as they are in the main window. What
/// it leaves comes back up the responder chain to here, which is where the swap
/// key is caught.
final class ShowKeyWindow: NSWindow {
    /// True when the window took the key, which is what stops AppKit beeping.
    var onKey: ((NSEvent) -> Bool)?

    // A borderless window refuses the keyboard by default, and this is the
    // window the show is driven from.
    override var canBecomeKey: Bool { true }
    override var canBecomeMain: Bool { true }

    override func keyDown(with event: NSEvent) {
        if onKey?(event) == true { return }
        super.keyDown(with: event)
    }
}

/// Which screen shows what, for the length of a show. Owns the show's window and
/// drives [PresenterWindow], so both faces are placed by one decision rather than
/// by two that have to agree.
///
/// Driven by [ShowBridge] on every view update, the way the traffic lights are:
/// one place decides what should be up, off the state that is on screen now.
final class ShowDisplays {
    /// Where the last swap left the show, remembered across shows and launches
    /// the way Keynote does: a lectern wired the other way round stays that way.
    private static let swappedKey = "showOnPrimaryDisplay"

    private let presenter = PresenterWindow()
    private var window: ShowKeyWindow?
    private var session: PlaySession?
    /// Settled when the show opens, so every placement pass agrees with the
    /// last. [presenterScreen] is nil with one display: nowhere to put it.
    private var showScreen: NSScreen?
    private var presenterScreen: NSScreen?

    /// A rehearsal: the show window is up, 1x1 in a corner, because the player
    /// is what drives the playback the presenter reads. Nothing else about it
    /// is a show, so this is what the placement and the swap key branch on.
    private var rehearsing: Bool { session?.isRehearsal == true }

    init() {
        presenter.onSwap = { [weak self] in self?.swapDisplays() }
    }

    /// [show] is what is playing, or nil for nothing; [presenterEnabled] is what
    /// the Play menu says.
    func sync(show: Show?, presenterEnabled: Bool) {
        guard let show, show.stage == .screens else {
            // In the window, the presenter is a window too, with no screen to
            // fill. The presenter first: closing the show window disposes the
            // session, and the presenter is drawing a view that goes with it.
            presenter.sync(session: show?.session, enabled: presenterEnabled, screen: nil)
            close()
            return
        }
        let started = show.session !== session
        if started {
            close()
            open(show.session)
        }
        // A rehearsal is the presenter display. The Play menu's toggle says
        // whether a show gets one behind it, and one display has no room for it.
        presenter.sync(
            session: show.session,
            enabled: show.rehearsal || (presenterEnabled && presenterScreen != nil),
            screen: presenterScreen
        )
        // No show window to drive from, so the presenter takes the keyboard.
        // Only as the rehearsal opens: every later pass would be stealing it.
        if started, show.rehearsal { presenter.takeKeyboard() }
        place()
    }

    /// The Play menu's item and the X key: the show moves to the other screen and
    /// the presenter takes the one it left. Remembered, so the next show opens
    /// the same way round.
    func swapDisplays() {
        guard window != nil, !rehearsing, presenterScreen != nil else { return }
        (showScreen, presenterScreen) = (presenterScreen, showScreen)
        let defaults = UserDefaults.standard
        defaults.set(!defaults.bool(forKey: Self.swappedKey), forKey: Self.swappedKey)
        presenter.place(on: presenterScreen)
        place()
    }

    private func open(_ session: PlaySession) {
        let screens = NSScreen.screens
        // The menu bar's screen, not NSScreen.main: that follows the keyboard,
        // and which screen the editor happens to be on says nothing about which
        // one the audience is looking at.
        guard let primary = screens.first else { return }
        if session.isRehearsal {
            // Never off the speaker's screen: what it puts there is a point,
            // not a deck.
            showScreen = primary
            presenterScreen = primary
        } else if screens.count >= 2 {
            // The first other display gets the deck and the primary the
            // presenter, unless a swap last time said otherwise.
            let swapped = UserDefaults.standard.bool(forKey: Self.swappedKey)
            showScreen = swapped ? primary : screens[1]
            presenterScreen = swapped ? screens[1] : primary
        } else {
            // One display: the deck covers it, and the presenter has nowhere.
            showScreen = primary
            presenterScreen = nil
        }
        self.session = session

        let window = ShowKeyWindow(
            contentRect: frame(on: showScreen ?? primary),
            styleMask: [.borderless],
            backing: .buffered,
            defer: false
        )
        // We hold the only reference and drop it in [close]; letting AppKit
        // release it under ARC is the classic over-release.
        window.isReleasedWhenClosed = false
        // Over the menu bar and the dock: the audience sees the deck, nothing else.
        window.level = NSWindow.Level(rawValue: NSWindow.Level.mainMenu.rawValue + 1)
        window.collectionBehavior = [.fullScreenNone, .stationary, .canJoinAllSpaces]
        window.backgroundColor = .black
        window.contentView = session.view
        window.onKey = { [weak self] event in
            guard event.keyCode == ShowKey.swap else { return false }
            guard event.modifierFlags.intersection(.deviceIndependentFlagsMask).isEmpty else {
                return false
            }
            self?.swapDisplays()
            return true
        }
        guard !session.isRehearsal else {
            // On a screen, so the scene goes on drawing frames and the playback
            // the presenter is reading keeps moving, and out of everyone's way:
            // a point in the corner that no click can land in.
            window.ignoresMouseEvents = true
            window.orderFront(nil)
            self.window = window
            return
        }
        // Key, unlike the presenter window: this is the one being played, and the
        // Compose view takes first responder as soon as it has a window.
        window.makeKeyAndOrderFront(nil)
        self.window = window
    }

    private func place() {
        guard let window, let showScreen else { return }
        window.setFrame(frame(on: showScreen), display: true)
    }

    /// What the show's window fills on [screen]: all of it, or the one point a
    /// rehearsal hides the player in.
    private func frame(on screen: NSScreen) -> NSRect {
        guard rehearsing else { return screen.frame }
        return NSRect(origin: screen.frame.origin, size: NSSize(width: 1, height: 1))
    }

    /// Tears the window down and, with it, the Compose scene behind it. Safe to
    /// call with nothing up.
    private func close() {
        guard let window else {
            session = nil
            return
        }
        window.onKey = nil
        // Off the window before the scene closes, so nothing is left drawing a
        // view that has been disposed.
        window.contentView = NSView()
        window.close()
        self.window = nil
        showScreen = nil
        presenterScreen = nil
        session?.dispose()
        session = nil
    }
}

/// Pushes the show and the menu toggle at [ShowDisplays] whenever the view
/// updates. A window is not something a SwiftUI body can return, so this is the
/// seam between the two.
struct ShowBridge: NSViewRepresentable {
    let show: Show?
    let presenterEnabled: Bool
    let displays: ShowDisplays

    func makeNSView(context: Context) -> NSView {
        let view = NSView(frame: .zero)
        DispatchQueue.main.async { displays.sync(show: show, presenterEnabled: presenterEnabled) }
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {
        displays.sync(show: show, presenterEnabled: presenterEnabled)
    }
}
