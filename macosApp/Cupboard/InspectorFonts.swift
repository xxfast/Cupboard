import SwiftUI
import AppKit
import UniformTypeIdentifiers
import CupboardCanvas

// MARK: - Fonts

/// The font picker in the Text section and the deck's own fonts in the Document
/// panel. What a box is set in is a name; the deck carries files for the names
/// it wants to travel with, and everything else is whatever this Mac has.
extension EditorView {
    /// The picker, and under it the line that says when the face it names is
    /// nowhere to be had. Reads the selected words, else the primary, like the
    /// rest of the Text section's character controls; text in more than one face
    /// reads Multiple and ticks nothing.
    func fontPicker(_ text: TextFormat) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            PopUpButton(building: { fontEntries(selected: text.fontName ?? "") }) {
                popupFace(text.fontName ?? Inspect.multiple)
            }

            if let fallback = text.missingFontFallback {
                Text("Not installed. Showing \(fallback).")
                    .font(.system(size: Inspect.caption))
                    .foregroundStyle(palette.inspectorCaption)
                    .lineLimit(2)
            }
        }
    }

    /// The deck's families, the three generic ones, every installed family, and
    /// the way to add one. Built on the click: a few hundred rows is nothing to
    /// a menu and too much to remake on every pass of the panel. Each in the
    /// system face, so type-ahead select works the way it does in any popup.
    func fontEntries(selected: String) -> [MenuEntry] {
        func pick(_ title: String, _ action: @escaping () -> Void) -> MenuEntry {
            MenuEntry(title: title, checked: title == selected, action: action)
        }

        var entries: [MenuEntry] = []
        let deck = host.deckFontFamilies()
        if !deck.isEmpty {
            entries.append(MenuEntry(title: "In This Deck", enabled: false))
            entries += deck.map { name in pick(name) { host.setSelectedTextFontName(name: name) } }
            entries.append(.separator())
        }

        entries += zip(Fonts.generic, Fonts.genericTitles).map { font, title in
            pick(title) { host.setSelectedTextGenericFont(font: font) }
        }
        entries.append(.separator())
        entries += Fonts.installed.map { name in pick(name) { host.setSelectedTextFontName(name: name) } }
        entries.append(.separator())
        entries.append(MenuEntry(title: "Add Font File...") { Fonts.add(to: host) })
        return entries
    }

    /// The faces the deck carries in its bundle, one row per cut, and the button
    /// that adds more. Removing one leaves its text alone, set in a name that now
    /// falls back like any face that isn't there.
    func deckFontsSection(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Fonts")

            if ui.deckFonts.isEmpty {
                Text("Fonts added here travel with the deck.")
                    .font(.system(size: Inspect.caption))
                    .foregroundStyle(palette.inspectorCaption)
            } else {
                VStack(spacing: 5) {
                    ForEach(ui.deckFonts) { font in deckFontRow(font) }
                }
            }

            panelButton("Add Font File...") { Fonts.add(to: host) }
        }
    }

    /// One cut: its family, weight and slant, and the button that drops it.
    /// The text link row's shape, so the panel's lists read as one kind of thing.
    func deckFontRow(_ font: DeckFontChoice) -> some View {
        let shape = RoundedRectangle(cornerRadius: 6, style: .continuous)
        return HStack(spacing: 8) {
            Text(font.title)
                .font(.system(size: 12))
                .foregroundStyle(palette.text)
                .lineLimit(1)
                .truncationMode(.middle)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                host.removeDeckFont(assetId: font.id)
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 13))
                    .foregroundStyle(palette.inspectorCaption)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .help("Remove Font")
        }
        .padding(EdgeInsets(top: 6, leading: 9, bottom: 6, trailing: 9))
        .background(palette.rowBg, in: shape)
    }
}

/// One of the deck's font cuts as the Document panel lists it: the asset id that
/// removes it, and the row's words, which Kotlin wrote.
struct DeckFontChoice: Identifiable, Equatable {
    let id: String
    let title: String

    static func all(_ host: EditorHost) -> [DeckFontChoice] {
        host.deckFonts().map { DeckFontChoice(id: $0.assetId, title: $0.title) }
    }
}

enum Fonts {
    /// The generic families, in the order the picker lists them.
    static let generic: [TextFont] = [TextFont.sans, TextFont.serif, TextFont.monospace]
    static let genericTitles = ["Sans", "Serif", "Monospace"]

    /// Every family this Mac has, once, sorted the way Font Book sorts them. The
    /// hidden system faces (a leading dot) are not for picking. Read once: a font
    /// installed while the app is up shows after a relaunch, as in most apps.
    static let installed: [String] = NSFontManager.shared.availableFontFamilies
        .filter { !$0.hasPrefix(".") }
        .sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }

    /// The font files the user picks, into the deck. Kotlin reads each for its
    /// family off the main thread; any it can't read are named in one alert once
    /// they have all answered.
    static func add(to host: EditorHost) {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = false
        panel.canChooseFiles = true
        panel.allowsMultipleSelection = true
        panel.allowedContentTypes = ["ttf", "otf", "ttc"].compactMap { UTType(filenameExtension: $0) }
        panel.prompt = "Add"

        guard panel.runModal() == .OK else { return }
        let urls = panel.urls
        guard !urls.isEmpty else { return }

        var pending = urls.count
        var failed: [String] = []
        func answered(_ name: String, _ added: Bool) {
            if !added { failed.append(name) }
            pending -= 1
            if pending == 0, !failed.isEmpty { unreadable(failed) }
        }

        for url in urls {
            let name = url.lastPathComponent
            guard let bytes = try? Data(contentsOf: url) else {
                answered(name, false)
                continue
            }
            host.addDeckFont(bytes: bytes, extension: url.pathExtension) { added in
                answered(name, added.boolValue)
            }
        }
    }

    private static func unreadable(_ names: [String]) {
        let alert = NSAlert()
        alert.messageText = names.count == 1
            ? "Couldn't read \(names[0]) as a font."
            : "Couldn't read \(names.joined(separator: ", ")) as fonts."
        alert.informativeText = "Cupboard reads TrueType and OpenType files (.ttf, .otf, .ttc)."
        alert.addButton(withTitle: "OK")
        alert.runModal()
    }
}
