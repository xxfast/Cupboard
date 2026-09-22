// The document lifecycle: File > New / Open / Open Recent / Save As / Rename,
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
    /// The deck this machine opens by default, which is the window with no ref.
    private var fallback: EditorModel?

    /// Whether a window is already on [ref], which is what makes a second Open
    /// of the same deck raise it rather than read the bundle again.
    func isOpen(_ ref: DocumentRef) -> Bool { models[ref] != nil }

    /// Raises the default window when it is the one on [ref], and says whether
    /// it did.
    ///
    /// The default window has no ref, so `openWindow` has no value to raise it
    /// by, and a user who picks that bundle out of the Open panel would get a
    /// second editor autosaving over the first. The window is found through the
    /// canvas view it is showing, which is the only handle on it we have.
    func raiseDefault(_ ref: DocumentRef) -> Bool {
        guard let fallback, DocumentRef(path: fallback.host.location()) == ref else { return false }
        guard let window = fallback.host.view.window else { return false }
        window.makeKeyAndOrderFront(nil)
        NSApp.activate()
        return true
    }

    /// Leaves [host] for the window about to open on [ref].
    func park(_ host: EditorHost, as ref: DocumentRef) { parked[ref] = host }

    /// The one editor for [ref], made on the first ask and the same one after.
    func model(for ref: DocumentRef?) -> EditorModel {
        guard let ref else {
            if let fallback { return fallback }
            let made = EditorModel()
            fallback = made
            return made
        }
        if let existing = models[ref] { return existing }
        let made = EditorModel(host: host(for: ref))
        models[ref] = made
        return made
    }

    /// Stops [ref]'s editor and forgets it, for a window that has gone. Autosave
    /// runs off the view model's scope, so an editor nobody is looking at is one
    /// still writing to disk.
    func release(_ ref: DocumentRef?) {
        guard let ref else {
            fallback?.close()
            fallback = nil
            return
        }
        models.removeValue(forKey: ref)?.close()
    }

    /// The parked host if Open left one, otherwise the deck read now: a window
    /// restored after a relaunch was never opened through the menu. A deck that
    /// will not read falls back to the default one, because there is no alert to
    /// put in a window that is already coming up.
    private func host(for ref: DocumentRef) -> EditorHost {
        if let parked = parked.removeValue(forKey: ref) { return parked }
        return Documents.shared.open(path: ref.path).host ?? EditorHost()
    }
}

// MARK: - Opening from Finder

/// Decks Finder or `open` asked for, waiting for a window to put them in.
///
/// The app delegate hears about them and SwiftUI opens the windows, and the two
/// cannot talk directly: `openWindow` is an environment value, which a delegate
/// has no way to read. So the delegate leaves the paths here and the first
/// window's view drains them. Draining rather than reading is what stops every
/// open window from opening its own copy of the same file.
@Observable
final class OpenRequests {
    static let shared = OpenRequests()

    private(set) var pending: [String] = []

    func request(_ paths: [String]) { pending.append(contentsOf: paths) }

    func drain() -> [String] {
        defer { pending = [] }
        return pending
    }
}

// MARK: - The File menu

extension CupboardHostApp {
    /// New, Open and Open Recent where the system's New Window was, then Save As
    /// and Rename in the save group below it. Close is AppKit's own Cmd+W.
    var fileCommands: some Commands {
        Group {
            CommandGroup(replacing: .newItem) {
                Button("New") { newDocument() }
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
                Button("Rename...") { model.renaming = true }
            }
        }
    }

    /// One entry, and it opens a deck: the showcase is a document rather than a
    /// help page, so it takes New's path rather than a window of its own kind.
    var helpCommands: some Commands {
        CommandGroup(replacing: .help) {
            Button("Open Feature Showcase") { openShowcase() }
        }
    }

    /// The feature showcase in a window of its own. `showcaseDocument` lays the
    /// bundle down and this opens it, the way `newDocument` works.
    func openShowcase() {
        openDocument(at: Documents.shared.showcaseDocument())
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
    /// into a window, not two.
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
        if DocumentStore.shared.raiseDefault(ref) { return }

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
        let target = model
        let panel = NSSavePanel()
        panel.allowedContentTypes = [.cupboardDeck]
        panel.canCreateDirectories = true
        panel.nameFieldStringValue = target.host.title()
        panel.prompt = "Save"

        guard panel.runModal() == .OK, let url = panel.url else { return }
        target.adopt(Documents.shared.saveAs(host: target.host, path: url.path))
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
