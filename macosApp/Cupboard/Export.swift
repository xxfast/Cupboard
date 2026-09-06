import SwiftUI
import AppKit
import PDFKit
import UniformTypeIdentifiers
import CupboardCanvas

// MARK: - The File menu's export half

extension CupboardHostApp {
    /// Every way the deck leaves the app, plus Print.
    ///
    /// One `Commands` rather than two because the builder up in `HostApp` is
    /// full: ten children is its limit, and a `Group` is what folds these into
    /// one of them, exactly as `fileCommands` does with its own pair.
    var exportCommands: some Commands {
        Group {
            // Under one submenu, the way Keynote puts them. No key equivalents:
            // exporting is a deliberate act, not something to hit on the way to
            // Cmd+S. The CuP project is last and behind a divider, being the odd
            // one out: it writes a Gradle build rather than a document.
            CommandGroup(replacing: .importExport) {
                Menu("Export") {
                    Button("PDF...") { exportDeckPdf(host: model.host) }
                    Button("Images (PNG)...") { exportDeckImages(host: model.host) }
                    Button("Animated GIF...") { exportDeckGif(host: model.host) }
                    Button("HTML Player...") { exportDeckHtml(host: model.host) }
                    Button("PowerPoint (PPTX)...") { exportDeckPptx(host: model.host) }
                    Divider()
                    Button("CuP Project...") { exportCupProject(host: model.host) }
                }
            }
            // Cmd+P, where every Mac app has it. Printing is a PDF export with
            // the system print panel pointed at it; see printDeck.
            CommandGroup(replacing: .printItem) {
                Button("Print...") { printDeck(host: model.host) }
                    .keyboardShortcut("p", modifiers: .command)
            }
        }
    }
}

// MARK: - Export

/// File > Export as CuP Project: the deck as a standalone Gradle project the
/// user can open and run. Kotlin generates the files, this writes them.
///
/// The chosen directory is where the project lands, not the project itself: a
/// folder named after the deck goes inside it, the way Xcode and Keynote both
/// export. Finder opens on it afterwards, which is the only confirmation an
/// export of files needs.
func exportCupProject(host: EditorHost) {
    let panel = NSOpenPanel()
    panel.canChooseDirectories = true
    panel.canChooseFiles = false
    panel.canCreateDirectories = true
    panel.allowsMultipleSelection = false
    panel.prompt = "Export"
    panel.message = "Choose where to put the exported project."

    guard panel.runModal() == .OK, let directory = panel.url else { return }
    let root = directory.appendingPathComponent(host.cupProjectName(), isDirectory: true)

    do {
        try writeProject(host.cupProject(), into: root)
    } catch {
        presentExportFailure(error)
        return
    }
    NSWorkspace.shared.activateFileViewerSelecting([root])
}

/// Writes every generated file under [root], making the directories on the way
/// down. Overwrites: an export is the document as it stands right now, so a
/// second one onto the same folder should say what the first would have.
private func writeProject(_ files: [ExportFile], into root: URL) throws {
    let manager = FileManager.default
    try manager.createDirectory(at: root, withIntermediateDirectories: true)
    for file in files {
        let url = root.appendingPathComponent(file.path)
        try manager.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try file.contents.write(to: url, atomically: true, encoding: .utf8)
    }
}

/// A half-written project is still on disk when this shows, deliberately: the
/// user picked the folder, so what did get there is theirs to look at.
private func presentExportFailure(_ error: Error) {
    presentExportFailure(error, saying: "Could not export the project.")
}

private func presentExportFailure(_ error: Error, saying message: String) {
    let alert = NSAlert()
    alert.alertStyle = .warning
    alert.messageText = message
    alert.informativeText = error.localizedDescription
    alert.runModal()
}

// MARK: - The rendered formats

// PDF, PNG, GIF, HTML and PPTX all come out of the same Kotlin exporter and all
// cost the same thing: one Compose composition and one Skia draw per frame, on a
// deck that may be fifty slides deep. So they share a shape. Pick the options in
// the save panel's own accessory area (one dialog, the way Preview and Keynote
// ask), run the export on a background queue behind a progress sheet, and show
// the file in Finder when it lands.

