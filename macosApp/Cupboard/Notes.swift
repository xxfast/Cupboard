import SwiftUI
import AppKit
import CupboardCanvas

// The speaker-notes strip, Keynote's in what it does: click in it for a caret,
// Return for a new line, Escape to leave, long notes scroll, and its top edge
// drags to resize it or, taken to the bottom, to hide it.

extension EditorView {
    /// Full window width and pinned to the bottom, so it passes behind the
    /// navigator card. Its text insets clear whichever panel is open.
    func notesStrip(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("SPEAKER NOTES")
                .font(.system(size: 10.5, weight: .bold))
                .tracking(1.2)
                .foregroundStyle(palette.faint)
            NotesField(
                host: host,
                slideId: ui.notesSlideId,
                notes: ui.notes,
                editing: ui.editingNotes,
                ink: NSColor(palette.dim),
                caret: NSColor(palette.accent)
            )
        }
        .padding(.top, 18)
        .padding(.bottom, 20)
        .padding(.leading, (ui.sidebarOpen ? Layout.notesLeading : Layout.notesInset)
            + (ui.objectListOpen ? Layout.objectListRun : 0))
        .padding(.trailing, ui.inspectorOpen ? Layout.notesTrailing : Layout.notesInset)
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(height: ui.notesHeight)
        .background(palette.panel)
        .overlay(alignment: .top) { palette.divider.frame(height: 1) }
        .overlay(alignment: .top) { NotesGrip(host: host, height: ui.notesHeight) }
    }
}

/// The strip's top edge. What the drag shows is the height the editor answers
/// with: this only does the sums, from where the strip stood when it began.
private struct NotesGrip: View {
    let host: EditorHost
    let height: CGFloat

    @State private var start: CGFloat?

    var body: some View {
        Color.clear
            .frame(height: 8)
            .contentShape(Rectangle())
            .onHover { inside in
                if inside { NSCursor.resizeUpDown.push() } else { NSCursor.pop() }
            }
            // Global, since the grip rides the edge it is moving.
            .gesture(
                DragGesture(minimumDistance: 1, coordinateSpace: .global)
                    .onChanged { drag in
                        let from = start ?? height
                        start = from
                        host.resizeNotes(height: from - drag.translation.height)
                    }
                    .onEnded { drag in
                        let from = start ?? height
                        start = nil
                        // Keynote's: let go near the bottom and the strip
                        // hides, at the height it had.
                        guard from - drag.translation.height < host.notesHideHeight() else { return }
                        host.resizeNotes(height: from)
                        host.toggleNotes()
                    }
            )
    }
}

/// The notes themselves. An NSTextView rather than a TextEditor, for what
/// SwiftUI's will not do: tell the editor the caret came and went, and take the
/// Edit menu's chords back from it while the caret is here.
///
/// The view keeps its own text and streams it out; the document is the
/// authority again whenever the caret is elsewhere, which is how a new slide
/// and an undo show up.
private struct NotesField: NSViewRepresentable {
    let host: EditorHost
    let slideId: String
    let notes: String
    let editing: Bool
    let ink: NSColor
    let caret: NSColor

    func makeCoordinator() -> Coordinator { Coordinator(host: host, slideId: slideId) }

    func makeNSView(context: Context) -> NSScrollView {
        let text = NotesTextView(frame: .zero)
        text.host = host
        text.delegate = context.coordinator
        text.isRichText = false
        text.allowsUndo = true
        text.usesFontPanel = false
        text.drawsBackground = false
        text.textContainerInset = .zero
        text.textContainer?.lineFragmentPadding = 0

        // Apple's recipe for a text view that wraps to its scroll view and
        // grows downwards.
        text.minSize = .zero
        text.maxSize = NSSize(width: CGFloat.greatestFiniteMagnitude, height: .greatestFiniteMagnitude)
        text.isVerticallyResizable = true
        text.isHorizontallyResizable = false
        text.autoresizingMask = [.width]
        text.textContainer?.containerSize = NSSize(width: 0, height: CGFloat.greatestFiniteMagnitude)
        text.textContainer?.widthTracksTextView = true

        // 13.5 on a 20.25 line, the strip's type everywhere it is drawn.
        let paragraph = NSMutableParagraphStyle()
        paragraph.lineSpacing = 6.75
        text.defaultParagraphStyle = paragraph
        text.font = .systemFont(ofSize: 13.5)
        text.typingAttributes = [
            .font: NSFont.systemFont(ofSize: 13.5),
            .paragraphStyle: paragraph,
            .foregroundColor: ink,
        ]
        text.string = notes

        let scroll = NSScrollView()
        scroll.drawsBackground = false
        scroll.hasVerticalScroller = true
        scroll.autohidesScrollers = true
        scroll.documentView = text
        return scroll
    }

