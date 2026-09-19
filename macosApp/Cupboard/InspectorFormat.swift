import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Format segments

/// The Format tab's body: one segment at a time, the way Keynote's is. The
/// segmented control above it is the panel's (`Inspector.swift`); everything
/// under it is here.
extension EditorView {
    /// Whichever segment is on. Arrange is the one that is not wholly disabled
    /// by a locked element: its Lock pair is the only way back out of one.
    @ViewBuilder func formatSegmentBody(_ ui: Chrome, _ element: Selection) -> some View {
        if ui.activeFormatSegment == "Arrange" {
            arrangeSegment(ui, element)
        } else if ui.activeFormatSegment == "Style" {
            styleSegment(ui, element)
                .disabled(element.locked)
                .opacity(element.locked ? 0.45 : 1)
        } else {
            kindSegment(ui, ui.activeFormatSegment)
                .disabled(element.locked)
                .opacity(element.locked ? 0.45 : 1)
        }
    }

    /// What the element looks like: the appearance controls of its kind, then
    /// the Opacity every kind wears. The object styles strip that belongs at the
    /// top of this segment is pinned above the scroll area rather than drawn
    /// here, so it stays put while these scroll.
    @ViewBuilder func styleSegment(_ ui: Chrome, _ element: Selection) -> some View {
        VStack(alignment: .leading, spacing: Layout.panelPadding) {
            if let shape = ui.shape {
                shapeSection(shape, ui)
                palette.divider.frame(height: 1)
            }
            if let code = ui.code {
                codeThemeSection(code)
                palette.divider.frame(height: 1)
            }
            if let terminal = ui.terminal {
                terminalAppearanceSection(terminal)
                palette.divider.frame(height: 1)
            }
            if let diagram = ui.diagram {
                diagramColorsSection(diagram)
                palette.divider.frame(height: 1)
            }
            if let equation = ui.equation {
                equationColorSection(equation)
                palette.divider.frame(height: 1)
            }

            opacitySection(element)
        }
    }

    /// The rest of the kind's own controls: what it says and what it is made of,
    /// as opposed to what it looks like. The link sits at the foot of the ones
    /// that carry one, which is where Keynote keeps it.
    @ViewBuilder func kindSegment(_ ui: Chrome, _ segment: String) -> some View {
        VStack(alignment: .leading, spacing: Layout.panelPadding) {
            switch segment {
            case "Text":
                // A shape has a word written on it rather than a text style, so
                // its Text segment is the label and the size it is set in.
                if let text = ui.text {
                    textSection(text, ui)
                    palette.divider.frame(height: 1)
                    textLinkRow(text)
                } else if let shape = ui.shape {
                    shapeTextSection(shape)
                }

            case "Image":
                if let image = ui.image { imageSection(image, ui) }

            case "Code":
                if let code = ui.code { codeSection(code) }

            case "Terminal":
                if let terminal = ui.terminal { terminalSection(terminal) }

            case "Diagram":
                if let diagram = ui.diagram { diagramSection(diagram) }

            case "Equation":
                if let equation = ui.equation { equationSection(equation) }

            case "Gallery":
                if let gallery = ui.gallery { gallerySection(gallery) }

            default:
                EmptyView()
            }

            // A text box carries its own link, drawn with its text above; this
            // is the element link the other two kinds that hold one wear, a
            // shape (on its Text segment) and an image.
            if ui.text == nil, let link = ui.link {
                palette.divider.frame(height: 1)
                linkSection(link, ui)
            }
        }
    }

    // MARK: Arrange

    /// Where the element sits, in the stack and on the slide. Top to bottom the
    /// way Keynote orders it: z-order, align, size, position, rotate, and the
    /// two button pairs at the foot.
    ///
    /// Everything but Lock/Unlock is dead on a locked element, which is the same
    /// deal the other segments take: the pair is the only way back in.
    func arrangeSegment(_ ui: Chrome, _ element: Selection) -> some View {
        VStack(alignment: .leading, spacing: Layout.panelPadding) {
            VStack(alignment: .leading, spacing: Layout.panelPadding) {
                zOrderSection(ui)
                palette.divider.frame(height: 1)
                alignSection(ui)
                palette.divider.frame(height: 1)
                sizeSection(element)
                palette.divider.frame(height: 1)
                positionSection(element)
                palette.divider.frame(height: 1)
                rotateSection(element)
            }
            .disabled(element.locked)
            .opacity(element.locked ? 0.45 : 1)

            palette.divider.frame(height: 1)

            // Straight under Rotate rather than at the foot of the panel, the
            // way Keynote stacks them. Both pairs are always here, the
            // inapplicable half greyed: a panel whose buttons come and go is one
            // you have to read twice.
            VStack(spacing: 8) {
                HStack(spacing: 8) {
                    arrangeButton("Lock", enabled: !element.locked) {
                        host.toggleSelectedElementLock()
                    }
                    arrangeButton("Unlock", enabled: element.locked) {
                        host.toggleSelectedElementLock()
                    }
                }

                HStack(spacing: 8) {
                    arrangeButton("Group", enabled: ui.canGroup) { host.groupSelection() }
                    arrangeButton("Ungroup", enabled: ui.canUngroup) { host.ungroupSelection() }
                }
            }

            Spacer(minLength: 0)
        }
    }

