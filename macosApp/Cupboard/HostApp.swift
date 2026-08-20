// Design v3 layered window: the Compose canvas is full-bleed edge to edge and
// the navigator card, notes strip, inspector and toolbar float over it. Layer
// order back to front: canvas, speaker notes, panels, toolbar, collapsed chrome.
// Only the canvas is Compose, bridged through EditorHost from CupboardCanvas.
// This file is the app entry and the EditorView shell; its panels live in
// extensions next door (Navigator, Toolbar, Inspector), the chrome tokens in
// Theme, the AppKit window work in Window, the menus in Menus.
// Build/run: ./macosApp/run.sh
import SwiftUI
import AppKit
import Observation
import CupboardCanvas

// MARK: - App

/// Activates the app once it finishes launching. Launched by exec'ing the
/// binary, which is what run.sh does to keep logs on the terminal,
/// LaunchServices never activates the process, and SwiftUI holds the
/// WindowGroup's window back until the first activation (instrumented: zero
/// windows ever exist before it), so the window only appeared after a dock
/// click sent activate + reopen. A Finder or `open` launch never needed this;
/// it makes the dev loop behave like one.
final class ActivationDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        if #available(macOS 14.0, *) {
            NSApp.activate()
        } else {
            NSApp.activate(ignoringOtherApps: true)
        }
    }
}

@main
struct CupboardHostApp: App {
    @NSApplicationDelegateAdaptor(ActivationDelegate.self) private var activation
    @State private var model = EditorModel()
    @State private var playSession: PlaySession?

    private var host: EditorHost { model.host }

    var body: some Scene {
        WindowGroup("Cupboard") {
            Group {
                if let session = playSession {
                    PlayCanvas(session: session)
                        .background(Color.black)
                } else {
                    EditorView(model: model, playSession: $playSession)
                }
            }
            .ignoresSafeArea()
            .background(WindowConfigurator(lights: model.lights).frame(width: 0, height: 0))
        }
        .windowStyle(.hiddenTitleBar)
        .commands {
            // Ours, not AppKit's: the history lives in Kotlin, so the system
            // undo manager has nothing to say about it.
            CommandGroup(replacing: .undoRedo) {
                // Reading generation is what keeps these enabled states fresh.
                let _ = model.generation
                Button("Undo") { host.undo() }
                    .keyboardShortcut("z", modifiers: .command)
                    .disabled(!host.canUndo())
                Button("Redo") { host.redo() }
                    .keyboardShortcut("z", modifiers: [.command, .shift])
                    .disabled(!host.canRedo())
            }
            // Same menu, below Undo/Redo. Backspace is the key equivalent, which
            // is also what makes it fire for a selection made on the canvas.
            CommandGroup(after: .undoRedo) {
                let _ = model.generation
                Divider()
                Button("Delete") { host.deleteSelection() }
                    .keyboardShortcut(.delete, modifiers: [])
                    .disabled(!host.canDelete())
                Button("Clear All") { host.clearAll() }
                    .disabled(!host.canClearAll())
            }
            // Replacing rather than adding: the system Cut/Copy/Paste items own
            // Cmd+X/C/V and would shadow ours. The clipboard is the app's own,
            // so nothing here has any business talking to NSPasteboard.
            // doCopy... is the exporter's doing: copy is a reserved ObjC method
            // family, so every copyX on the host arrives here as doCopyX.
            CommandGroup(replacing: .pasteboard) {
                let _ = model.generation
                Button("Cut") { host.cutSelection() }
                    .keyboardShortcut("x", modifiers: .command)
                    .disabled(!host.canCut())
                Button("Copy") { host.doCopySelection() }
                    .keyboardShortcut("c", modifiers: .command)
                    .disabled(!host.canCopy())
                Button("Paste") { host.paste() }
                    .keyboardShortcut("v", modifiers: .command)
                    .disabled(!host.canPaste())
                Button("Duplicate") { host.duplicateSelection() }
                    .keyboardShortcut("d", modifiers: .command)
                    .disabled(!host.canDuplicate())

                Divider()

                Button("Copy Style") { host.doCopyStyle() }
                    .keyboardShortcut("c", modifiers: [.command, .option])
                    .disabled(!host.canCopyStyle())
                Button("Paste Style") { host.pasteStyle() }
                    .keyboardShortcut("v", modifiers: [.command, .option])
                    .disabled(!host.canPasteStyle())
            }
            CommandGroup(after: .sidebar) {
                let _ = model.generation
                Toggle("Show Speaker Notes", isOn: Binding(
                    get: { host.showNotes() },
                    set: { _ in host.toggleNotes() }
                ))
            }
            slideMenu
            arrangeMenu
        }
    }

