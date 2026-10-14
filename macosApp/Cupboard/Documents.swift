// The document lifecycle: File > New / Open / Open Recent / Save As / Rename / Move To,
// one window per deck, and the `.cupboard` file association Finder opens through.
// Everything that touches a bundle goes through Kotlin's `Documents`; this file
// is the panels, the menu items and the bookkeeping that maps a bundle path to
// the window showing it.
import SwiftUI
import AppKit
import UniformTypeIdentifiers
import CupboardCanvas

// MARK: - The type

extension UTType {
    /// Declared for real in Info.plist (UTExportedTypeDeclarations); this is the
    /// same identifier for the two panels to filter on. A package, so a deck is
    /// one thing to pick even though it is a folder underneath.
    static let cupboardDeck = UTType(exportedAs: "io.github.xxfast.cupboard.deck")
}

/// Which deck a window is on, as SwiftUI's window value.
///
/// Codable and Hashable because `WindowGroup(for:)` needs both: equal refs mean
/// one window (a second Open of a deck raises the one already showing it), and
/// the encoding is what state restoration puts back after a relaunch. A path
/// rather than a URL: it is what crosses to Kotlin, and a bookmark would be
/// promising sandbox access this app does not yet ask for.
struct DocumentRef: Codable, Hashable {
    let path: String

    init(path: String) {
        // One spelling per deck, so two ways in (a panel URL, a recents entry)
        // cannot key two windows onto the same bundle.
        self.path = URL(fileURLWithPath: path).standardizedFileURL.path
    }

    /// What a menu calls it: the bundle's name without `.cupboard`. The deck's
    /// own name is the host's to answer; this is for the paths no window is on.
    var name: String {
        URL(fileURLWithPath: path).deletingPathExtension().lastPathComponent
    }
}

// MARK: - Open windows

/// Which editor each window is on, so a window is opened rather than a document.
///
/// Two jobs. It memoizes: SwiftUI re-inits a window's root view freely, and every
/// init asking Kotlin for an editor would put several autosaving view models on
/// one bundle. And it parks: Open reads the deck before any window exists, so the
/// error can go in an alert instead of a blank window, and the host it produced
/// waits here for the window that is about to ask for it.
final class DocumentStore {
    static let shared = DocumentStore()

    private var models: [DocumentRef: EditorModel] = [:]
    private var parked: [DocumentRef: EditorHost] = [:]

    /// Whether a window is already on [ref], which is what makes a second Open
    /// of the same deck raise it rather than read the bundle again.
    func isOpen(_ ref: DocumentRef) -> Bool { models[ref] != nil }

    /// Whether any deck is open at all, which is what decides if a launch or a
    /// Dock click still needs the theme chooser.
    var isEmpty: Bool { models.isEmpty }

    /// The editor a window is showing [ref] in, if one is.
    func showing(_ ref: DocumentRef) -> EditorModel? { models[ref] }

    /// Leaves [host] for the window about to open on [ref].
    func park(_ host: EditorHost, as ref: DocumentRef) { parked[ref] = host }

    /// The one editor for [ref], made on the first ask and the same one after.
    /// Nil for a deck that will not read, which only a restored window can be
    /// on: every other way in reads the bundle before the window opens.
    func model(for ref: DocumentRef) -> EditorModel? {
        if let existing = models[ref] { return existing }
        guard let host = host(for: ref) else { return nil }
        let made = EditorModel(host: host)
        models[ref] = made
        return made
    }

    /// The window that was on [old] is on [new] now: Rename, Move To and Save
    /// As all leave a window showing another bundle. Without this the editor
    /// stays filed under a path it has left, and an Open of the deck it is
    /// really on would start a second one autosaving over it.
    func rekey(_ old: DocumentRef, to new: DocumentRef) {
        guard let model = models.removeValue(forKey: old) else { return }
        models[new] = model
    }

    /// Stops [ref]'s editor and forgets it, for a window that has gone. Autosave
    /// runs off the view model's scope, so an editor nobody is looking at is one
    /// still writing to disk.
    func release(_ ref: DocumentRef) {
        models.removeValue(forKey: ref)?.close()
    }

