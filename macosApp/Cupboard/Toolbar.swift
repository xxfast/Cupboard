import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Toolbar

extension EditorView {
    var collapsedChrome: some View {
        Button { host.toggleSidebar() } label: {
            pill { Image(systemName: "sidebar.left").font(.system(size: 15)) }
        }
        .buttonStyle(.plain)
        .help("Show sidebar")
        .padding(.leading, Layout.collapsedToggle)
        .frame(height: Layout.header)
    }

    // MARK: Toolbar

    /// No bar of its own: no fill, no blur, no hairline. Only the capsules are
    /// opaque, and they float straight over the canvas and the inspector glass.
    func toolbar(_ ui: Chrome) -> some View {
        HStack(spacing: Layout.edge) {
            documentName
            Spacer(minLength: 8)
            toolbarCluster(ui)
            Spacer(minLength: 8)
            zoomPill
            if ui.inspectorOpen {
                // Fixed 254pt region over the inspector, laid out space-between:
                // Share 14 inside the panel's left edge, tabs 14 from the window.
                HStack(spacing: Layout.edge) {
                    shareButton
                    Spacer(minLength: 0)
                    tabGroup(ui)
                }
                .frame(width: Layout.tabRegion)
                .padding(.leading, Layout.edge)
            } else {
                shareButton
                tabGroup(ui)
            }
        }
        .padding(.leading, (ui.sidebarOpen ? Layout.toolbarOpen : Layout.toolbarClosed) + Layout.inset)
        .padding(.trailing, Layout.edge)
        .frame(height: Layout.header)
    }

    /// Where a title bar would be, since there isn't one: the deck's name, and
    /// under it whether the last few edits have made it to disk yet. The tag is
    /// a fixed-height row rather than one that comes and goes, so the name does
    /// not shift up and down as the autosave lands.
    var documentName: some View {
        let ui = chrome
        return HStack(spacing: 5) {
            VStack(alignment: .leading, spacing: 0) {
                Text(ui.documentTitle)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(palette.title)
                Text("Edited")
                    .font(.system(size: 11))
                    .foregroundStyle(palette.faint)
                    .opacity(ui.edited ? 1 : 0)
            }
            Image(systemName: "chevron.down")
                .font(.system(size: 8, weight: .semibold))
                .foregroundStyle(palette.faint)
        }
        .fixedSize()
    }

    func toolbarCluster(_ ui: Chrome) -> some View {
        HStack(spacing: 10) {
            // A layout is not a slide of the talk, so there is nothing to play
            // from while one is being edited. The shortcut goes with the button.
            Button { startPlay() } label: {
                pill { Image(systemName: "play.fill").font(.system(size: 12)) }
                    .opacity(ui.editingLayouts ? 0.45 : 1)
            }
            .buttonStyle(.plain)
            .keyboardShortcut(.return, modifiers: .command)
            .disabled(ui.editingLayouts)
            .help("Play from the selected slide")

            placeholderPill("plus.rectangle", help: "Add slide")

            insertCapsule

            placeholderPill("bubble.left", help: "Comment")
        }
        .fixedSize()
    }

    var shareButton: some View {
        placeholderPill("square.and.arrow.up", help: "Share")
    }

