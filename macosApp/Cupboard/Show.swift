import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Show

/// A running presentation, and where it is playing. Two screens gives the show
/// one of its own and the presenter display the other; one screen is the path
/// this app has always taken, the show replacing the editor in the main window.
///
/// Settled when the show starts rather than read per frame: a display waking up
/// or sleeping mid-show is no reason to move the deck out from under a speaker.
struct Show {
    let session: PlaySession
    let external: Bool

    /// A show: its own screen whenever there is one to give it.
    static func play(_ session: PlaySession) -> Show {
        Show(session: session, external: NSScreen.screens.count >= 2)
    }

    /// One slide on its own. Always in the main window, whatever is plugged in:
    /// a preview is something you look at while editing, not something you show.
    static func preview(_ session: PlaySession) -> Show {
        Show(session: session, external: false)
    }

    /// A run-through for the speaker alone: the presenter display, and no deck
    /// on any screen. Never external, whatever is plugged in, since [external]
    /// is about the screen an audience is looking at and there isn't one.
    static func rehearse(_ session: PlaySession) -> Show {
        Show(session: session, external: false)
    }

    /// Whether this show is a rehearsal, which is the session's own fact.
    var rehearsal: Bool { session.isRehearsal }
}

/// The keys the play windows handle themselves, by AppKit key code. Playback is
/// the session's; this is what is left over.
enum ShowKey {
    static let swap: UInt16 = 7 // x
}

/// The show's own window on a second screen: borderless, over the menu bar and
/// the dock, filling the screen the audience is looking at.
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
    private let presenter = PresenterWindow()
    private var window: ShowKeyWindow?
    private var session: PlaySession?
    /// Settled when the show opens. NSScreen.main follows the keyboard, so
    /// re-reading it once the show window is key would answer with the screen the
    /// show is already on, and every placement pass would fight the last one.
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
    /// the View menu says.
    func sync(show: Show?, presenterEnabled: Bool) {
        guard let show, show.external || show.rehearsal else {
            // The presenter first: closing the show window disposes the session,
            // and the presenter is drawing a view that goes with it.
            presenter.sync(session: show?.session, enabled: presenterEnabled, screen: nil)
            close()
            return
        }
        let started = show.session !== session
        if started {
            close()
            open(show.session)
        }
        // A rehearsal is the presenter display. The View menu's toggle says
        // whether a show gets one behind it, which is not a question here.
        presenter.sync(
            session: show.session,
            enabled: show.rehearsal || presenterEnabled,
            screen: presenterScreen
        )
        // No show window to drive from, so the presenter takes the keyboard.
        // Only as the rehearsal opens: every later pass would be stealing it.
        if started, show.rehearsal { presenter.takeKeyboard() }
        place()
    }

    /// The View menu's item and the X key: the show moves to the other screen and
    /// the presenter takes the one it left.
    func swapDisplays() {
        guard window != nil, !rehearsing else { return }
        (showScreen, presenterScreen) = (presenterScreen, showScreen)
        presenter.place(on: presenterScreen)
        place()
    }

    private func open(_ session: PlaySession) {
        let screens = NSScreen.screens
        guard let primary = NSScreen.main ?? screens.first else { return }
        // The show goes to the first screen that is not the one the app is on.
        // Falling back to that same screen covers a display leaving between the
        // start of the show and this pass: still a show, just nowhere to put the
        // presenter behind it. A rehearsal never leaves the screen the speaker
        // is on: what it puts there is a point, not a deck.
        showScreen = session.isRehearsal ? primary : (screens.first { $0 !== primary } ?? primary)
        presenterScreen = primary
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