    /// Acts on the slide as a whole rather than what is on it. No key equivalents:
    /// Cmd+X/C/V belong to the elements on the canvas, and a slide-level clipboard
    /// stealing them would make the common case unreachable.
    private var slideMenu: some Commands {
        CommandMenu("Slide") {
            // Reading generation is what keeps these acting on the slide that is
            // selected now. Off the same list the navigator rows render, minus
            // Paste: in the bar that verb is Edit > Paste's.
            let _ = model.generation
            MenuEntries(entries: slideEntries(host, slideId: nil, includePaste: false))
        }
    }

    /// The Compose shell's Arrange menu, natively, off the same entry list the
    /// canvas context menu renders. One statement of the items, two renderings:
    /// the two menus cannot drift apart, only be built from different facts.
    private var arrangeMenu: some Commands {
        CommandMenu("Arrange") {
            // Reading generation is what keeps these enabled states fresh.
            let _ = model.generation
            MenuEntries(entries: arrangeEntries(ArrangeFacts(Chrome(host)), host))
        }
    }
}

// MARK: - Editor

/// The navigator's scroll content, as a coordinate space: row frames, the drag
/// and the drop line are all measured in it, so they cannot disagree.

struct EditorView: View {
    let model: EditorModel
    @Binding var playSession: PlaySession?

    @Environment(\.colorScheme) var colorScheme
    /// Zoom is view-local in the Kotlin host, outside `states`, so the label
    /// reads from this mirror rather than waiting on a generation bump.
    @State var zoomPercent: Int = 0
    /// The row being dragged, and where each row sits for the gap maths.
    @State var slideDrag: SlideDrag?
    @State var rowFrames: [String: CGRect] = [:]

    var host: EditorHost { model.host }
    var palette: Palette { Palette.of(colorScheme) }

    /// Touching `generation` is what subscribes this view to store changes.
    var chrome: Chrome {
        let _ = model.generation
        return Chrome(host)
    }

    /// Canvas at the back, edge to edge; the notes strip over it; the two panels
    /// over that (the notes pass visibly behind the navigator card); the toolbar
    /// over the panels, so its right-hand controls float on the inspector glass.
    var body: some View {
        let ui = chrome
        return ZStack {
            ComposeCanvas(host: host)

            if ui.showNotes {
                notesStrip(ui)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .transition(.move(edge: .bottom))
            }

            panels(ui)

            toolbar(ui)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)

            if !ui.sidebarOpen {
                collapsedChrome
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
        .frame(minWidth: 1100, minHeight: 640)
        .background {
            TrafficLightTarget(lights: model.lights, sidebarOpen: ui.sidebarOpen)
                .frame(width: 0, height: 0)
        }
        .animation(.easeInOut(duration: 0.2), value: ui.sidebarOpen)
        .animation(.easeInOut(duration: 0.2), value: ui.inspectorOpen)
        .animation(.easeInOut(duration: 0.2), value: ui.showNotes)
        // The well is painted Kotlin-side, so the appearance has to be pushed in.
        .onAppear { host.setDarkChrome(dark: colorScheme == .dark) }
        .onChange(of: colorScheme) { _, scheme in host.setDarkChrome(dark: scheme == .dark) }
    }

    func panels(_ ui: Chrome) -> some View {
        HStack(spacing: 0) {
            if ui.sidebarOpen {
                navigatorCard
                    .frame(width: Layout.sidebar)
                    .padding(.leading, Layout.cardLeading)
                    .padding(.top, Layout.cardTop)
                    .padding(.bottom, Layout.cardBottom)
                    .transition(.move(edge: .leading))
            }
            Spacer(minLength: 0)
            if ui.inspectorOpen {
                inspector(ui).transition(.move(edge: .trailing))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: Speaker notes

    /// Full window width and pinned to the bottom, so it passes behind the
    /// navigator card. Its text insets clear whichever panel is open.
    func notesStrip(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("SPEAKER NOTES")
                .font(.system(size: 10.5, weight: .bold))
                .tracking(1.2)
                .foregroundStyle(palette.faint)
            Text(ui.notes)
                .font(.system(size: 13.5))
                .foregroundStyle(palette.dim)
                .lineSpacing(6.75)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(.top, 18)
        .padding(.bottom, 20)
        .padding(.leading, ui.sidebarOpen ? Layout.notesLeading : Layout.notesInset)
        .padding(.trailing, ui.inspectorOpen ? Layout.notesTrailing : Layout.notesInset)
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(height: Layout.notes)
        .background(palette.panel)
        .overlay(alignment: .top) { palette.divider.frame(height: 1) }
    }

    func startPlay() {
        // Kotlin calls onExit on the main thread, so touching @State is safe.
        playSession = host.startPlay(onExit: { playSession = nil })
    }
}