    /// The parked host if Open left one, otherwise the deck read now: a window
    /// restored after a relaunch was never opened through the menu.
    private func host(for ref: DocumentRef) -> EditorHost? {
        if let parked = parked.removeValue(forKey: ref) { return parked }
        return Documents.shared.open(path: ref.path).host
    }
}

// MARK: - Opening from Finder

/// Decks Finder or `open` asked for, and the window to open them with.
///
/// The app delegate hears about them and SwiftUI opens the windows, and the two
/// cannot talk directly: `openWindow` is an environment value, which a delegate
/// has no way to read. So the app hands its own here ([attach]), and the
/// delegate's paths wait for launching to finish before going through it: a
/// launch's files arrive before SwiftUI is ready to open anything.
final class OpenRequests {
    static let shared = OpenRequests()

    private var pending: [String] = []
    private var openWindow: OpenWindowAction?
    private var launched = false

    /// Whether this launch was on decks (Finder, `open`, `--showcase`) rather
    /// than bare, which is a launch with no chooser in it.
    private(set) var launchedOnDecks = false

    func request(_ paths: [String]) {
        guard !paths.isEmpty else { return }
        if !launched { launchedOnDecks = true }
        pending.append(contentsOf: paths)
        flush()
    }

    /// Called from the app's body, which runs again freely, so this only keeps
    /// the latest. The flush waits a turn: opening a window from inside a body
    /// evaluation is not something SwiftUI promises to survive.
    func attach(_ openWindow: OpenWindowAction) {
        self.openWindow = openWindow
        guard !pending.isEmpty else { return }
        DispatchQueue.main.async { self.flush() }
    }

    func finishLaunching() {
        launched = true
        DispatchQueue.main.async { self.flush() }
    }

    /// The theme chooser, raised if it is already up: it is a single window.
    func showChooser() {
        openWindow?(id: ThemeChooserView.windowId)
    }

    private func flush() {
        guard launched, let openWindow, !pending.isEmpty else { return }
        open(with: openWindow)
    }

    /// Every waiting deck in a window of its own, or the window already on it
    /// raised. Silent on a deck that will not read: this is usually a launch,
    /// and an alert from a window that is still coming up is a modal on nothing.
    func open(with openWindow: OpenWindowAction) {
        let paths = pending
        pending = []
        for path in paths {
            let ref = DocumentRef(path: path)
            if DocumentStore.shared.isOpen(ref) {
                openWindow(value: ref)
                continue
            }
            guard let host = Documents.shared.open(path: ref.path).host else { continue }
            DocumentStore.shared.park(host, as: ref)
            openWindow(value: ref)
        }
    }
}

// MARK: - The File menu

extension CupboardHostApp {
    /// New, Open and Open Recent where the system's New Window was, then Save As,
    /// Rename and Move To in the save group below it. Close is AppKit's own Cmd+W.
    var fileCommands: some Commands {
        Group {
            CommandGroup(replacing: .newItem) {
                Button("New") { OpenRequests.shared.showChooser() }
                    .keyboardShortcut("n", modifiers: .command)
                Button("Open...") { openDocument() }
                    .keyboardShortcut("o", modifiers: .command)
                // Built when the menu opens, so the list is what it is now
                // rather than what it was when the app launched.
                Menu("Open Recent") { recentItems }
            }
            CommandGroup(replacing: .saveItem) {
                // No plain Save: the editor autosaves, so the only save that
                // means anything is the one that picks a new file.
                Button("Save As...") { saveAs() }
                    .keyboardShortcut("s", modifiers: [.command, .shift])
                    .disabled(model == nil)
                Button("Rename...") { model?.renaming = true }
                    .disabled(model == nil)
                Button("Move To...") { moveTo() }
                    .disabled(model == nil)
            }
        }
    }

    /// The showcase is a document rather than a help page, so Open takes New's
    /// path rather than a window of its own kind.
    var helpCommands: some Commands {
        CommandGroup(replacing: .help) {
            Menu("Feature Showcase") {
                Button("Open") { openShowcase() }
                Button("Reset") { resetShowcase() }
            }
        }
    }

    /// The feature showcase in a window of its own. `showcaseDocument` hands
    /// back its one bundle and this opens it, the way `newDocument` works, so a
    /// second Open raises the window already on it.
    func openShowcase() {
        openDocument(at: Documents.shared.showcaseDocument())
    }