/// File > Export > PDF: the deck as a handout, laid out however the panel says.
func exportDeckPdf(host: EditorHost) {
    let exporter = DeckExporter(editor: host)
    let options = PdfOptionsView(layouts: exporter.handoutLayoutTitles())
    guard let url = savePanelUrl(
        host: host,
        type: .pdf,
        extension: "pdf",
        accessory: options
    ) else { return }

    let layout = Int32(options.layout.indexOfSelectedItem)
    let everyBuild = options.everyBuild.state == .on
    let skipped = options.skipped.state == .on
    let notes = options.notes.state == .on

    runExport(title: "Exporting PDF", exporter: exporter, revealing: url) {
        let data = exporter.pdf(
            layoutIndex: layout,
            everyBuild: everyBuild,
            includeSkipped: skipped,
            includeNotes: notes
        )
        try (data as Data).write(to: url)
    }
}

/// File > Export > Images: one PNG per slide, in a folder named after the deck.
///
/// A directory rather than a file, because the export is many files. The folder
/// goes inside whatever was picked, the way the CuP project export does: a dozen
/// loose `slide-01.png` in someone's Documents is not what was asked for.
func exportDeckImages(host: EditorHost) {
    let exporter = DeckExporter(editor: host)
    let options = ImageOptionsView()

    let panel = NSOpenPanel()
    panel.canChooseDirectories = true
    panel.canChooseFiles = false
    panel.canCreateDirectories = true
    panel.allowsMultipleSelection = false
    panel.prompt = "Export"
    panel.message = "Choose where to put the exported images."
    panel.accessoryView = options

    guard panel.runModal() == .OK, let directory = panel.url else { return }
    let root = directory.appendingPathComponent(host.cupProjectName(), isDirectory: true)
    let everyBuild = options.everyBuild.state == .on

    runExport(title: "Exporting images", exporter: exporter, revealing: root) {
        let files = exporter.pngs(everyBuild: everyBuild)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        for file in files {
            try (file.data as Data).write(to: root.appendingPathComponent(file.name))
        }
    }
}

/// File > Export > Animated GIF: the deck as one looping animation, a frame per
/// build step.
func exportDeckGif(host: EditorHost) {
    let exporter = DeckExporter(editor: host)
    let options = GifOptionsView()
    guard let url = savePanelUrl(
        host: host,
        type: .gif,
        extension: "gif",
        accessory: options
    ) else { return }

    let width = options.selectedWidth
    let seconds = options.selectedSeconds
    let loop = options.loop.state == .on

    runExport(title: "Exporting GIF", exporter: exporter, revealing: url) {
        let data = exporter.gif(width: width, secondsPerFrame: seconds, loop: loop)
        try (data as Data).write(to: url)
    }
}

/// File > Export > HTML Player: one self-contained page, frames and notes in it.
func exportDeckHtml(host: EditorHost) {
    let exporter = DeckExporter(editor: host)
    guard let url = savePanelUrl(
        host: host,
        type: .html,
        extension: "html",
        accessory: nil
    ) else { return }

    runExport(title: "Exporting HTML player", exporter: exporter, revealing: url) {
        try exporter.html().write(to: url, atomically: true, encoding: .utf8)
    }
}

/// File > Export > PowerPoint: a .pptx of picture-backed slides, notes carried.
func exportDeckPptx(host: EditorHost) {
    let exporter = DeckExporter(editor: host)
    guard let url = savePanelUrl(
        host: host,
        type: UTType(filenameExtension: "pptx") ?? .data,
        extension: "pptx",
        accessory: nil
    ) else { return }

    runExport(title: "Exporting PowerPoint", exporter: exporter, revealing: url) {
        let data = exporter.pptx()
        try (data as Data).write(to: url)
    }
}

// MARK: - Print

/// File > Print: the deck one slide to a page, through the system print panel.
///
/// A PDF is the whole of it. The slide canvas is Compose and there is no AppKit
/// view of a deck to hand `NSPrintOperation`, but there is already an exporter
/// that writes the pages a printer wants, so printing is exporting to a temp file
/// and letting PDFKit drive the panel over it. Preview is the fallback for the
/// day PDFKit declines: it can print, and the file is on disk either way.
func printDeck(host: EditorHost) {
    let exporter = DeckExporter(editor: host)
    let url = FileManager.default.temporaryDirectory
        .appendingPathComponent("\(host.cupProjectName()).pdf")

    runExport(title: "Preparing to print", exporter: exporter, then: { present(pdf: url) }) {
        let data = exporter.pdf(
            layoutIndex: 0,
            everyBuild: false,
            includeSkipped: false,
            includeNotes: false
        )
        try (data as Data).write(to: url)
    }
}