    /// The stack, as two pairs in one row: the ends of it, then one step each
    /// way. Back and Backward share a flag and Front and Forward the other,
    /// because a move that changes nothing is a move that has nowhere to go.
    func zOrderSection(_ ui: Chrome) -> some View {
        HStack(spacing: 10) {
            zOrderPair(
                ("Back", "square.3.layers.3d.bottom.filled", ZOrderMove.toback, ui.canSendBackward),
                ("Front", "square.3.layers.3d.top.filled", ZOrderMove.tofront, ui.canBringForward)
            )
            zOrderPair(
                ("Backwards", "square.2.layers.3d.bottom.filled", ZOrderMove.backward, ui.canSendBackward),
                ("Forward", "square.2.layers.3d.top.filled", ZOrderMove.forward, ui.canBringForward)
            )
        }
    }

    /// Two moves in one joined group, their captions under it rather than in
    /// it, the way Keynote writes Back and Front under the icons.
    func zOrderPair(
        _ left: (String, String, ZOrderMove, Bool),
        _ right: (String, String, ZOrderMove, Bool)
    ) -> some View {
        VStack(spacing: 3) {
            joinedIcons([
                IconCell(symbol: left.1, enabled: left.3, help: left.0) {
                    host.reorderSelectedElement(move: left.2)
                },
                IconCell(symbol: right.1, enabled: right.3, help: right.0) {
                    host.reorderSelectedElement(move: right.2)
                },
            ])

            HStack(spacing: 0) {
                fieldCaption(left.0).opacity(left.3 ? 1 : 0.45)
                fieldCaption(right.0).opacity(right.3 ? 1 : 0.45)
            }
        }
    }

    /// Align and Distribute as menu buttons, off the same entries the Arrange
    /// menu is built from: one statement of what aligning means, rendered twice.
    /// A lone element aligns to the slide, so Align needs one; Distribute needs
    /// three, since two elements have no gap between them to equalize.
    func alignSection(_ ui: Chrome) -> some View {
        let entries = arrangeEntries(ArrangeFacts(ui), host)
        return HStack(spacing: 8) {
            menuButton("Align", entries.first { $0.title == "Align Objects" })
            menuButton("Distribute", entries.first { $0.title == "Distribute Objects" })
        }
    }

    /// One of those two: the submenu's children, dropped under a bordered
    /// button. A missing entry is a dead button rather than a crash, the way an
    /// unknown placeholder role still draws one.
    func menuButton(_ label: String, _ entry: MenuEntry?) -> some View {
        let enabled = entry?.enabled == true
        let shape = RoundedRectangle(cornerRadius: Inspect.radius, style: .continuous)
        return PopUpButton(entries: entry?.children ?? [], enabled: enabled) {
            HStack(spacing: 5) {
                Text(label)
                    .font(.system(size: Inspect.text))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                Spacer(minLength: 2)
                // One chevron, not the popup's pair: these open a list of verbs
                // rather than show which of them is picked.
                Image(systemName: "chevron.down")
                    .font(.system(size: 9, weight: .semibold))
            }
            .foregroundStyle(enabled ? palette.text : palette.faint)
            .padding(.horizontal, 9)
            .frame(maxWidth: .infinity)
            .frame(height: Inspect.control)
            .background(palette.inspectorControl, in: shape)
            .contentShape(shape)
        }
    }

    /// Two fields side by side with their names written underneath, which is
    /// where Keynote puts them: the row reads as a pair of measurements rather
    /// than as two labelled controls.
    func sizeSection(_ element: Selection) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Size")