    func pill<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        content()
            .foregroundStyle(palette.icon)
            .frame(width: 36, height: 30)
            .background(palette.ctrl, in: Capsule())
    }

    func placeholderPill(_ symbol: String, help: String) -> some View {
        Button {} label: {
            pill { Image(systemName: symbol).font(.system(size: 13)).opacity(0.45) }
        }
        .buttonStyle(.plain)
        .disabled(true)
        .help(help)
    }

    /// The graph glyph, when the running system has it. SF Symbol names resolve
    /// at runtime, so an absent one draws nothing at all rather than failing to
    /// build; the connected-rectangle one is the older stand-in.
    static let diagramSymbol: String =
        NSImage(
            systemSymbolName: "point.3.connected.trianglepath.dotted",
            accessibilityDescription: nil
        ) == nil ? "rectangle.connected.to.line.below" : "point.3.connected.trianglepath.dotted"

    /// The radical glyph, resolved the same way the diagram one is; the
    /// function glyph stands in where the running system has no square root.
    static let equationSymbol: String =
        NSImage(
            systemSymbolName: "x.squareroot",
            accessibilityDescription: nil
        ) == nil ? "function" : "x.squareroot"

    /// The one insert cluster: raised capsule, 30x24 items. Text, Code, Terminal,
    /// Diagram, Equation, Shape and Image are live; Table and Chart wait on element
    /// types the document model does not hold yet.
    var insertCapsule: some View {
        HStack(spacing: 2) {
            insertPlaceholder("tablecells", help: "Table")
            insertPlaceholder("chart.pie", help: "Chart")

            Button { host.insertTextBox() } label: {
                insertIcon("textformat")
            }
            .buttonStyle(.plain)
            .help("Text Box")

            Button { host.insertCodeBox() } label: {
                insertIcon("curlybraces")
            }
            .buttonStyle(.plain)
            .help("Code")

            Button { host.insertTerminal() } label: {
                insertIcon("terminal")
            }
            .buttonStyle(.plain)
            .help("Terminal")

            Button { host.insertDiagram() } label: {
                insertIcon(Self.diagramSymbol)
            }
            .buttonStyle(.plain)
            .help("Diagram")

            Button { host.insertEquation() } label: {
                insertIcon(Self.equationSymbol)
            }
            .buttonStyle(.plain)
            .help("Equation")

            // A popup rather than a button: what a shape is comes off the
            // catalog, so picking one is picking a row of it.
            Menu {
                MenuEntries(entries: shapeEntries(host))
            } label: {
                insertIcon("square.on.circle")
            }
            .menuStyle(.borderlessButton)
            .menuIndicator(.hidden)
            .frame(width: 30, height: 24)
            .help("Shape")

            Button { Media.insert(into: host) } label: {
                insertIcon("photo")
            }
            .buttonStyle(.plain)
            .help("Image")
        }
        .padding(3)
        .background(palette.ctrl, in: Capsule())
    }

    /// One item of the cluster: the same 30x24 cell whether it acts or not.
    func insertIcon(_ symbol: String, dim: Bool = false) -> some View {
        Image(systemName: symbol)
            .font(.system(size: 13))
            .foregroundStyle(palette.icon.opacity(dim ? 0.45 : 1))
            .frame(width: 30, height: 24)
            .contentShape(RoundedRectangle(cornerRadius: 7))
    }

    func insertPlaceholder(_ symbol: String, help: String) -> some View {
        Button {} label: { insertIcon(symbol, dim: true) }
            .buttonStyle(.plain)
            .disabled(true)
            .help(help)
    }

    // MARK: Inspector tabs

    /// Three tabs in one raised capsule. The active one is a grey pill; all
    /// three icons keep the same ink, and the divider next to the active tab
    /// hides so the pill reads as one shape.
    func tabGroup(_ ui: Chrome) -> some View {
        HStack(spacing: 0) {
            tabButton(InspectorTab.format, symbol: "paintbrush", help: "Format", ui: ui)
            tabDivider(InspectorTab.format, InspectorTab.animate, ui: ui)
            tabButton(InspectorTab.animate, symbol: "diamond", help: "Animate", ui: ui)
            tabDivider(InspectorTab.animate, InspectorTab.document, ui: ui)
            tabButton(InspectorTab.document, symbol: "rectangle.fill", help: "Document", ui: ui)
        }
        .padding(2)
        .background(palette.ctrl, in: Capsule())
    }

    func tabButton(_ tab: InspectorTab, symbol: String, help: String, ui: Chrome) -> some View {
        let on = ui.inspectorOpen && ui.tab == tab
        return Button {
            // Clicking the tab you are already on closes the inspector.
            if on { host.closeInspector() } else { host.selectInspectorTab(tab: tab) }
        } label: {
            Image(systemName: symbol)
                .font(.system(size: 13))
                .foregroundStyle(palette.ctrlText)
                .frame(width: 34, height: 26)
                .background(
                    on ? palette.tabOn : .clear,
                    in: RoundedRectangle(cornerRadius: 13, style: .continuous)
                )
                .contentShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
        }
        .buttonStyle(.plain)
        .help(help)
    }

    func tabDivider(_ before: InspectorTab, _ after: InspectorTab, ui: Chrome) -> some View {
        let active = ui.inspectorOpen ? ui.tab : nil
        let hidden = active == before || active == after
        return (hidden ? Color.clear : palette.tabDivider).frame(width: 1, height: 16)
    }

    // MARK: Zoom

    private static let zoomSteps = [25, 50, 75, 100, 125, 150, 200]

    var zoomPill: some View {
        Menu {
            zoomOption("Fit in Window", percent: 0)
            Divider()
            ForEach(Self.zoomSteps, id: \.self) { zoomOption("\($0)%", percent: $0) }
        } label: {
            HStack(spacing: 5) {
                Text(zoomPercent == 0 ? "Fit" : "\(zoomPercent)%")
                    .font(.system(size: 12.5))
                    .foregroundStyle(palette.ctrlText)
                Image(systemName: "chevron.down")
                    .font(.system(size: 8, weight: .semibold))
                    .foregroundStyle(palette.faint)
            }
            .padding(.horizontal, 12)
            .frame(height: 30)
            .background(palette.ctrl, in: Capsule())
        }
        .menuStyle(.borderlessButton)
        .menuIndicator(.hidden)
        .fixedSize()
    }

    func zoomOption(_ label: String, percent: Int) -> some View {
        Button {
            zoomPercent = percent
            host.setZoomPercent(percent: Int32(percent))
        } label: {
            if zoomPercent == percent {
                Label(label, systemImage: "checkmark")
            } else {
                Text(label)
            }
        }
    }
}