    /// The showcase back to how it shipped. A window already on it is stopped
    /// before the reset, since its autosave would write the old deck straight
    /// back, and then moved onto the fresh deck the way Save As moves one. With
    /// no window on it, it opens.
    func resetShowcase() {
        let ref = DocumentRef(path: Documents.shared.showcaseDocument())
        guard let showing = DocumentStore.shared.showing(ref) else {
            openDocument(at: Documents.shared.resetShowcase())
            return
        }
        showing.host.close()
        guard let host = Documents.shared.open(path: Documents.shared.resetShowcase()).host else { return }
        showing.adopt(host)
    }

    @ViewBuilder
    private var recentItems: some View {
        let recents = Documents.shared.recents()
        ForEach(recents, id: \.self) { path in
            Button(DocumentRef(path: path).name) { openDocument(at: path) }
        }
        if !recents.isEmpty {
            Divider()
            Button("Clear Menu") {
                for path in recents { Documents.shared.forgetRecent(path: path) }
            }
        }
    }

    /// A fresh `Untitled.cupboard` in Cupboard's own folder, opened like any
    /// other deck: making it and opening it are separate so there is one path
    /// into a window, not two. File > New goes through the theme chooser
    /// instead, which ends in `openDocument(at:)` the same way.
    ///
    /// doNewDocument is the exporter's doing: `new` is a reserved ObjC method
    /// family, so newDocument arrives here under that name.
    func newDocument() {
        openDocument(at: Documents.shared.doNewDocument())
    }

    /// A deck the user picks. Packages are files here, not folders to descend
    /// into: a `.cupboard` is one thing to choose even though it is a directory.
    func openDocument() {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = true
        panel.canChooseFiles = true
        panel.treatsFilePackagesAsDirectories = false
        panel.allowsMultipleSelection = false
        panel.allowedContentTypes = [.cupboardDeck]
        panel.prompt = "Open"

        guard panel.runModal() == .OK, let url = panel.url else { return }
        openDocument(at: url.path)
    }

    /// The deck at [path] in a window of its own, or an alert saying why not.
    ///
    /// The bundle is read here rather than inside the window, so a deck from a
    /// newer Cupboard says so in a dialog instead of opening an empty window.
    func openDocument(at path: String) {
        let ref = DocumentRef(path: path)
        // Already up: openWindow raises the window with this value, and reading
        // the bundle again would leave a second editor autosaving over the first.
        if DocumentStore.shared.isOpen(ref) {
            openWindow(value: ref)
            return
        }

        let outcome = Documents.shared.open(path: ref.path)
        guard let host = outcome.host else {
            presentOpenFailure(outcome.error, path: ref.path)
            return
        }
        DocumentStore.shared.park(host, as: ref)
        openWindow(value: ref)
    }

    /// The deck copied into a bundle the user picks, and this window moved onto
    /// it. The bundle it was on stays exactly as it is, which is what Save As
    /// means everywhere else.
    func saveAs() {
        guard let target = model else { return }
        let panel = NSSavePanel()
        panel.allowedContentTypes = [.cupboardDeck]
        panel.canCreateDirectories = true
        panel.nameFieldStringValue = target.host.title()
        panel.prompt = "Save"

        guard panel.runModal() == .OK, let url = panel.url else { return }
        target.adopt(Documents.shared.saveAs(host: target.host, path: url.path))
    }

    /// The bundle itself into a folder the user picks, under the name it has.
    /// The popover's Where from the menu bar, for a move with no pointer.
    func moveTo() {
        guard let target = model else { return }
        let bundle = URL(fileURLWithPath: target.host.location())
        guard let folder = chooseFolder(startingAt: bundle.deletingLastPathComponent()) else { return }
        let moved = folder.appendingPathComponent(bundle.lastPathComponent)
        guard let reason = target.move(to: moved.path) else { return }
        presentMoveFailure(reason, name: DocumentRef(path: bundle.path).name)
    }
}

/// [reason] is Kotlin's sentence, written for the person who picked the file.
private func presentOpenFailure(_ reason: String?, path: String) {
    let alert = NSAlert()
    alert.alertStyle = .warning
    alert.messageText = "Could not open \(DocumentRef(path: path).name)."
    alert.informativeText = reason ?? "The deck could not be read."
    alert.runModal()
}