            // Right-aligned with a gap at the left, the way Keynote lays the
            // two measurements out: fixed fields rather than stretched ones.
            HStack(spacing: 8) {
                Spacer(minLength: 0)
                captionedField("Width") {
                    ValueField(
                        label: "",
                        value: element.width,
                        palette: palette,
                        unit: "pt",
                        minimum: 1
                    ) { setFrame(element, width: $0) }
                    .frame(width: Inspect.field + Inspect.stepper + 5)
                }
                captionedField("Height") {
                    ValueField(
                        label: "",
                        value: element.height,
                        palette: palette,
                        unit: "pt",
                        minimum: 1
                    ) { setFrame(element, height: $0) }
                    .frame(width: Inspect.field + Inspect.stepper + 5)
                }
            }
        }
    }

    func positionSection(_ element: Selection) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Position")

            HStack(spacing: 8) {
                Spacer(minLength: 0)
                captionedField("X") {
                    ValueField(label: "", value: element.x, palette: palette, unit: "pt") {
                        setFrame(element, x: $0)
                    }
                    .frame(width: Inspect.field + Inspect.stepper + 5)
                }
                captionedField("Y") {
                    ValueField(label: "", value: element.y, palette: palette, unit: "pt") {
                        setFrame(element, y: $0)
                    }
                    .frame(width: Inspect.field + Inspect.stepper + 5)
                }
            }
        }
    }

    /// The dial, the angle, and the two flips, each captioned underneath. The
    /// dial is a drag: round the circle is how an angle is set by hand.
    func rotateSection(_ element: Selection) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Rotate")

            HStack(alignment: .top, spacing: 10) {
                RotateDial(degrees: element.rotation, palette: palette) {
                    host.setSelectedElementRotation(degrees: Float($0))
                }
                .padding(.top, 1)

                captionedField("Angle") {
                    ValueField(
                        label: "",
                        value: element.rotation,
                        palette: palette,
                        unit: "\u{00B0}"
                    ) { host.setSelectedElementRotation(degrees: Float($0)) }
                }

                // Two separate buttons rather than a joined pair: the flips are
                // two verbs, not a choice between two states.
                VStack(spacing: 3) {
                    HStack(spacing: 6) {
                        flipButton(
                            "arrow.left.and.right.righttriangle.left.righttriangle.right.fill",
                            help: "Flip Horizontally"
                        ) { host.flipSelectedElement(axis: FlipAxis.horizontal) }

                        flipButton(
                            "arrow.up.and.down.righttriangle.up.righttriangle.down.fill",
                            help: "Flip Vertically"
                        ) { host.flipSelectedElement(axis: FlipAxis.vertical) }
                    }

                    fieldCaption("Flip")
                }
            }
        }
    }

    /// A control with its name under it rather than beside it.
    func captionedField<Content: View>(
        _ caption: String,
        @ViewBuilder _ content: () -> Content
    ) -> some View {
        VStack(spacing: 3) {
            content()
            fieldCaption(caption)
        }
        // As wide as the field it captions and no wider, so a row of them sits
        // where it is put rather than spreading to fill.
        .fixedSize(horizontal: true, vertical: false)
    }

    /// A plain labelled button of the Arrange pairs' size, greyed when its verb
    /// has nothing to do rather than taken away.
    /// A button with nothing to do keeps its fill and greys only its label, the
    /// way Keynote's Unlock and Ungroup do. `.disabled` would fade the fill
    /// with it and leave the pair looking like two words on the panel, so the
    /// verb is guarded in the action instead.
    func arrangeButton(
        _ label: String,
        enabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: Inspect.radius, style: .continuous)
        return Button { if enabled { action() } } label: {
            Text(label)
                .font(.system(size: Inspect.text))
                .foregroundStyle(enabled ? palette.inspectorTitle : palette.faint)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .frame(maxWidth: .infinity)
                .frame(height: Inspect.button)
                .background(
                    enabled ? palette.inspectorControl : palette.inspectorControlOff,
                    in: shape
                )
                .contentShape(shape)
        }
        .buttonStyle(.plain)
    }

    // MARK: Slide formatting

    /// What Format shows with nothing selected: the slide itself. Moved here
    /// from the Document tab, which now holds only what belongs to the whole
    /// deck.
    ///
    /// Layout mode came with it rather than staying behind: one rule, that
    /// Format with nothing selected formats whatever the navigator is showing,
    /// is shorter than two, and a layout's name and placeholders are as much
    /// "this slide" as a slide's own background is.
    @ViewBuilder func slideFormatPanel(_ ui: Chrome) -> some View {
        GeometryReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: Layout.panelPadding) {
                    if ui.editingLayouts {
                        layoutNameSection(ui)
                        palette.divider.frame(height: 1)
                        placeholdersSection(ui)
                        palette.divider.frame(height: 1)
                        backgroundSection(ui)
                        Spacer(minLength: 0)
                        panelButton("Done", symbol: "checkmark") { host.exitSlideLayouts() }
                    } else {
                        slideLayoutCard(ui)
                        reapplyLayoutButton(ui)
                        appearanceSection(ui)
                        palette.divider.frame(height: 1)
                        backgroundSection(ui)
                        Spacer(minLength: 0)
                        editLayoutButton
                    }
                }
                .padding(Layout.panelPadding)
                .frame(maxWidth: .infinity, minHeight: proxy.size.height, alignment: .top)
            }
            .scrollContentBackground(.hidden)
        }
    }

    // MARK: Disclosure sections

    /// A collapsible section: chevron, title, and a summary at the right that
    /// says what is inside without opening it. Open iff the editor says so,
    /// which is why the click goes through the loop rather than into a local
    /// flag: the expansion outlives the selection that opened it.
    func disclosure<Summary: View, Content: View>(
        _ section: String,
        _ ui: Chrome,
        title: String? = nil,
        @ViewBuilder summary: () -> Summary,
        @ViewBuilder body: () -> Content
    ) -> some View {
        let open = ui.expandedSections.contains(section)
        return VStack(alignment: .leading, spacing: 9) {
            Button { host.toggleInspectorSection(name: section) } label: {
                HStack(spacing: 8) {
                    Image(systemName: open ? "chevron.down" : "chevron.right")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(palette.subtle)
                        .frame(width: 10)
                    Text(title ?? section)
                        .font(.system(size: Inspect.text, weight: .semibold))
                        .foregroundStyle(palette.inspectorTitle)
                    Spacer(minLength: 8)
                    summary()
                }
                .frame(height: Inspect.wellHeight)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if open { body() }
        }
    }

    /// What the Fill header shows: the colour, its gradient, or the struck-out
    /// well that means none.
    @ViewBuilder func fillPreview(_ shape: ShapeFormat) -> some View {
        if shape.hasGradient {
            previewWell {
                LinearGradient(
                    colors: [Color(argb: shape.gradientStart), Color(argb: shape.gradientEnd)],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            }
        } else if shape.fill == 0 {
            nonePreview
        } else {
            previewWell { Color(argb: shape.fill) }
        }
    }

    /// What the Border header shows: the line in its own colour and width, or
    /// none when there is nothing to draw.
    @ViewBuilder func borderPreview(_ shape: ShapeFormat) -> some View {
        if shape.strokeWidth <= 0 {
            nonePreview
        } else {
            previewWell { palette.inspectorControl }
                .overlay {
                    Rectangle()
                        .fill(Color(argb: shape.strokeColor))
                        .frame(height: max(1, min(shape.strokeWidth, 6)))
                        .padding(.horizontal, 6)
                }
        }
    }

    /// What the Shadow header shows: a square dropping one, or none.
    @ViewBuilder func shadowPreview(_ shape: ShapeFormat) -> some View {
        if shape.hasShadow {
            previewWell { palette.inspectorControl }
                .overlay {
                    RoundedRectangle(cornerRadius: 2, style: .continuous)
                        .fill(Color(argb: shape.fill))
                        .frame(width: 18, height: 10)
                        .shadow(color: Color(argb: shape.shadowColor).opacity(0.7), radius: 2, y: 1)
                }
        } else {
            nonePreview
        }
    }

    /// The grey well with a red diagonal Keynote draws for "none".
    var nonePreview: some View {
        previewWell { palette.track }
            .overlay {
                GeometryReader { proxy in
                    Path { path in
                        path.move(to: CGPoint(x: 0, y: proxy.size.height))
                        path.addLine(to: CGPoint(x: proxy.size.width, y: 0))
                    }
                    .stroke(Color(rgb: 0xC0392B), lineWidth: 1)
                }
            }
    }

    /// The shape every header summary is drawn in: a small rounded well at the
    /// right of the row, so the five of them line up whatever they hold.
    func previewWell<S: ShapeStyle>(_ fill: () -> S) -> some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return shape
            .fill(fill())
            .frame(width: Inspect.wellWidth, height: Inspect.wellHeight)
            .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
    }
}

