// The deck's name in the toolbar, and the popover under it: Name, Tags and
// Where, which is what a document window's title does on macOS. That one comes
// free with NSDocument and a title bar; this app has neither, so it is drawn
// here, with the system's own controls inside so it reads as the same thing.
import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - The title

/// Where a title bar would be, since there isn't one: the deck's name, and under
/// it whether the last few edits have made it to disk yet. The tag is a
/// fixed-height row rather than one that comes and goes, so the name does not
/// shift up and down as the autosave lands.
///
/// Under the pointer it grows the document's icon and a chevron, the way a
/// title bar's does, and a click opens the popover. The room the icon slides
/// the name into is reserved up front: the toolbar centres its cluster in what
/// is left over, and a name that changed width on hover would nudge it.
struct DocumentTitle: View {
    let model: EditorModel
    let ui: Chrome
    let palette: Palette
    @State private var hovering = false

    private static let iconRun: CGFloat = 20

    var body: some View {
        @Bindable var window = model
        let showing = hovering || model.renaming
        // The name is the one thing in the bar that can give: it truncates
        // rather than fix its width, so the toolbar never outgrows the window.
        return Button { model.renaming = true } label: {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 5) {
                    Text(ui.documentTitle)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(palette.title)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    Image(systemName: "chevron.down")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundStyle(palette.dim)
                        .opacity(showing ? 1 : 0)
                }
                Text("Edited")
                    .font(.system(size: 11))
                    .foregroundStyle(palette.faint)
                    .opacity(ui.edited ? 1 : 0)
            }
            .offset(x: showing ? Self.iconRun : 0)
            .padding(.trailing, Self.iconRun)
            .overlay(alignment: .topLeading) {
                Image(nsImage: NSWorkspace.shared.icon(forFile: model.host.location()))
                    .resizable()
                    .frame(width: 16, height: 16)
                    .opacity(showing ? 1 : 0)
            }
            .contentShape(Rectangle())
            .animation(.easeOut(duration: 0.15), value: showing)
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .contextMenu { pathMenu }
        .popover(isPresented: $window.renaming, arrowEdge: .bottom) {
            DocumentInfo(model: model)
        }
    }

    /// A title bar's right-click: the deck, then every folder it is inside, out
    /// to the machine. Picking a row shows it in Finder.
    @ViewBuilder
    private var pathMenu: some View {
        // Built when the menu opens, off where the bundle is by then.
        let bundle = URL(fileURLWithPath: model.host.location()).standardizedFileURL
        let folders = Array(sequence(first: bundle.deletingLastPathComponent()) { folder in
            folder.path == "/" ? nil : folder.deletingLastPathComponent()
        })
        pathRow(FileManager.default.displayName(atPath: bundle.path), NSWorkspace.shared.icon(forFile: bundle.path)) {
            NSWorkspace.shared.activateFileViewerSelecting([bundle])
        }
        ForEach(folders, id: \.path) { folder in
            pathRow(FileManager.default.displayName(atPath: folder.path), NSWorkspace.shared.icon(forFile: folder.path)) {
                NSWorkspace.shared.open(folder)
            }
        }
        // The machine is not a folder to open. Showing the startup volume is
        // what lands Finder on its Computer window.
        pathRow(Host.current().localizedName ?? "Computer", NSImage(named: NSImage.computerName) ?? NSImage()) {
            NSWorkspace.shared.activateFileViewerSelecting([URL(fileURLWithPath: "/")])
        }
    }

    private func pathRow(_ title: String, _ icon: NSImage, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label {
                Text(title)
            } icon: {
                Image(nsImage: menuIcon(icon))
            }
        }
    }
}

/// [icon] at a menu row's size. A copy: the workspace hands out shared images,
/// and sizing one in place would resize it for everyone else holding it.
func menuIcon(_ icon: NSImage) -> NSImage {
    let sized = icon.copy() as? NSImage ?? icon
    sized.size = NSSize(width: 16, height: 16)
    return sized
}

// MARK: - The popover

/// Name, Tags and Where for the deck this window is on.
///
/// Name commits on return and on a click away, and escape leaves it alone,
/// which is the title bar popover's own rule. Tags and Where act as they are
/// changed. A new name or folder is a move of the bundle, so the window ends up
/// on a new editor the way Save As leaves it; see `EditorModel.move(to:)`.
struct DocumentInfo: View {
    let model: EditorModel
    @State private var name = ""
    @State private var tags: [String] = []
    /// Escape, as against a click away. Both only reach here as the popover
    /// going, so the key is watched for itself.
    @State private var cancelled = false
    @State private var escape: Any?
    @FocusState private var nameFocused: Bool
    @Environment(\.dismiss) private var dismiss

    private static let field: CGFloat = 229

    private var bundle: URL { URL(fileURLWithPath: model.host.location()) }

