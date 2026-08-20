import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Inspector

extension EditorView {
    /// Header is bare: Share and the tabs moved to the toolbar, which floats
    /// over this glass, so the panel only owns the title under them.
    func inspector(_ ui: Chrome) -> some View {
        VStack(spacing: 0) {
            Color.clear.frame(height: Layout.header)

            VStack(spacing: 1) {
                Text(inspectorTitle(ui))
                    .font(.system(size: 13))
                    .foregroundStyle(palette.subtle)
                // The title names the primary element, so with more than one
                // selected it has to say what else the controls are editing.
                if ui.tab == InspectorTab.format && ui.selectionCount > 1 {
                    Text("\(ui.selectionCount) selected")
                        .font(.system(size: 11))
                        .foregroundStyle(palette.faint)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 4)
            .padding(.horizontal, Layout.panelPadding)
            .padding(.bottom, 12)

            if ui.tab == InspectorTab.document {
                documentPanel(ui)
            } else if ui.tab == InspectorTab.format {
                formatPanel(ui)
            } else {
                // The Animate body lands with the build editor.
                Spacer(minLength: 0)
            }
        }
        .frame(width: Layout.inspector)
        .frame(maxHeight: .infinity)
        .glass(.sidebar, edge: .leading, palette: palette)
    }

    /// Format names what it is formatting, so the title follows the selection.
    func inspectorTitle(_ ui: Chrome) -> String {
        if ui.tab == InspectorTab.animate { return "Build" }
        if ui.tab == InspectorTab.document { return "Slide" }
        return ui.element?.kind ?? "Text"
    }

    // MARK: Format panel

    /// The selected element's shared properties. Everything shown here comes
    /// back through `states`, so a typed value, a canvas drag and an undo all
    /// land in the same place.
    @ViewBuilder func formatPanel(_ ui: Chrome) -> some View {
        if let element = ui.element {
            VStack(alignment: .leading, spacing: Layout.panelPadding) {
                VStack(alignment: .leading, spacing: Layout.panelPadding) {
                    positionSection(element)
                    palette.divider.frame(height: 1)
                    rotateSection(element)
                    palette.divider.frame(height: 1)
                    opacitySection(element)
                    palette.divider.frame(height: 1)
                    arrangeSection
                }
                // A locked element ignores every edit but the button below, so
                // the panel says so rather than swallowing them silently.
                .disabled(element.locked)
                .opacity(element.locked ? 0.45 : 1)

                Spacer(minLength: 0)

                // Two unlocked elements make a group; a lone group comes apart
                // again. Neither button is here when it has nothing to do.
                if ui.canGroup {
                    panelButton("Group", symbol: "square.on.square") { host.groupSelection() }
                }
                if ui.canUngroup {
                    panelButton("Ungroup", symbol: "square.split.2x2") { host.ungroupSelection() }
                }

                lockButton(element)
            }
            .padding(Layout.panelPadding)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        } else {
            Text("Select an element to edit it")
                .font(.system(size: 12))
                .foregroundStyle(palette.faint)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(Layout.panelPadding)
        }
    }

    func positionSection(_ element: Selection) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Position & Size")

            HStack(spacing: 8) {
                ValueField(label: "X", value: element.x, palette: palette) {
                    setFrame(element, x: $0)
                }
                ValueField(label: "Y", value: element.y, palette: palette) {
                    setFrame(element, y: $0)
                }
            }

            HStack(spacing: 8) {
                ValueField(label: "W", value: element.width, palette: palette) {
                    setFrame(element, width: $0)
                }
                ValueField(label: "H", value: element.height, palette: palette) {
                    setFrame(element, height: $0)
                }
            }
        }
    }

    /// A frame commits whole, so a field that edits one number sends the other
    /// three back as they stand.
    func setFrame(
        _ element: Selection,
        x: Double? = nil,
        y: Double? = nil,
        width: Double? = nil,
        height: Double? = nil
    ) {
        host.setSelectedElementFrame(
            x: Float(x ?? element.x),
            y: Float(y ?? element.y),
            width: Float(width ?? element.width),
            height: Float(height ?? element.height)
        )
    }

    func rotateSection(_ element: Selection) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Rotate")

            HStack(spacing: 8) {
                ValueField(label: "\u{00B0}", value: element.rotation, palette: palette) {
                    host.setSelectedElementRotation(degrees: Float($0))
                }
                .frame(width: 104)

                Spacer(minLength: 0)

                flipButton(
                    "arrow.left.and.right.righttriangle.left.righttriangle.right",
                    help: "Flip Horizontally"
                ) { host.flipSelectedElement(axis: FlipAxis.horizontal) }

                flipButton(
                    "arrow.up.and.down.righttriangle.up.righttriangle.down",
                    help: "Flip Vertically"
                ) { host.flipSelectedElement(axis: FlipAxis.vertical) }
            }
        }
    }

    func flipButton(
        _ symbol: String,
        help: String,
        action: @escaping () -> Void
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 12))
                .foregroundStyle(palette.ctrlText)
                .frame(width: 32, height: 22)
                .background(palette.ctrl, in: shape)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
        .help(help)
    }

    func opacitySection(_ element: Selection) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Opacity")

            HStack(spacing: 10) {
                Slider(
                    value: Binding(
                        get: { element.opacity },
                        set: { host.setSelectedElementOpacity(opacity: Float($0), commit: false) }
                    ),
                    in: 0...1,
                    onEditingChanged: { editing in
                        // The release commits whatever the previews left in the
                        // document, so the whole drag is one undo entry. Read it
                        // back rather than trusting this pass's snapshot.
                        guard !editing, let live = host.selectedElement() else { return }
                        host.setSelectedElementOpacity(opacity: live.opacity, commit: true)
                    }
                )
                .controlSize(.small)
                .tint(palette.accent)

                Text("\(Int((element.opacity * 100).rounded()))%")
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(palette.ctrlText)
                    .frame(width: 40, alignment: .trailing)
            }
        }
    }

    var arrangeSection: some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Arrange")

            HStack(spacing: 8) {
                arrangeButton("Bring Forward", ZOrderMove.forward)
                arrangeButton("Send Backward", ZOrderMove.backward)
            }

            HStack(spacing: 8) {
                arrangeButton("Bring to Front", ZOrderMove.tofront)
                arrangeButton("Send to Back", ZOrderMove.toback)
            }
        }
    }

    func arrangeButton(_ label: String, _ move: ZOrderMove) -> some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return Button { host.reorderSelectedElement(move: move) } label: {
            Text(label)
                .font(.system(size: 11.5))
                .foregroundStyle(palette.ctrlText)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .frame(maxWidth: .infinity)
                .frame(height: 22)
                .background(palette.ctrl, in: shape)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
    }

    /// Full width and always live: it is the only way back into a locked element.
    func lockButton(_ element: Selection) -> some View {
        panelButton(
            element.locked ? "Unlock" : "Lock",
            symbol: element.locked ? "lock.fill" : "lock.open"
        ) { host.toggleSelectedElementLock() }
    }

    /// The panel's raised full-width button: the lock, and the group pair above it.
    func panelButton(
        _ label: String,
        symbol: String,
        action: @escaping () -> Void
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: 6, style: .continuous)
        return Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: symbol)
                    .font(.system(size: 11))
                Text(label)
                    .font(.system(size: 12.5))
            }
            .foregroundStyle(palette.ctrlText)
            .frame(maxWidth: .infinity)
            .frame(height: 26)
            .background(palette.buttonFill, in: shape)
            .contentShape(shape)
        }
        .buttonStyle(.plain)
    }

    /// A small bordered value field: whole document units, mono, committed on
    /// Enter or on losing focus. Anything that is not a number reverts to what
    /// the document holds, so a half-typed field cannot push nonsense in.
    private struct ValueField: View {
        let label: String
        let value: Double
        let palette: Palette
        let onCommit: (Double) -> Void

        @State private var text: String = ""
        @FocusState private var focused: Bool

        private var shape: RoundedRectangle {
            RoundedRectangle(cornerRadius: 5, style: .continuous)
        }

        var body: some View {
            HStack(spacing: 6) {
                Text(label)
                    .font(.system(size: 11))
                    .foregroundStyle(palette.subtle)
                    .frame(width: 11, alignment: .leading)

                TextField("", text: $text)
                    .textFieldStyle(.plain)
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(palette.ctrlText)
                    .multilineTextAlignment(.trailing)
                    .focused($focused)
                    .padding(.horizontal, 7)
                    .frame(height: 22)
                    .background(palette.ctrl, in: shape)
                    .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
                    .onSubmit { commit() }
                    .onChange(of: focused) { _, now in if !now { commit() } }
            }
            .onAppear { text = Self.whole(value) }
            // A canvas drag or an undo moves the element under the field. The
            // one being typed in is left alone until it loses focus.
            .onChange(of: value) { _, latest in if !focused { text = Self.whole(latest) } }
        }

        private func commit() {
            guard let typed = Double(text.trimmingCharacters(in: .whitespaces)) else {
                text = Self.whole(value)
                return
            }
            text = Self.whole(typed)
            onCommit(typed)
        }

        private static func whole(_ value: Double) -> String { String(Int(value.rounded())) }
    }

    // MARK: Document panel

    /// The selected slide, as far as the document model goes: the number switch
    /// and the background are real and ride through `states`; the layout card
    /// above them is still static, layouts not being modelled yet.
    func documentPanel(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: Layout.panelPadding) {
            slideLayoutCard
            appearanceSection(ui)
            palette.divider.frame(height: 1)
            backgroundSection(ui)
            Spacer(minLength: 0)
            editLayoutButton
        }
        .padding(Layout.panelPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    var slideLayoutCard: some View {
        HStack(spacing: 12) {
            layoutPreview
            VStack(alignment: .leading, spacing: 0) {
                Text("Slide Layout")
                    .font(.system(size: 11))
                    .foregroundStyle(palette.subtle)
                Text("Title")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(palette.text)
            }
            Spacer(minLength: 0)
            Text("\u{2304}")
                .font(.system(size: 10))
                .foregroundStyle(palette.subtle)
        }
        .padding(10)
        .background(palette.ctrl, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
    }

    /// A slide the way a layout picker draws one: white paper, three grey bars.
    var layoutPreview: some View {
        let shape = RoundedRectangle(cornerRadius: 3, style: .continuous)
        return VStack(alignment: .leading, spacing: 0) {
            previewBar(width: 34, height: 4, color: Color(rgb: 0x2A2630))
            previewBar(width: 23, height: 3, color: Color(rgb: 0x9A958D))
                .padding(.top, 3)
            previewBar(width: 17, height: 2.5, color: Color(rgb: 0xC9C4BD))
                .padding(.top, 10)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 5)
        .frame(width: 62, height: 35, alignment: .topLeading)
        .background(Color.white, in: shape)
        .overlay { shape.inset(by: 0.5).stroke(Color.black.opacity(0.12), lineWidth: 1) }
    }

    func previewBar(width: CGFloat, height: CGFloat, color: Color) -> some View {
        RoundedRectangle(cornerRadius: 1).fill(color).frame(width: width, height: height)
    }

    /// Title and Body are still inert: what a layout puts on a slide is the
    /// layout's, and layouts are not modelled yet. The number is the slide's own.
    func appearanceSection(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Appearance")
            checkRow("Title", on: true)
            checkRow("Body", on: true)
            checkRow("Slide Number", on: ui.slide.numberVisible) {
                host.setSlideNumberVisible(visible: !ui.slide.numberVisible)
            }
        }
    }

    func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(palette.subtle)
    }

    /// [action] nil is a row that only reports: the ones whose fact the document
    /// model does not hold yet stay untouchable rather than lying about a toggle.
    @ViewBuilder func checkRow(
        _ label: String,
        on: Bool,
        action: (() -> Void)? = nil
    ) -> some View {
        let row = HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(on ? palette.accent : palette.track)
                .frame(width: 15, height: 15)
                .overlay {
                    if on {
                        Image(systemName: "checkmark")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(palette.accentText)
                    }
                }
            Text(label)
                .font(.system(size: 13))
                .foregroundStyle(palette.text)
            Spacer(minLength: 0)
        }

        if let action {
            Button(action: action) { row.contentShape(Rectangle()) }
                .buttonStyle(.plain)
        } else {
            row
        }
    }

    /// What the slide paints behind its elements: the deck's own, a flat colour,
    /// or a two-stop gradient. Switching to a kind the slide is not wearing
    /// commits it there and then, so the swatches below always have something to
    /// mark, and every pick is one edit and one undo entry.
    @ViewBuilder func backgroundSection(_ ui: Chrome) -> some View {
        let slide = ui.slide
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Background")

            HStack(spacing: 2) {
                segment("Default", on: slide.backgroundKind == 0) { host.setBackgroundDefault() }
                segment("Color", on: slide.backgroundKind == 1) {
                    host.setBackgroundColor(argb: slide.color)
                }
                segment("Gradient", on: slide.backgroundKind == 2) {
                    host.setBackgroundGradient(start: slide.gradientStart, end: slide.gradientEnd)
                }
            }
            .padding(2)
            .frame(height: 26)
            .background(palette.segBg, in: RoundedRectangle(cornerRadius: 6, style: .continuous))

            switch slide.backgroundKind {
            case 1:
                swatchGrid(selected: slide.color) { host.setBackgroundColor(argb: $0) }
            case 2:
                stopRow("Start", selected: slide.gradientStart) {
                    host.setBackgroundGradient(start: $0, end: slide.gradientEnd)
                }
                stopRow("End", selected: slide.gradientEnd) {
                    host.setBackgroundGradient(start: slide.gradientStart, end: $0)
                }
            default:
                Text("The deck's own background.")
                    .font(.system(size: 12))
                    .foregroundStyle(palette.faint)
            }
        }
    }

    /// One end of the gradient: the same swatches, said whose they are.
    func stopRow(
        _ label: String,
        selected: Int64,
        onPick: @escaping (Int64) -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.system(size: 11))
                .foregroundStyle(palette.subtle)
            swatchGrid(selected: selected, onPick: onPick)
        }
    }

    /// The palette, six to a row. No colour panel yet: one tap is one colour and
    /// one undo entry, which a live picker would spend a hundred entries on.
    func swatchGrid(selected: Int64, onPick: @escaping (Int64) -> Void) -> some View {
        LazyVGrid(
            columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 6),
            spacing: 6
        ) {
            ForEach(Self.backgroundSwatches, id: \.self) { argb in
                swatch(argb, selected: argb == selected, onPick: onPick)
            }
        }
    }

    func swatch(
        _ argb: Int64,
        selected: Bool,
        onPick: @escaping (Int64) -> Void
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return Button { onPick(argb) } label: {
            shape
                .fill(Color(argb: argb))
                .frame(height: 22)
                .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
                .overlay {
                    if selected { shape.inset(by: -2.5).stroke(palette.accent, lineWidth: 2) }
                }
                .contentShape(shape)
        }
        .buttonStyle(.plain)
    }

    /// The deck's inks first, then the accents, then two paper tones. Packed
    /// ARGB, the document model's colour format.
    private static let backgroundSwatches: [Int64] = [
        0xFF000000, 0xFF17181C, 0xFF23262E, 0xFF101223, 0xFF2A2452, 0xFF4C2FA8,
        0xFF0F3B39, 0xFF10391F, 0xFF58151D, 0xFF6B4A0E, 0xFFD7D9DE, 0xFFFFFFFF,
    ]

    func segment(_ label: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12, weight: on ? .semibold : .regular))
                .foregroundStyle(on ? palette.accentText : palette.subtle)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(
                    on ? AnyShapeStyle(palette.accent) : AnyShapeStyle(Color.clear),
                    in: RoundedRectangle(cornerRadius: 5, style: .continuous)
                )
                .contentShape(RoundedRectangle(cornerRadius: 5, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    var editLayoutButton: some View {
        Text("Edit Slide Layout")
            .font(.system(size: 12.5))
            .foregroundStyle(palette.ctrlText)
            .frame(maxWidth: .infinity)
            .frame(height: 26)
            .background(palette.buttonFill, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
    }
}
