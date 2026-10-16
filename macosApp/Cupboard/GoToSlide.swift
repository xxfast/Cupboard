import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Go to Slide

extension EditorView {
    /// The bar over the canvas, centred on the part of it the panels leave
    /// showing rather than on the window. Behind it, a catcher the size of the
    /// window: a click anywhere else puts the bar away and goes no further, so
    /// it cannot also select something.
    func goToSlideBar(_ ui: Chrome) -> some View {
        let leading = (ui.sidebarOpen ? Layout.cardLeading + Layout.sidebar : 0)
            + (ui.objectListOpen ? Layout.objectListRun : 0)
        let trailing = ui.inspectorOpen ? Layout.inspector : 0
        return ZStack(alignment: .top) {
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture { model.goingToSlide = false }

            GoToSlideBar(model: model, count: Int(host.presentedSlideCount()), palette: palette)
                .padding(.top, Layout.header + 24)
                .padding(.leading, leading)
                .padding(.trailing, trailing)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Spotlight's shape for one question: which slide. The digits typed are the
/// bar's own and go nowhere until Return; the number they make is the one the
/// navigator rows show, so skipped slides have none and [count] is the last.
struct GoToSlideBar: View {
    let model: EditorModel
    let count: Int
    let palette: Palette

    @State private var text = ""
    @FocusState private var focused: Bool
    /// Whatever had the keyboard when the bar came up, the canvas or a panel
    /// field, so it has it back when the bar goes. A class in a @State only so
    /// the box outlives re-inits: nothing redraws for what is in it.
    @State private var previous = Responder()

    private static let width: CGFloat = 300

    var body: some View {
        TextField("Slide number (1–\(count))", text: $text)
            .textFieldStyle(.plain)
            .font(.system(size: 20))
            .foregroundStyle(palette.text)
            .focused($focused)
            // Digits only. Filtered after the fact: the field has no input
            // hook, and a write back that changes nothing does not loop.
            .onChange(of: text) { _, typed in
                let digits = typed.filter { $0.isASCII && $0.isNumber }
                if digits != typed { text = digits }
            }
            .onSubmit { submit() }
            .onExitCommand { model.goingToSlide = false }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(width: Self.width)
            .glassCard(palette: palette)
            .onAppear {
                previous.capture(model.host.view.window)
                focused = true
            }
            .onDisappear {
                // Also what clears the flag when the editor goes with the bar
                // still up (a show in the window), so it does not come back.
                model.goingToSlide = false
                previous.restore()
            }
    }

    /// In range jumps and closes; anything else beeps and stays, so the number
    /// can be fixed rather than retyped.
    private func submit() {
        guard let number = Int(text), number >= 1, number <= count else {
            NSSound.beep()
            return
        }
        model.host.goToSlide(number: Int32(number))
        model.goingToSlide = false
    }
}

/// The responder the bar took the keyboard from. A field being typed into is
/// its window's field editor while it has the focus, so the field itself is
/// what is kept: the editor is shared, and by the time the bar goes it is ours.
private final class Responder {
    private weak var window: NSWindow?
    private weak var responder: NSResponder?

    func capture(_ window: NSWindow?) {
        self.window = window
        var current = window?.firstResponder
        if let editor = current as? NSTextView, editor.isFieldEditor,
           let field = editor.delegate as? NSResponder {
            current = field
        }
        responder = current
    }

    /// A turn later, once SwiftUI has let go of the bar's field. A responder
    /// that left the window meanwhile (the editor came down) is left alone.
    func restore() {
        let window = self.window
        let responder = self.responder
        DispatchQueue.main.async {
            guard let window, let responder else { return }
            if let view = responder as? NSView, view.window !== window { return }
            window.makeFirstResponder(responder)
        }
    }
}