    var body: some View {
        // The bundle moves under this view while it is up.
        let _ = model.generation
        return Grid(alignment: .trailing, horizontalSpacing: 7, verticalSpacing: 9) {
            GridRow {
                Text("Name:")
                TextField("", text: $name)
                    .textFieldStyle(.roundedBorder)
                    .focused($nameFocused)
                    .onSubmit {
                        rename()
                        dismiss()
                    }
                    .frame(width: Self.field)
            }
            GridRow {
                Text("Tags:")
                TagField(tags: $tags, suggestions: FinderTags.suggestions) { retag() }
                    .frame(width: Self.field)
            }
            GridRow {
                Text("Where:")
                FolderPopup(
                    folder: bundle.deletingLastPathComponent(),
                    recent: recentFolders
                ) { folder in
                    move(to: folder)
                }
                .frame(width: Self.field)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 18)
        .padding(.bottom, 14)
        .onAppear {
            name = DocumentRef(path: bundle.path).name
            tags = FinderTags.read(bundle)
            nameFocused = true
            // The whole name selected, so typing replaces it. Once the field
            // has the focus, which is not until the popover is on screen.
            DispatchQueue.main.async {
                NSApp.sendAction(#selector(NSText.selectAll(_:)), to: nil, from: nil)
            }
            escape = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
                if event.keyCode == 53 { cancelled = true }
                return event
            }
        }
        .onDisappear {
            if let escape { NSEvent.removeMonitor(escape) }
            // Before the name: tags are on the bundle where it is now.
            retag()
            if !cancelled { rename() }
        }
    }

    /// The folders the recent decks are in, for the popup's Recent Places.
    private var recentFolders: [URL] {
        var seen: Set<String> = []
        return Documents.shared.recents()
            .map { URL(fileURLWithPath: $0).deletingLastPathComponent().standardizedFileURL }
            .filter { seen.insert($0.path).inserted }
    }

    private func rename() {
        let current = DocumentRef(path: bundle.path).name
        let typed = name.trimmingCharacters(in: .whitespaces)
        guard !typed.isEmpty, typed != current else { return }
        // A slash would be a folder and a leading dot a hidden file, neither of
        // which is a name Finder takes either.
        guard !typed.contains("/"), !typed.hasPrefix(".") else {
            NSSound.beep()
            name = current
            return
        }
        let target = bundle.deletingLastPathComponent()
            .appendingPathComponent("\(typed).\(bundle.pathExtension)")
        guard let reason = model.move(to: target.path) else { return }
        name = current
        presentMoveFailure(reason, name: current)
    }

    private func move(to folder: URL) {
        let target = folder.appendingPathComponent(bundle.lastPathComponent)
        guard let reason = model.move(to: target.path) else { return }
        presentMoveFailure(reason, name: DocumentRef(path: bundle.path).name)
    }

    private func retag() {
        guard tags != FinderTags.read(bundle) else { return }
        FinderTags.write(tags, to: bundle)
    }
}

// MARK: - Tags

/// Finder's tags on a file. They are the file's, not the deck's: they live in
/// its extended attributes, so nothing of them crosses to Kotlin or travels
/// inside the bundle.
enum FinderTags {
    static func read(_ url: URL) -> [String] {
        (try? url.resourceValues(forKeys: [.tagNamesKey]).tagNames) ?? []
    }

    /// Through NSURL: the Swift overlay's `tagNames` is read-only.
    static func write(_ tags: [String], to url: URL) {
        try? (url as NSURL).setResourceValue(tags, forKey: .tagNamesKey)
    }

    /// The colour labels, by the names this machine gives them. The first is
    /// "None", which is no tag at all.
    static var suggestions: [String] { Array(NSWorkspace.shared.fileLabels.dropFirst()) }
}

/// A token field over [tags]. AppKit's, because SwiftUI has none: typed text
/// becomes a token on return or a comma, and backspace takes one off.
struct TagField: NSViewRepresentable {
    @Binding var tags: [String]
    let suggestions: [String]
    /// The tokens changed, as against text still being typed.
    let onChange: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeNSView(context: Context) -> NSTokenField {
        let field = NSTokenField()
        field.tokenStyle = .rounded
        field.delegate = context.coordinator
        field.objectValue = tags
        // One line: tags past the edge scroll rather than grow the popover.
        field.cell?.wraps = false
        field.cell?.isScrollable = true
        return field
    }

    func updateNSView(_ field: NSTokenField, context: Context) {
        context.coordinator.parent = self
        // Never under the caret: the field's own text is ahead of the binding
        // while it is being typed in.
        guard field.currentEditor() == nil else { return }
        if (field.objectValue as? [String]) != tags { field.objectValue = tags }
    }

    final class Coordinator: NSObject, NSTokenFieldDelegate {
        var parent: TagField

        init(_ parent: TagField) { self.parent = parent }

        func tokenField(
            _ tokenField: NSTokenField,
            completionsForSubstring substring: String,
            indexOfToken tokenIndex: Int,
            indexOfSelectedItem selectedIndex: UnsafeMutablePointer<Int>?
        ) -> [Any]? {
            parent.suggestions.filter { $0.lowercased().hasPrefix(substring.lowercased()) }
        }