    func updateNSView(_ scroll: NSScrollView, context: Context) {
        guard let text = scroll.documentView as? NotesTextView else { return }
        let coordinator = context.coordinator

        text.textColor = ink
        text.insertionPointColor = caret
        text.typingAttributes[.foregroundColor] = ink

        if coordinator.slideId != slideId || (!editing && text.string != notes) {
            coordinator.slideId = slideId
            text.string = notes
            coordinator.history.removeAllActions()
        }

        // A session the editor ended itself, on a click in the navigator, a
        // toolbar verb or an undo: the caret leaves with it, for the canvas.
        // Not from inside this update, since the blur reports back to Kotlin.
        if editing {
            coordinator.sawSession = true
        } else if coordinator.sawSession {
            coordinator.sawSession = false
            DispatchQueue.main.async { [host] in
                guard let window = text.window, window.firstResponder === text else { return }
                window.makeFirstResponder(host.view)
            }
        }
    }

    final class Coordinator: NSObject, NSTextViewDelegate {
        let host: EditorHost
        var slideId: String
        var sawSession = false
        /// The typing's own undo stack, Keynote's Undo Typing, kept apart from
        /// the window's so a slide change can drop it.
        let history = UndoManager()

        init(host: EditorHost, slideId: String) {
            self.host = host
            self.slideId = slideId
        }

        func textDidChange(_ notification: Notification) {
            guard let text = notification.object as? NSTextView else { return }
            host.editNotes(notes: text.string)
        }

        func undoManager(for view: NSTextView) -> UndoManager? { history }

        /// Escape leaves the notes for the canvas instead of offering completions.
        func textView(_ view: NSTextView, doCommandBy selector: Selector) -> Bool {
            guard selector == #selector(NSResponder.cancelOperation(_:)) else { return false }
            view.window?.makeFirstResponder(host.view)
            return true
        }
    }
}

private final class NotesTextView: NSTextView {
    weak var host: EditorHost?

    override func becomeFirstResponder() -> Bool {
        let became = super.becomeFirstResponder()
        if became { host?.beginNotesEdit() }
        return became
    }

    override func resignFirstResponder() -> Bool {
        let resigned = super.resignFirstResponder()
        guard resigned else { return false }
        // A selection left behind would stay painted, greyed, under no caret.
        setSelectedRange(NSRange(location: selectedRange().location, length: 0))
        host?.endNotesEdit()
        return true
    }

    /// The Edit menu's items are the editor's own and act on slides and
    /// elements, so AppKit never sends a text view its cut, copy, paste or
    /// undo. A view hears a chord before the menu does: with the caret here,
    /// they are the text's.
    override func performKeyEquivalent(with event: NSEvent) -> Bool {
        guard window?.firstResponder === self else { return super.performKeyEquivalent(with: event) }

        let modifiers = event.modifierFlags.intersection([.command, .shift, .option, .control])
        switch (event.charactersIgnoringModifiers?.lowercased(), modifiers) {
        case ("x", .command): cut(nil)
        case ("c", .command): copy(nil)
        case ("v", .command): pasteAsPlainText(nil)
        case ("a", .command): selectAll(nil)
        case ("z", .command): undoManager?.undo()
        case ("z", [.command, .shift]): undoManager?.redo()
        default: return super.performKeyEquivalent(with: event)
        }
        return true
    }
}