/// The style grid's pager: chevrons at the edges, the grid between them, the
/// bold title under it and a dot per page. Which page is showing is view-local,
/// so it lives in here rather than in the window's state.
struct StylePager<Content: View>: View {
    let pages: Int
    let title: String
    let palette: Palette
    @ViewBuilder let content: (Int) -> Content

    @State private var page = 0

    var body: some View {
        let current = min(page, max(0, pages - 1))
        return VStack(spacing: 6) {
            HStack(spacing: 2) {
                chevron("chevron.left", enabled: current > 0) { page = current - 1 }
                // The grid between the chevrons takes everything that is left:
                // a flexible column's ideal width is tiny, so without this the
                // swatches shrink to pills in the middle of the row.
                content(current)
                    .frame(maxWidth: .infinity)
                chevron("chevron.right", enabled: current < pages - 1) { page = current + 1 }
            }

            Text(title)
                .font(.system(size: Inspect.text, weight: .bold))
                .foregroundStyle(palette.text)

            if pages > 1 {
                HStack(spacing: 5) {
                    ForEach(0..<pages, id: \.self) { index in
                        Circle()
                            .fill(index == current ? palette.subtle : palette.track)
                            .frame(width: 5, height: 5)
                    }
                }
            }
        }
    }

    private func chevron(
        _ symbol: String,
        enabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(enabled ? palette.subtle : palette.faint)
                .frame(width: 12, height: 40)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// The angle dial: a grey circle with a spoke, dragged round to turn the
/// element. The drag rides on this view's own value and commits per sample the
/// way the canvas rotate handle does, so the field beside it keeps up.
struct RotateDial: View {
    let degrees: Double
    let palette: Palette
    let onCommit: (Double) -> Void

    private static let side: CGFloat = 26

    var body: some View {
        Circle()
            .fill(palette.inspectorControl)
            .frame(width: Self.side, height: Self.side)
            .overlay {
                // A short dash at the rim rather than a spoke from the middle,
                // which is how Keynote's dial reads the angle back: 0 is up,
                // and the angle turns clockwise, as the document means it.
                Capsule()
                    .fill(palette.inspectorCaption)
                    .frame(width: 1.5, height: 5)
                    .offset(y: -(Self.side / 2) + 4)
                    .rotationEffect(.degrees(degrees))
            }
            .contentShape(Circle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let centre = Self.side / 2
                        let dx = value.location.x - centre
                        let dy = value.location.y - centre
                        guard abs(dx) > 0.01 || abs(dy) > 0.01 else { return }
                        let radians = atan2(dx, -dy)
                        let turned = (radians * 180 / .pi + 360).truncatingRemainder(dividingBy: 360)
                        onCommit(turned.rounded())
                    }
            )
    }
}

// MARK: - Pop-up buttons

/// A button of the panel's own making that drops an AppKit menu under itself.
///
/// SwiftUI's `Menu` takes a label it recognises as a title and draws it the
/// system's way, which is how the accent Add an Effect button and the two
/// Arrange popups came out as bare left-aligned text. An NSMenu popped under an
/// anchor keeps whatever is drawn here, and the entries are the same
/// `MenuEntry` list the menu bar and the canvas menu are built from.
struct PopUpButton<Label: View>: View {
    let entries: [MenuEntry]
    var enabled: Bool = true
    @ViewBuilder let label: () -> Label