        func tokenField(_ tokenField: NSTokenField, shouldAdd tokens: [Any], at index: Int) -> [Any] {
            // The field has not taken them yet, so read it on the next pass.
            DispatchQueue.main.async { [weak self] in self?.settle(tokenField) }
            return tokens
        }

        func controlTextDidEndEditing(_ notification: Notification) {
            guard let field = notification.object as? NSTokenField else { return }
            settle(field)
        }

        private func settle(_ field: NSTokenField) {
            let typed = (field.objectValue as? [String]) ?? []
            var seen: Set<String> = []
            parent.tags = typed
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty && seen.insert($0).inserted }
            parent.onChange()
        }
    }
}

// MARK: - Where

/// The popup of folders the deck could be in: where it is and everything above
/// it, the usual places, wherever the recent decks are, and Other for anywhere
/// else. Picking one is the move.
///
/// An NSPopUpButton rather than a SwiftUI menu, for the folder icons in the
/// button's own face and a tick on the row it is showing.
struct FolderPopup: NSViewRepresentable {
    let folder: URL
    let recent: [URL]
    let onPick: (URL) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeNSView(context: Context) -> NSPopUpButton {
        let button = NSPopUpButton(frame: .zero, pullsDown: false)
        button.target = context.coordinator
        button.action = #selector(Coordinator.picked(_:))
        button.lineBreakMode = .byTruncatingMiddle
        return button
    }

    func updateNSView(_ button: NSPopUpButton, context: Context) {
        context.coordinator.parent = self
        let menu = NSMenu()
        menu.autoenablesItems = false

        // Where it is, then each folder above it up to the volume.
        var listed: Set<String> = []
        var level = folder.standardizedFileURL
        while listed.insert(level.path).inserted {
            menu.addItem(item(for: level))
            level = level.deletingLastPathComponent()
        }

        section("Favourites", of: Self.places, into: menu, skipping: &listed)
        section("Recent Places", of: recent, into: menu, skipping: &listed)

        menu.addItem(.separator())
        menu.addItem(NSMenuItem(title: "Other...", action: nil, keyEquivalent: ""))

        button.menu = menu
        button.selectItem(at: 0)
    }

    /// The folders a new account's Finder sidebar starts with. The sidebar as
    /// the user has it now is not something an app can read.
    private static var places: [URL] {
        let kinds: [FileManager.SearchPathDirectory] = [
            .desktopDirectory, .documentDirectory, .downloadsDirectory, .moviesDirectory, .musicDirectory,
        ]
        return kinds.compactMap { FileManager.default.urls(for: $0, in: .userDomainMask).first }
    }

    private func section(_ title: String, of folders: [URL], into menu: NSMenu, skipping listed: inout Set<String>) {
        let fresh = folders
            .map(\.standardizedFileURL)
            .filter { FileManager.default.fileExists(atPath: $0.path) && listed.insert($0.path).inserted }
        guard !fresh.isEmpty else { return }
        menu.addItem(.separator())
        menu.addItem(.sectionHeader(title: title))
        for folder in fresh { menu.addItem(item(for: folder)) }
    }

    private func item(for folder: URL) -> NSMenuItem {
        let item = NSMenuItem(
            title: FileManager.default.displayName(atPath: folder.path),
            action: nil,
            keyEquivalent: ""
        )
        item.image = menuIcon(NSWorkspace.shared.icon(forFile: folder.path))
        item.representedObject = folder
        return item
    }

    final class Coordinator: NSObject {
        var parent: FolderPopup

        init(_ parent: FolderPopup) { self.parent = parent }

        @objc func picked(_ sender: NSPopUpButton) {
            let picked = sender.selectedItem?.representedObject as? URL
            // Back on where the deck is until the move says otherwise: a move
            // that fails, or an Other that is cancelled, leaves the popup true.
            sender.selectItem(at: 0)
            guard let folder = picked ?? chooseFolder(startingAt: parent.folder) else { return }
            guard folder.standardizedFileURL != parent.folder.standardizedFileURL else { return }
            parent.onPick(folder)
        }
    }
}

/// A folder to move a deck into, or nil if the panel was dismissed.
func chooseFolder(startingAt folder: URL) -> URL? {
    let panel = NSOpenPanel()
    panel.canChooseDirectories = true
    panel.canChooseFiles = false
    panel.canCreateDirectories = true
    panel.allowsMultipleSelection = false
    panel.directoryURL = folder
    panel.prompt = "Move"

    guard panel.runModal() == .OK else { return nil }
    return panel.url
}

/// [reason] is Kotlin's sentence. On the next pass: this is reached from a
/// popover on its way out, and a modal run from inside that is one on nothing.
func presentMoveFailure(_ reason: String, name: String) {
    DispatchQueue.main.async {
        let alert = NSAlert()
        alert.alertStyle = .warning
        alert.messageText = "Could not move \(name)."
        alert.informativeText = reason
        alert.runModal()
    }
}