private func present(pdf url: URL) {
    guard
        let document = PDFDocument(url: url),
        let operation = document.printOperation(
            for: NSPrintInfo.shared,
            scalingMode: .pageScaleDownToFit,
            autoRotate: true
        )
    else {
        NSWorkspace.shared.open(url)
        return
    }
    operation.showsPrintPanel = true
    operation.run()
}

// MARK: - Running one

/// Runs [work] off the main thread behind a progress sheet, then shows the
/// result in Finder.
private func runExport(
    title: String,
    exporter: DeckExporter,
    revealing url: URL,
    work: @escaping () throws -> Void
) {
    runExport(
        title: title,
        exporter: exporter,
        then: { NSWorkspace.shared.activateFileViewerSelecting([url]) },
        work: work
    )
}

/// The same, with what happens afterwards left to the caller.
///
/// [work] is the export: minutes of Skia on a big deck, so it never runs on the
/// main thread. The exporter counts frames back through [DeckExporter.progress]
/// from whichever thread it is on, which is why every touch of the sheet hops to
/// the main queue. [finish] runs there too, once the sheet is down: a print panel
/// or a Finder window is not something to open behind a modal sheet.
private func runExport(
    title: String,
    exporter: DeckExporter,
    then finish: @escaping () -> Void,
    work: @escaping () throws -> Void
) {
    let sheet = ExportProgressSheet(title: title)
    sheet.begin()
    exporter.progress = { done, total in
        DispatchQueue.main.async { sheet.advance(done: done.intValue, of: total.intValue) }
    }

    DispatchQueue.global(qos: .userInitiated).async {
        var failure: Error?
        do { try work() } catch { failure = error }

        DispatchQueue.main.async {
            // Dropped before the sheet comes down, so nothing arriving late can
            // touch a window that is already closing.
            exporter.progress = { _, _ in }
            sheet.end()
            if let failure {
                presentExportFailure(failure, saying: "Could not finish the export.")
                return
            }
            finish()
        }
    }
}

/// Where an export of one file goes, or nil if the panel was cancelled. The deck
/// names the file, and [accessory] is whatever that format asks about.
private func savePanelUrl(
    host: EditorHost,
    type: UTType,
    extension suffix: String,
    accessory: NSView?
) -> URL? {
    let panel = NSSavePanel()
    panel.allowedContentTypes = [type]
    panel.nameFieldStringValue = "\(host.cupProjectName()).\(suffix)"
    panel.canCreateDirectories = true
    panel.prompt = "Export"
    if let accessory {
        panel.accessoryView = accessory
    }
    guard panel.runModal() == .OK else { return nil }
    return panel.url
}

/// The sheet an export runs behind: what is being made, and how far along it is.
///
/// Indeterminate until the first frame lands, because how many frames there are
/// is something the exporter says rather than something the shell knows: a deck
/// walked build by build has more frames than it has slides. Modal, deliberately:
/// the export reads the document as it stands, so editing it mid-export would be
/// editing something already half rendered.
private final class ExportProgressSheet {
    private let window: NSWindow
    private let bar = NSProgressIndicator()
    private let caption: NSTextField
    private let detail = NSTextField(labelWithString: "Rendering slides...")
    private weak var parent: NSWindow?

    init(title: String) {
        caption = NSTextField(labelWithString: "\(title)...")
        caption.font = .systemFont(ofSize: NSFont.systemFontSize, weight: .semibold)
        detail.font = .systemFont(ofSize: NSFont.smallSystemFontSize)
        detail.textColor = .secondaryLabelColor

        bar.style = .bar
        bar.isIndeterminate = true
        bar.minValue = 0

        let rows = NSStackView(views: [caption, bar, detail])
        rows.orientation = .vertical
        rows.alignment = .leading
        rows.spacing = 10
        rows.edgeInsets = NSEdgeInsets(top: 22, left: 24, bottom: 22, right: 24)
        rows.translatesAutoresizingMaskIntoConstraints = false

        let content = NSView()
        content.addSubview(rows)
        NSLayoutConstraint.activate([
            rows.topAnchor.constraint(equalTo: content.topAnchor),
            rows.bottomAnchor.constraint(equalTo: content.bottomAnchor),
            rows.leadingAnchor.constraint(equalTo: content.leadingAnchor),
            rows.trailingAnchor.constraint(equalTo: content.trailingAnchor),
            bar.widthAnchor.constraint(equalToConstant: 280),
        ])

        window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 328, height: 116),
            styleMask: [.titled],
            backing: .buffered,
            defer: false
        )
        window.contentView = content
    }

    func begin() {
        guard let parent = NSApp.keyWindow else { return }
        self.parent = parent
        bar.startAnimation(nil)
        parent.beginSheet(window)
    }

    func advance(done: Int, of total: Int) {
        guard total > 0 else { return }
        if bar.isIndeterminate {
            bar.stopAnimation(nil)
            bar.isIndeterminate = false
            bar.maxValue = Double(total)
        }
        bar.doubleValue = Double(done)
        detail.stringValue = "Slide \(done) of \(total)"
    }

    func end() {
        bar.stopAnimation(nil)
        guard let parent else { return }
        parent.endSheet(window)
        window.orderOut(nil)
        self.parent = nil
    }
}