    @StateObject private var anchor = MenuAnchor()

    var body: some View {
        Button { anchor.pop(entries) } label: { label() }
            .buttonStyle(.plain)
            .disabled(!enabled || entries.isEmpty)
            .background {
                MenuAnchorView(anchor: anchor)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .allowsHitTesting(false)
            }
    }
}

/// The view a pop-up menu is measured and positioned against, which is the
/// button itself: the menu lines its left edge up and hangs below it.
final class MenuAnchor: ObservableObject {
    weak var view: NSView?

    func pop(_ entries: [MenuEntry]) {
        guard let view, view.window != nil else { return }
        let menu = nsMenu(entries)
        menu.minimumWidth = max(view.bounds.width, 120)
        // A hosted view may be flipped or not, and the two put the bottom edge
        // at opposite ends of the y axis.
        let below = view.isFlipped ? view.bounds.height + 2 : -2
        let at = NSPoint(x: 0, y: below)
        // The click is still being handled, so the menu's tracking loop waits
        // for the next turn of the main queue, the way the canvas menu does.
        DispatchQueue.main.async { menu.popUp(positioning: nil, at: at, in: view) }
    }
}

/// A zero-business NSView behind the button, there to be an anchor and nothing
/// else: it never takes a click, so the SwiftUI button above it still gets them.
struct MenuAnchorView: NSViewRepresentable {
    let anchor: MenuAnchor

    func makeNSView(context: Context) -> NSView {
        let view = PassThroughView()
        anchor.view = view
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {
        anchor.view = nsView
    }
}

final class PassThroughView: NSView {
    override func hitTest(_ point: NSPoint) -> NSView? { nil }
}