// MARK: - Options

/// The shared shape of a save panel's accessory: labelled rows, stacked, sized by
/// what is in them. Every options view below is one of these plus its controls.
private class ExportOptionsView: NSView {
    /// Call once, with the rows in the order they read.
    func install(_ views: [NSView]) {
        let rows = NSStackView(views: views)
        rows.orientation = .vertical
        rows.alignment = .leading
        rows.spacing = 8
        rows.edgeInsets = NSEdgeInsets(top: 12, left: 20, bottom: 14, right: 20)
        rows.translatesAutoresizingMaskIntoConstraints = false
        addSubview(rows)
        NSLayoutConstraint.activate([
            rows.topAnchor.constraint(equalTo: topAnchor),
            rows.bottomAnchor.constraint(equalTo: bottomAnchor),
            rows.leadingAnchor.constraint(equalTo: leadingAnchor),
            rows.trailingAnchor.constraint(equalTo: trailingAnchor),
        ])
    }

    /// One control with its name to the left of it, the way a panel reads.
    func labelled(_ title: String, _ control: NSView) -> NSView {
        let label = NSTextField(labelWithString: title)
        let row = NSStackView(views: [label, control])
        row.orientation = .horizontal
        row.spacing = 8
        return row
    }
}

private final class PdfOptionsView: ExportOptionsView {
    let layout = NSPopUpButton(frame: .zero, pullsDown: false)
    let everyBuild = NSButton(checkboxWithTitle: "A page per build stage", target: nil, action: nil)
    let skipped = NSButton(checkboxWithTitle: "Include skipped slides", target: nil, action: nil)
    let notes = NSButton(checkboxWithTitle: "Include speaker notes", target: nil, action: nil)

    init(layouts: [String]) {
        super.init(frame: .zero)
        layout.addItems(withTitles: layouts)
        install([labelled("Layout:", layout), everyBuild, skipped, notes])
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("not loaded from a nib") }
}

private final class ImageOptionsView: ExportOptionsView {
    let everyBuild = NSButton(checkboxWithTitle: "An image per build stage", target: nil, action: nil)

    init() {
        super.init(frame: .zero)
        install([everyBuild])
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("not loaded from a nib") }
}

/// Fixed choices rather than free numbers: a GIF that is 4013 pixels across or
/// held for 0.03s is a GIF nobody meant to make, and a popup cannot produce one.
private final class GifOptionsView: ExportOptionsView {
    private static let widths: [Int32] = [480, 640, 960, 1280]
    private static let seconds: [Float] = [0.5, 1, 1.5, 2, 3]

    let width = NSPopUpButton(frame: .zero, pullsDown: false)
    let hold = NSPopUpButton(frame: .zero, pullsDown: false)
    let loop = NSButton(checkboxWithTitle: "Loop forever", target: nil, action: nil)

    init() {
        super.init(frame: .zero)
        width.addItems(withTitles: Self.widths.map { "\($0) px" })
        width.selectItem(at: 2)
        hold.addItems(withTitles: Self.seconds.map { "\(secondsTitle($0)) per frame" })
        hold.selectItem(at: 2)
        loop.state = .on
        install([labelled("Width:", width), labelled("Hold:", hold), loop])
    }

    var selectedWidth: Int32 {
        Self.widths[min(max(width.indexOfSelectedItem, 0), Self.widths.count - 1)]
    }

    var selectedSeconds: Float {
        Self.seconds[min(max(hold.indexOfSelectedItem, 0), Self.seconds.count - 1)]
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("not loaded from a nib") }
}

/// "1s", "1.5s": no trailing zero on a whole number of seconds.
private func secondsTitle(_ value: Float) -> String {
    value == value.rounded() ? "\(Int(value))s" : "\(value)s"
}
