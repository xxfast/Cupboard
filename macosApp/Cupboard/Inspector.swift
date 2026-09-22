import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Inspector

/// A SwiftUI colour as the document model's packed ARGB, the other way round
/// from `Color(argb:)`. Through sRGB on the way, since that is the space the
/// stored number means and a picked colour may arrive in another.
func packedArgb(_ color: Color) -> Int64 {
    let srgb = NSColor(color).usingColorSpace(.sRGB) ?? .white
    func channel(_ value: CGFloat) -> Int64 { Int64((value * 255).rounded()) }
    return channel(srgb.alphaComponent) << 24
        | channel(srgb.redComponent) << 16
        | channel(srgb.greenComponent) << 8
        | channel(srgb.blueComponent)
}

enum BuildSpace {
    static let name = "buildOrder"
}

/// The inspector's measurements, in points, read off the Keynote captures in
/// `docs/keynote` (2x, so every pixel there is half a point here). Stated once
/// so the controls cannot drift apart: the panel is a column of things that all
/// have to line up with each other.
enum Inspect {
    /// The column's side padding, and so the width every full-width control runs to.
    static let side: CGFloat = 16
    /// Popups, fields, joined segment groups and the small buttons.
    static let control: CGFloat = 24
    /// The full-width grey buttons: Lock, Build Order, Edit Slide Layout.
    static let button: CGFloat = 28
    /// The panel's own segmented control, which is taller than the inner ones.
    static let track: CGFloat = 28
    /// A numeric field, and the stepper that sits outside its right edge.
    static let field: CGFloat = 60
    static let stepper: CGFloat = 20
    /// The well a disclosure header shows its summary in.
    static let wellWidth: CGFloat = 65
    static let wellHeight: CGFloat = 22
    static let radius: CGFloat = 6
    /// Body type, which is nearly everything: labels, popups, buttons, fields.
    static let text: CGFloat = 13
    /// The caption under a field, and under the Arrange icon pairs.
    static let caption: CGFloat = 11
    /// The gap between one section and the next, hairline included.
    static let section: CGFloat = 14
}

/// Where each build row sits in [BuildSpace], by its place in the order. Keyed
/// by index rather than by element: a build is not its element, and one element
/// may hold several of them.
struct BuildRowFrames: PreferenceKey {
    static let defaultValue: [Int: CGRect] = [:]

    static func reduce(value: inout [Int: CGRect], nextValue: () -> [Int: CGRect]) {
        value.merge(nextValue()) { _, latest in latest }
    }
}

/// The inspector's own material, which is not the app's general chrome: the
/// panel is a near-white grey and its controls a clearly darker grey, sampled
/// off the Keynote captures in `docs/keynote` rather than guessed. The dark
/// values are the same relationship inverted, since Keynote has no dark panel
/// to sample.
///
/// An extension rather than new palette fields: the palette is the app's, and
/// only the inspector wants these.
extension Palette {
    /// Which of the two palettes this is, asked of the one field that cannot
    /// coincide between them.
    var isDark: Bool { panel == Palette.dark.panel }

    /// #F2F4F6, flat: Keynote's inspector is a panel, not a glass.
    var inspectorPanel: Color { isDark ? Color(rgb: 0x2A2A2E) : Color(rgb: 0xF2F4F6) }

    /// #E3E5E7: popups, buttons, the segmented track, the dial, the joined
    /// icon groups. The one fill that makes a control look like a control.
    var inspectorControl: Color { isDark ? Color(rgb: 0x3D3D43) : Color(rgb: 0xE3E5E7) }

    /// #DADCDE: a numeric field, a shade darker than the controls around it.
    var inspectorField: Color { isDark ? Color(rgb: 0x37373C) : Color(rgb: 0xDADCDE) }

    /// #EAEBED: the fill of a button with nothing to do, which fades towards
    /// the panel rather than going flat.
    var inspectorControlOff: Color { isDark ? Color(rgb: 0x333338) : Color(rgb: 0xEAEBED) }

    /// #3A3A3C: section titles and disclosure titles, which are dark and
    /// semibold in Keynote rather than the pale grey our chrome labels use.
    var inspectorTitle: Color { isDark ? Color(rgb: 0xE2E2E6) : Color(rgb: 0x3A3A3C) }

    /// #4C4C4C: the captions under fields, dark and semibold too.
    var inspectorCaption: Color { isDark ? Color(rgb: 0xC6C6CC) : Color(rgb: 0x4C4C4C) }
}

/// One cell of a joined icon group: a glyph or a short word, whether it is on,
/// and what clicking it does. A value, so a group is stated as a list.
struct IconCell {
    var symbol: String? = nil
    var title: String? = nil
    var on: Bool = false
    /// A cell with nowhere to go greys on its own, the way Keynote greys Front
    /// and Forward on the frontmost object while Back and Backwards stay live.
    var enabled: Bool = true
    var help: String = ""
    let action: () -> Void
}

/// A build row on the move, and the gap it is over. SwiftUI state, like the
/// navigator's drag: nothing but the drop is the document's business.
struct BuildDrag: Equatable {
    /// The row being carried, by its place in the order.
    let index: Int
    /// The gap the drop lands in, 0 for the one above the first row.
    let gap: Int
    /// Where the drop line draws, in [BuildSpace].
    let lineY: CGFloat
    /// How far the pointer has carried the row: it travels with the cursor.
    let translationY: CGFloat

    /// The place the build ends up at, which is one back from the gap whenever
    /// the row has left a hole above it.
    var destination: Int { gap > index ? gap - 1 : gap }
}

extension EditorView {
    /// Header is bare: Share and the tabs moved to the toolbar, which floats
    /// over this glass, so the panel only owns what sits under them, which is a
    /// segmented control when the selection offers segments and a title when it
    /// does not.
    func inspector(_ ui: Chrome) -> some View {
        VStack(spacing: 0) {
            Color.clear.frame(height: Layout.header)

            if ui.tab == InspectorTab.document {
                panelTitle(ui.editingLayouts ? "Layout" : "Document")
                documentPanel(ui)
            } else if ui.tab == InspectorTab.format {
                formatPanel(ui)
            } else {
                animatePanel(ui)
            }
        }
        .frame(width: Layout.inspector)
        .frame(maxHeight: .infinity)
        // Flat and opaque, the way Keynote's panel is: the glass this used to
        // wear took its colour off whatever the canvas was showing, which is
        // what made every control fill disappear into it.
        .background(palette.inspectorPanel)
        .overlay(alignment: .leading) { palette.divider.frame(width: 1) }
    }

    /// The panel's title line, for the states that have a word rather than a
    /// segmented control: slide formatting, the transitions, the Document tab.
    func panelTitle(_ title: String) -> some View {
        VStack(spacing: 10) {
            Text(title)
                .font(.system(size: Inspect.text))
                .foregroundStyle(palette.subtle)
                .frame(maxWidth: .infinity)
            palette.divider.frame(height: 1)
        }
        .padding(.top, 8)
        .padding(.horizontal, Inspect.side)
        .padding(.bottom, 12)
    }

    /// The full-width segmented control both tabs wear under the toolbar: the
    /// Format segments this selection offers, or the three Animate ones. It does
    /// not scroll; only the body under it does.
    func panelSegments(
        _ titles: [String],
        selected: String,
        onPick: @escaping (String) -> Void
    ) -> some View {
        HStack(spacing: 0) {
            ForEach(Array(titles.enumerated()), id: \.offset) { index, title in
                // Keynote hairlines only the seam between two unselected
                // segments: the accent capsule is its own edge.
                if index > 0 {
                    let touches = titles[index - 1] == selected || title == selected
                    (touches ? Color.clear : palette.tabDivider)
                        .frame(width: 1, height: 16)
                }
                segment(Self.segmentLabel(title), on: title == selected) { onPick(title) }
            }
        }
        .padding(2)
        .frame(height: Inspect.track)
        .background(palette.inspectorControl, in: Capsule())
        .padding(.top, 6)
        .padding(.horizontal, 8)
        .padding(.bottom, 12)
    }

    /// Kotlin's enum names are the labels, so the only thing to do to one is put
    /// the space back into the two that are two words.
    static func segmentLabel(_ name: String) -> String {
        switch name {
        case "BuildIn": return "Build In"
        case "BuildOut": return "Build Out"
        default: return name
        }
    }

    // MARK: Format panel

    /// The segmented control, then whichever segment is on. Everything shown
    /// here comes back through `states`, so a typed value, a canvas drag and an
    /// undo all land in the same place.
    ///
    /// Nothing selected is not an empty state: Format falls back to formatting
    /// the slide, the way Keynote's does.
    @ViewBuilder func formatPanel(_ ui: Chrome) -> some View {
        if let element = ui.element, !ui.formatSegments.isEmpty {
            VStack(spacing: 0) {
                panelSegments(ui.formatSegments, selected: ui.activeFormatSegment) {
                    host.selectFormatSegment(name: $0)
                }

                // The controls speak for the primary, so with more than one
                // selected the panel has to say what else they are editing.
                if ui.selectionCount > 1 {
                    Text("\(ui.selectionCount) selected")
                        .font(.system(size: 11))
                        .foregroundStyle(palette.faint)
                        .frame(maxWidth: .infinity)
                        .padding(.bottom, 8)
                }

                // Pinned above the scroll area, the way Keynote pins its style
                // grid: only the sections below it scroll.
                if ui.activeFormatSegment == "Style", let shape = ui.shape, !shape.isLine {
                    styleStrip(ui.objectStyles)
                        .padding(.horizontal, Layout.panelPadding)
                        .padding(.bottom, Layout.panelPadding)
                        .disabled(element.locked)
                        .opacity(element.locked ? 0.45 : 1)
                    palette.divider.frame(height: 1)
                }

                // Scrolls so a tall segment (Arrange, or a Style with every
                // section open) can't grow the window past its own edge.
                GeometryReader { proxy in
                    ScrollView {
                        formatSegmentBody(ui, element)
                            .padding(Layout.panelPadding)
                            .frame(maxWidth: .infinity, minHeight: proxy.size.height, alignment: .top)
                    }
                    .scrollContentBackground(.hidden)
                }
            }
        } else {
            panelTitle(ui.editingLayouts ? "Layout" : "Slide")
            slideFormatPanel(ui)
        }
    }

    // MARK: Text

    /// The text box's own style, above Position & Size. Every control writes to
    /// the whole selection and reads back off the primary, so what it shows is
    /// what the document holds and never a local copy.
    ///
    /// Whole-box, all of it: a list, a weight and a colour are properties of the
    /// element, not of a range, which is what the document model says and what
    /// keeps the caret's text one plain string.
    func textSection(_ text: TextFormat, _ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            textStyleRows(text)

            palette.divider.frame(height: 1)

            // Collapsible from here down, the way Keynote's Spacing and
            // Bullets & Lists are: the header alone says what they are set to.
            spacingSection(text, ui)

            palette.divider.frame(height: 1)

            listsSection(text, ui)
        }
    }

    /// The rows that are always out: what the text is set in, how it is dressed,
    /// what colour it is and which edge it lines up on.
    func textStyleRows(_ text: TextFormat) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Font")

            stylePopup(Self.fontTitles, selected: Self.fontIndex(text.font)) { index in
                host.setSelectedTextFont(font: Self.fonts[index])
            }

            HStack(spacing: 8) {
                stylePopup(Self.weightTitles, selected: Self.weightIndex(text.weight)) { index in
                    host.setSelectedTextWeight(weight: Int32(Self.weights[index]))
                }

                ValueField(
                    label: "",
                    value: text.size,
                    palette: palette,
                    unit: "pt",
                    minimum: 1
                ) { host.setSelectedTextSize(size: Float($0)) }
                .frame(width: Inspect.field + Inspect.stepper + 5)
            }

            // One joined group, the way Keynote draws them, rather than four
            // squares with air between.
            joinedIcons([
                IconCell(symbol: "bold", on: text.isBold, help: "Bold") {
                    host.toggleSelectedTextBold()
                },
                IconCell(symbol: "italic", on: text.italic, help: "Italic") {
                    host.toggleSelectedTextItalic()
                },
                IconCell(symbol: "underline", on: text.underline, help: "Underline") {
                    host.toggleSelectedTextUnderline()
                },
                IconCell(symbol: "strikethrough", on: text.strikethrough, help: "Strikethrough") {
                    host.toggleSelectedTextStrikethrough()
                },
            ])

            // The system colour panel is live: it commits on every sample it
            // sends, so a slow drag through it spends an undo entry per sample.
            // Same-colour writes cost nothing (the core drops an edit that
            // changes nothing), which takes the worst of it off.
            colorWell("Text Colour", argb: text.color) {
                host.setSelectedTextColor(argb: $0)
            }

            joinedIcons([
                IconCell(
                    symbol: "text.alignleft",
                    on: text.align == TextAlign.start,
                    help: "Align Left"
                ) { host.setSelectedTextAlign(align: TextAlign.start) },
                IconCell(
                    symbol: "text.aligncenter",
                    on: text.align == TextAlign.center,
                    help: "Align Centre"
                ) { host.setSelectedTextAlign(align: TextAlign.center) },
                IconCell(
                    symbol: "text.alignright",
                    on: text.align == TextAlign.end,
                    help: "Align Right"
                ) { host.setSelectedTextAlign(align: TextAlign.end) },
            ])
        }
    }

    /// How far apart the lines sit, as a multiple of the type size. Collapsed,
    /// the header says the multiple, which is the whole of what is in here.
    func spacingSection(_ text: TextFormat, _ ui: Chrome) -> some View {
        disclosure("Spacing", ui) {
            Text(String(format: "%.2f", text.lineHeight))
                .font(.system(size: Inspect.text))
                .foregroundStyle(palette.inspectorCaption)
        } body: {
            HStack(spacing: 8) {
                rowLabel("Lines")
                Spacer(minLength: 0)
                ValueField(
                    label: "",
                    value: text.lineHeight,
                    palette: palette,
                    decimals: 2,
                    step: 0.1,
                    minimum: 0.5
                ) { host.setSelectedTextLineHeight(lineHeight: Float($0)) }
                .frame(width: 92)
            }
        }
    }

    /// What marks each paragraph. Collapsed, the header names the kind.
    func listsSection(_ text: TextFormat, _ ui: Chrome) -> some View {
        disclosure("Lists", ui) {
            Text(Self.listTitles[Self.listIndex(text.list)])
                .font(.system(size: Inspect.text))
                .foregroundStyle(palette.inspectorCaption)
        } body: {
            stylePopup(Self.listTitles, selected: Self.listIndex(text.list)) { index in
                host.setSelectedTextList(style: Self.lists[index])
            }
        }
    }

    /// Where a text box takes the show when it is clicked, which is the text
    /// box's own link rather than the element link the other kinds carry.
    func textLinkRow(_ text: TextFormat) -> some View {
        StringField(placeholder: "Link", value: text.link, palette: palette) {
            host.setSelectedTextLink(link: $0.isEmpty ? nil : $0)
        }
    }

    /// An AppKit popup button over a fixed list. Indices rather than the Kotlin
    /// enums: a tag has to be Hashable, and what these pick from is a list this
    /// file states anyway.
    func stylePopup(
        _ titles: [String],
        selected: Int,
        onPick: @escaping (Int) -> Void
    ) -> some View {
        let entries = titles.enumerated().map { index, title in
            MenuEntry(title: title) { onPick(index) }
        }
        return PopUpButton(entries: entries) {
            popupFace(titles.indices.contains(selected) ? titles[selected] : "")
        }
    }

    /// A popup's face: the pick at the left, the up/down chevron pair at the
    /// right, in a grey filled rounded rect at control height.
    func popupFace(_ title: String) -> some View {
        let shape = RoundedRectangle(cornerRadius: Inspect.radius, style: .continuous)
        return HStack(spacing: 6) {
            Text(title)
                .font(.system(size: Inspect.text))
                .foregroundStyle(palette.text)
                .lineLimit(1)
                .truncationMode(.middle)
            Spacer(minLength: 2)
            chevronPair
        }
        .padding(.horizontal, 9)
        .frame(maxWidth: .infinity)
        .frame(height: Inspect.control)
        .background(palette.inspectorControl, in: shape)
        .contentShape(shape)
    }

    /// The stacked pair every popup and stepper wears at its right.
    var chevronPair: some View {
        VStack(spacing: 1) {
            Image(systemName: "chevron.up")
            Image(systemName: "chevron.down")
        }
        .font(.system(size: 8, weight: .heavy))
        .foregroundStyle(palette.inspectorTitle)
    }

    /// Generic families only, the document model's: nothing bundles font files
    /// yet, so a named face would render as one thing here and another wherever
    /// it is missing.
    static let fonts: [TextFont] = [TextFont.sans, TextFont.serif, TextFont.monospace]
    static let fontTitles = ["Sans", "Serif", "Monospace"]

    static func fontIndex(_ font: TextFont) -> Int {
        fonts.firstIndex { $0 == font } ?? 0
    }

    /// The five the popup offers. Bold is a point on this scale, so the B toggle
    /// and this popup are two views of one number.
    static let weights = [300, 400, 500, 600, 700]
    static let weightTitles = ["Light", "Regular", "Medium", "Semibold", "Bold"]

    /// The nearest offered weight, so a 450 imported from elsewhere marks
    /// something rather than leaving the popup blank.
    static func weightIndex(_ weight: Int) -> Int {
        weights.indices.min { abs(weights[$0] - weight) < abs(weights[$1] - weight) } ?? 1
    }

    static let lists: [CupboardCanvas.ListStyle] = [
        CupboardCanvas.ListStyle.none,
        CupboardCanvas.ListStyle.bullet,
        CupboardCanvas.ListStyle.numbered,
    ]
    static let listTitles = ["No List", "Bullet List", "Numbered List"]

    static func listIndex(_ style: CupboardCanvas.ListStyle) -> Int {
        lists.firstIndex { $0 == style } ?? 0
    }

    // MARK: Link

    /// Where the element takes the show when it is clicked. Every kind commits
    /// on the pick, url and slide included, so what the popup says is what the
    /// document holds rather than something waiting on a second control.
    ///
    /// The two kinds that carry a value show it: an address to open, or the
    /// slide to jump to. The rest are the ordinary walk, and have nothing else
    /// to say.
    @ViewBuilder func linkSection(_ link: LinkFormat, _ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Link")

            stylePopup(ui.linkKinds, selected: link.kindIndex) { index in
                host.setSelectedLink(kindIndex: Int32(index), url: link.url, slideId: link.slideId)
            }

            if link.kindIndex == Self.urlLinkKind {
                StringField(placeholder: "https://", value: link.url, palette: palette) { typed in
                    host.setSelectedLink(
                        kindIndex: Int32(Self.urlLinkKind),
                        url: typed,
                        slideId: link.slideId
                    )
                }
            }

            if link.kindIndex == Self.slideLinkKind {
                stylePopup(
                    ui.slideChoices.map(\.name),
                    selected: Self.slideChoiceIndex(link.slideId, in: ui.slideChoices)
                ) { index in
                    guard ui.slideChoices.indices.contains(index) else { return }
                    host.setSelectedLink(
                        kindIndex: Int32(Self.slideLinkKind),
                        url: link.url,
                        slideId: ui.slideChoices[index].id
                    )
                }
            }
        }
    }

    /// Where the two kinds that carry a value sit in `linkKinds`. Kotlin states
    /// the order and this reads it back: the position is the whole protocol, so
    /// it is spelled once, here.
    static let urlLinkKind = 6
    static let slideLinkKind = 7

    /// The slide the link points at, or the first: a deck always has one, and a
    /// popup marking nothing would read as a link pointing nowhere.
    static func slideChoiceIndex(_ id: String, in choices: [SlideChoice]) -> Int {
        choices.firstIndex { $0.id == id } ?? 0
    }

    // MARK: Shape

    /// The shape's own look, above Position & Size: what paints it, what outlines
    /// it, and the parts only some kinds have. Same contract as the Text section,
    /// read off the primary and written to every unlocked shape in the selection.
    ///
    /// The colour wells are the system panel, and it is live: it commits every
    /// sample it sends, so a slow drag through it spends an undo entry per
    /// sample. The text colour takes the same deal, with the same mitigation:
    /// the core drops a write that changes nothing.
    @ViewBuilder func shapeSection(_ shape: ShapeFormat, _ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            // A line is all stroke: there is no inside of it to paint.
            if !shape.isLine {
                fillSection(shape, ui)
                palette.divider.frame(height: 1)
            }

            borderSection(shape, ui)

            palette.divider.frame(height: 1)

            shadowSection(shape, ui)

            // A corner radius means nothing to any other kind, so the field is
            // not there to be typed into rather than there and inert.
            if shape.isRectangle {
                palette.divider.frame(height: 1)

                HStack(spacing: 8) {
                    rowLabel("Corner")
                    Spacer(minLength: 0)
                    ValueField(
                        label: "",
                        value: shape.cornerRadius,
                        palette: palette,
                        unit: "pt",
                        minimum: 0
                    ) { host.setSelectedShapeCornerRadius(radius: Float($0)) }
                    .frame(width: 92)
                }
            }
        }
    }

    /// What paints the shape: one colour, or a two-stop gradient. Switching to a
    /// kind the shape is not wearing commits it there and then, off the values it
    /// came back with, so the wells below always have something to show and never
    /// have to invent a colour.
    func fillSection(_ shape: ShapeFormat, _ ui: Chrome) -> some View {
        disclosure("Fill", ui) {
            fillPreview(shape)
        } body: {
            VStack(alignment: .leading, spacing: 9) {
                HStack(spacing: 2) {
                    segment("Color", on: !shape.hasGradient) { host.clearSelectedShapeGradient() }
                    segment("Gradient", on: shape.hasGradient) { setGradient(shape) }
                }
                .padding(2)
                .frame(height: Inspect.control + 4)
                .background(palette.inspectorControl, in: Capsule())

                if shape.hasGradient {
                    HStack(spacing: 8) {
                        colorWell("Start", argb: shape.gradientStart) {
                            setGradient(shape, start: $0)
                        }
                        Spacer(minLength: 0)
                        colorWell("End", argb: shape.gradientEnd) { setGradient(shape, end: $0) }
                    }
                    HStack(spacing: 8) {
                        rowLabel("Angle")
                        Spacer(minLength: 0)
                        ValueField(label: "", value: shape.gradientAngle, palette: palette, unit: "\u{00B0}") {
                            setGradient(shape, angle: $0)
                        }
                        .frame(width: 92)
                    }
                } else {
                    colorWell("Colour", argb: shape.fill) { host.setSelectedShapeFill(argb: $0) }
                }
            }
        }
    }

    /// What outlines the shape, which for a line is the line itself, so its two
    /// arrowheads sit in here rather than in a section of their own.
    func borderSection(_ shape: ShapeFormat, _ ui: Chrome) -> some View {
        disclosure("Border", ui, title: shape.isLine ? "Stroke" : "Border") {
            borderPreview(shape)
        } body: {
            VStack(alignment: .leading, spacing: 9) {
                HStack(spacing: 8) {
                    colorWell("Colour", argb: shape.strokeColor) {
                        host.setSelectedShapeStroke(color: $0, width: Float(shape.strokeWidth))
                    }
                    Spacer(minLength: 0)
                    ValueField(
                        label: "",
                        value: shape.strokeWidth,
                        palette: palette,
                        unit: "pt",
                        decimals: 1,
                        minimum: 0
                    ) { host.setSelectedShapeStroke(color: shape.strokeColor, width: Float($0)) }
                    .frame(width: 92)
                    .help("Border Width")
                }

                if shape.isLine {
                    HStack(spacing: 8) {
                        checkRow("Start", on: shape.startArrow) {
                            host.setSelectedShapeArrows(
                                start: !shape.startArrow,
                                end: shape.endArrow
                            )
                        }
                        checkRow("End", on: shape.endArrow) {
                            host.setSelectedShapeArrows(
                                start: shape.startArrow,
                                end: !shape.endArrow
                            )
                        }
                    }
                }
            }
        }
    }

    /// What the shape drops behind it. The checkbox is inside rather than on the
    /// header: the header's job is to say whether there is one, which the
    /// preview does without anything to click.
    func shadowSection(_ shape: ShapeFormat, _ ui: Chrome) -> some View {
        disclosure("Shadow", ui) {
            shadowPreview(shape)
        } body: {
            VStack(alignment: .leading, spacing: 9) {
                checkRow("Shadow", on: shape.hasShadow) {
                    host.setSelectedShapeShadow(
                        enabled: !shape.hasShadow,
                        color: shape.shadowColor,
                        blur: Float(shape.shadowBlur)
                    )
                }

                if shape.hasShadow {
                    HStack(spacing: 8) {
                        colorWell("Colour", argb: shape.shadowColor) {
                            host.setSelectedShapeShadow(
                                enabled: true,
                                color: $0,
                                blur: Float(shape.shadowBlur)
                            )
                        }
                        Spacer(minLength: 0)
                        ValueField(
                            label: "",
                            value: shape.shadowBlur,
                            palette: palette,
                            unit: "pt",
                            minimum: 0
                        ) {
                            host.setSelectedShapeShadow(
                                enabled: true,
                                color: shape.shadowColor,
                                blur: Float($0)
                            )
                        }
                        .frame(width: 92)
                        .help("Blur")
                    }
                }
            }
        }
    }

    /// A shape's label and the size it is set in: what the Text segment shows
    /// for a shape, which has a word written on it rather than a text style.
    func shapeTextSection(_ shape: ShapeFormat) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            StringField(placeholder: "Label", value: shape.label, palette: palette) {
                host.setSelectedShapeLabel(label: $0)
            }

            HStack(spacing: 8) {
                rowLabel("Label Size")
                Spacer(minLength: 0)
                ValueField(
                    label: "",
                    value: shape.labelSize,
                    palette: palette,
                    unit: "pt",
                    minimum: 1
                ) { host.setSelectedShapeLabelSize(size: Float($0)) }
                .frame(width: 92)
            }
        }
    }

    /// The deck's saved shape looks, as a wrapping row of swatches. A click
    /// dresses the whole selection in one, a right-click renames or drops one,
    /// and the button under them lifts the primary shape's look into the library.
    ///
    /// The ring marks the look the primary is already wearing, which Kotlin works
    /// out by appearance rather than by a stored id: a shape edited away from a
    /// style is wearing none, and the strip says so by ringing nothing.
    func styleStrip(_ styles: [ObjectStyleChoice]) -> some View {
        // Six to a page, three across, the way Keynote pages its grid. Which
        // page is showing is the pager's own business: which looks the deck
        // holds is the document's, which six are on screen is not.
        let column = GridItem(.flexible(), spacing: 8)

        return VStack(spacing: 8) {
            StylePager(pages: max(1, Int(ceil(Double(styles.count) / 6))), title: "Shape Styles", palette: palette) { page in
                LazyVGrid(columns: Array(repeating: column, count: 3), spacing: 8) {
                    ForEach(Array(styles.dropFirst(page * 6).prefix(6))) { style in
                        styleSwatch(style)
                    }
                }
            }

            panelButton("Save Style...") {
                saveStyleText = ""
                savingStyle = true
            }
        }
        // Both verbs that need a name take an alert, the way Save Theme does.
        // Rename carries the id it was opened on: the menu it came from is gone
        // by the time the field is typed into.
        .alert("Save Style", isPresented: $savingStyle) {
            TextField("Name", text: $saveStyleText)
            Button("Save") {
                let typed = saveStyleText.trimmingCharacters(in: .whitespaces)
                guard !typed.isEmpty else { return }
                host.saveObjectStyle(name: typed)
            }
            Button("Cancel", role: .cancel) {}
        }
        .alert("Rename Style", isPresented: $renamingStyle) {
            TextField("Name", text: $renameStyleText)
            Button("Rename") {
                let typed = renameStyleText.trimmingCharacters(in: .whitespaces)
                guard !typed.isEmpty else { return }
                host.renameObjectStyle(styleId: renameStyleId, name: typed)
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    /// One saved look, painted: the fill or its gradient, the border over it, and
    /// a soft drop shadow when the style carries one. The name is the tooltip,
    /// since a swatch has nowhere to write it.
    ///
    /// The corner is clamped rather than scaled: a swatch has no shape's width to
    /// scale a radius against, and square against rounded is the part of it worth
    /// showing at this size.
    func styleSwatch(_ style: ObjectStyleChoice) -> some View {
        let shape = RoundedRectangle(cornerRadius: 4, style: .continuous)
        return Button { host.applyObjectStyle(styleId: style.id) } label: {
            shape
                .fill(styleFill(style))
                // Landscape, filling its share of the width: a style swatch is
                // a patch of the look, not a shape. One frame, not two: a
                // height frame over a width frame proposes it no width.
                .frame(maxWidth: .infinity, minHeight: 47, maxHeight: 47)
                .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
                .overlay {
                    shape.inset(by: 0.5).stroke(
                        Color(argb: style.strokeColor),
                        lineWidth: max(1, min(style.strokeWidth, 3))
                    )
                }
                .shadow(color: style.hasShadow ? .black.opacity(0.5) : .clear, radius: 3, y: 1)
                .overlay {
                    if style.isCurrent { shape.inset(by: -2.5).stroke(palette.accent, lineWidth: 2) }
                }
                .contentShape(shape)
        }
        .buttonStyle(.plain)
        .help(style.name)
        .contextMenu {
            Button("Rename...") {
                renameStyleId = style.id
                renameStyleText = style.name
                renamingStyle = true
            }
            Button("Delete") { host.deleteObjectStyle(styleId: style.id) }
        }
    }

    /// What a swatch paints with: the style's gradient when it carries one, its
    /// flat fill when it does not. No angle at this size, so the stops run down.
    func styleFill(_ style: ObjectStyleChoice) -> AnyShapeStyle {
        guard style.hasGradient else { return AnyShapeStyle(Color(argb: style.fill)) }
        return AnyShapeStyle(
            LinearGradient(
                colors: [Color(argb: style.gradientStart), Color(argb: style.gradientEnd)],
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }

    /// A gradient commits whole, so a control that edits one of its three sends
    /// the other two back as they stand. No argument at all is the segment
    /// turning one on, which commits what the shape came back carrying.
    func setGradient(
        _ shape: ShapeFormat,
        start: Int64? = nil,
        end: Int64? = nil,
        angle: Double? = nil
    ) {
        host.setSelectedShapeGradient(
            start: start ?? shape.gradientStart,
            end: end ?? shape.gradientEnd,
            angle: Float(angle ?? shape.gradientAngle)
        )
    }

    /// A labelled colour well, the system picker at small size.
    func colorWell(
        _ label: String,
        argb: Int64,
        onPick: @escaping (Int64) -> Void
    ) -> some View {
        HStack(spacing: 8) {
            if !label.isEmpty {
                Text(label)
                    .font(.system(size: Inspect.text))
                    .foregroundStyle(palette.text)
            }
            Spacer(minLength: 0)
            ColorPicker(
                "",
                selection: Binding(
                    get: { Color(argb: argb) },
                    set: { onPick(packedArgb($0)) }
                ),
                supportsOpacity: true
            )
            .labelsHidden()
            .frame(width: Inspect.wellWidth, height: Inspect.wellHeight)
            // A white swatch on a near-white panel needs an edge to be a
            // swatch at all, which is what Keynote's well has.
            .overlay {
                RoundedRectangle(cornerRadius: 5, style: .continuous)
                    .inset(by: 0.5)
                    .stroke(palette.hairline, lineWidth: 1)
                    .allowsHitTesting(false)
            }
        }
    }

    // MARK: Code

    /// The code block's own style: what it is highlighted as, what palette it
    /// wears, and the two switches for the gutter and the long lines. Same
    /// contract as the Text and Shape sections, read off the primary and written
    /// to every unlocked code block in the selection.
    ///
    /// Both lists come off the host rather than being restated here: what
    /// highlights, what a palette is called, and what order they come in are the
    /// document's to say, the way the shape catalog's rows are.
    func codeSection(_ code: CodeFormat) -> some View {
        let languages = host.codeLanguages()
        return VStack(alignment: .leading, spacing: 9) {
            stylePopup(languages, selected: Self.languageIndex(code.language, in: languages)) {
                index in host.setCodeLanguage(language: languages[index])
            }

            HStack(spacing: 8) {
                rowLabel("Size")
                Spacer(minLength: 0)
                ValueField(
                    label: "",
                    value: code.size,
                    palette: palette,
                    unit: "pt",
                    minimum: 1
                ) { host.setCodeFontSize(size: Float($0)) }
                .frame(width: 92)
            }

            checkRow("Line Numbers", on: code.showLineNumbers) {
                host.setCodeLineNumbers(enabled: !code.showLineNumbers)
            }

            checkRow("Wrap", on: code.wrap) {
                host.setCodeWrap(enabled: !code.wrap)
            }
        }
    }

    /// The versions of the block's source, oldest first: what the canvas types
    /// into, and what a morph plays through. One row each, the one on show
    /// picked, over the list controls the gallery strip carries.
    ///
    /// Reordering is the two chevrons rather than a drag, unlike the build
    /// order's rows: these are a short list of labels, and the buttons are what
    /// the strip beside them already reorders with.
    func codeVersionsSection(_ code: CodeFormat) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Versions")

            VStack(spacing: 4) {
                ForEach(Array(0..<code.versionCount), id: \.self) { index in
                    CodeVersionRow(
                        index: index,
                        picked: index == code.shownVersion,
                        palette: palette
                    ) {
                        host.selectCodeVersion(index: Int32(index))
                    }
                }
            }

            HStack(spacing: 6) {
                galleryStep("plus", help: "Add Version") { host.addCodeVersion() }
                // The core keeps the last version standing, so the button that
                // would take it says so by greying.
                galleryStep("minus", help: "Remove Version") {
                    host.removeCodeVersion(index: Int32(code.shownVersion))
                }
                .disabled(code.versionCount < 2)
                galleryStep("chevron.up", help: "Move Up") {
                    host.moveCodeVersion(
                        from: Int32(code.shownVersion),
                        to: Int32(code.shownVersion - 1)
                    )
                }
                .disabled(code.shownVersion <= 0)
                galleryStep("chevron.down", help: "Move Down") {
                    host.moveCodeVersion(
                        from: Int32(code.shownVersion),
                        to: Int32(code.shownVersion + 1)
                    )
                }
                .disabled(code.shownVersion >= code.versionCount - 1)
                Spacer(minLength: 0)
            }
        }
    }

    /// One row of the Versions list: the badge and what the version is called.
    /// The build order row's look without its drag, since these reorder by
    /// button.
    struct CodeVersionRow: View {
        let index: Int
        /// This is the version the canvas shows and the caret types into.
        let picked: Bool
        let palette: Palette
        let onTap: () -> Void

        @State private var hovering = false

        private var shape: RoundedRectangle {
            RoundedRectangle(cornerRadius: 6, style: .continuous)
        }

        var body: some View {
            HStack(spacing: 9) {
                Text("\(index + 1)")
                    .font(.system(size: 10.5, weight: .bold))
                    .foregroundStyle(picked ? palette.accentText : palette.badgeOffText)
                    .frame(width: 17, height: 17)
                    .background(picked ? palette.accent : palette.badgeOff, in: Circle())

                Text("Version \(index + 1)")
                    .font(.system(size: 12))
                    .foregroundStyle(palette.text)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(EdgeInsets(top: 7, leading: 9, bottom: 7, trailing: 9))
            .background(fill, in: shape)
            .overlay {
                if picked { shape.inset(by: 0.5).stroke(palette.accent, lineWidth: 1) }
            }
            .contentShape(shape)
            .onTapGesture(perform: onTap)
            .onHover { hovering = $0 }
        }

        /// The build order row's fills: the accent at 22% for the picked one,
        /// the row fill lifted on hover for the rest.
        private var fill: Color {
            if picked { return palette.accent.opacity(0.22) }
            return hovering ? palette.rowHov : palette.rowBg
        }
    }

    /// The palette a code block is painted in, which is its look rather than its
    /// content, so it sits in Style while the language and the gutter sit in the
    /// Code segment.
    func codeThemeSection(_ code: CodeFormat) -> some View {
        let themes = host.codeThemes()
        return VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Theme")

            stylePopup(themes, selected: themes.firstIndex(of: code.theme) ?? 0) { index in
                host.setCodeTheme(theme: themes[index])
            }
        }
    }

    /// The row a language marks. Case-insensitive, because the model stores what
    /// it was given and documents on disk carry lowercase names. One the list
    /// doesn't have marks Plain, which is what it highlights as anyway.
    static func languageIndex(_ language: String, in languages: [String]) -> Int {
        languages.firstIndex { $0.caseInsensitiveCompare(language) == .orderedSame }
            ?? languages.firstIndex(of: "Plain")
            ?? 0
    }

    // MARK: Terminal

    /// The terminal's own style: what its title bar is called, what prefixes a
    /// line of input, the type size, and whether the bar is drawn at all. Same
    /// contract as the Code section, except the title, which is content and so
    /// goes to the primary alone.
    func terminalSection(_ terminal: TerminalFormat) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            StringField(placeholder: "Title", value: terminal.title, palette: palette) {
                host.setTerminalTitle(title: $0)
            }

            StringField(placeholder: "Prompt", value: terminal.prompt, palette: palette) {
                host.setTerminalPrompt(prompt: $0)
            }

            HStack(spacing: 8) {
                rowLabel("Size")
                Spacer(minLength: 0)
                ValueField(
                    label: "",
                    value: terminal.size,
                    palette: palette,
                    unit: "pt",
                    minimum: 1
                ) { host.setTerminalFontSize(size: Float($0)) }
                .frame(width: 92)
            }
        }
    }

    /// Whether the terminal wears its title bar, which is the one thing about it
    /// that is a look rather than what it says.
    func terminalAppearanceSection(_ terminal: TerminalFormat) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Appearance")

            checkRow("Show Title Bar", on: terminal.showTitleBar) {
                host.setTerminalTitleBar(enabled: !terminal.showTitleBar)
            }
        }
    }

    // MARK: Diagram

    /// The diagram's own style: the type size the labels are set in, and the
    /// four colours the layout paints with. Same contract as the Terminal
    /// section. Nothing here touches the source, which is content and is typed
    /// on the canvas.
    func diagramSection(_ diagram: DiagramFormat) -> some View {
        HStack(spacing: 8) {
            rowLabel("Label Size")
            Spacer(minLength: 0)
            ValueField(
                label: "",
                value: diagram.size,
                palette: palette,
                unit: "pt",
                minimum: 1
            ) { host.setDiagramFontSize(size: Float($0)) }
            .frame(width: 92)
        }
    }

    /// The four colours a diagram is drawn in: its look, so Style rather than
    /// the Diagram segment, which holds the one thing left that is not a colour.
    func diagramColorsSection(_ diagram: DiagramFormat) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Colours")

            HStack(spacing: 8) {
                colorWell("Node Fill", argb: diagram.nodeFill) {
                    host.setDiagramNodeFill(argb: $0)
                }
                Spacer(minLength: 0)
                colorWell("Node Stroke", argb: diagram.nodeStroke) {
                    host.setDiagramNodeStroke(argb: $0)
                }
            }

            HStack(spacing: 8) {
                colorWell("Node Text", argb: diagram.nodeText) {
                    host.setDiagramNodeText(argb: $0)
                }
                Spacer(minLength: 0)
                colorWell("Edge", argb: diagram.edgeColor) {
                    host.setDiagramEdgeColor(argb: $0)
                }
            }
        }
    }

    // MARK: Equation

    /// The equation's own style: the size the expression is set at, and the
    /// colour it is drawn in. Same contract as the Diagram section. Nothing
    /// here touches the latex, which is content and is typed on the canvas.
    func equationSection(_ equation: EquationFormat) -> some View {
        HStack(spacing: 8) {
            rowLabel("Size")
            Spacer(minLength: 0)
            ValueField(
                label: "",
                value: equation.size,
                palette: palette,
                unit: "pt",
                minimum: 1
            ) { host.setEquationFontSize(size: Float($0)) }
            .frame(width: 92)
        }
    }

    /// What colour the expression is drawn in: its look, so Style, the way the
    /// diagram's colours are.
    func equationColorSection(_ equation: EquationFormat) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Colour")

            colorWell("Colour", argb: equation.color) { host.setEquationColor(argb: $0) }
        }
    }

    // MARK: Image

    /// The picture's own controls: what shows of it, how it is corrected, what
    /// is written under it, and the two verbs that change its bytes. Same
    /// contract as the Equation section, except the caption, which is content
    /// and so goes to the primary alone.
    ///
    /// Everything below the mask needs bytes to act on, so an image that has
    /// none shows the popup and the caption and nothing else: there is no
    /// picture to adjust and no background to rub out.
    @ViewBuilder func imageSection(_ image: ImageFormat, _ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            // None first, then the outlines, so the popup's position is Kotlin's
            // index plus one and picking None is index -1: no mask at all.
            stylePopup(["None"] + ui.maskKinds, selected: image.maskKind + 1) { index in
                setMask(image, kind: index - 1)
            }

            if image.maskKind >= 0 {
                HStack(spacing: 8) {
                    maskField("X", image.maskX) { setMask(image, x: $0) }
                    maskField("Y", image.maskY) { setMask(image, y: $0) }
                }
                HStack(spacing: 8) {
                    maskField("W", image.maskWidth) { setMask(image, width: $0) }
                    maskField("H", image.maskHeight) { setMask(image, height: $0) }
                }
            }

            if image.hasAsset {
                palette.divider.frame(height: 1)

                adjustRow("Exposure", image.exposure, in: -1...1) {
                    setAdjust(image, exposure: $0)
                } commit: {
                    commitImageAdjust()
                }
                adjustRow("Saturation", image.saturation, in: 0...2) {
                    setAdjust(image, saturation: $0)
                } commit: {
                    commitImageAdjust()
                }
                adjustRow("Contrast", image.contrast, in: 0...2) {
                    setAdjust(image, contrast: $0)
                } commit: {
                    commitImageAdjust()
                }

                HStack(spacing: 8) {
                    Spacer(minLength: 0)
                    Button("Reset") { host.resetImageAdjust() }
                        .buttonStyle(.plain)
                        .font(.system(size: 11.5))
                        .foregroundStyle(image.adjusted ? palette.accent : palette.faint)
                        .disabled(!image.adjusted)
                }
            }

            StringField(placeholder: "Caption", value: image.caption, palette: palette) {
                host.setImageCaption(text: $0)
            }

            if image.hasAsset {
                palette.divider.frame(height: 1)

                // How far from the corner pixel counts as the same backdrop.
                // A slider rather than a field: what it takes is a look at the
                // result, and the number itself means nothing to anyone.
                HStack(spacing: 10) {
                    Text("Tolerance")
                        .font(.system(size: 11.5))
                        .foregroundStyle(palette.subtle)
                        .frame(width: 66, alignment: .leading)
                    Slider(value: $backgroundTolerance, in: 0...1)
                        .controlSize(.small)
                        .tint(palette.accent)
                    Text("\(Int((backgroundTolerance * 100).rounded()))%")
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundStyle(palette.ctrlText)
                        .frame(width: 40, alignment: .trailing)
                }

                panelButton("Remove Background", symbol: "wand.and.stars") {
                    host.removeImageBackground(tolerance: Float(backgroundTolerance))
                }
            }

            panelButton("Replace Image...", symbol: "photo") { Media.replace(in: host) }
        }
    }

    /// One corner of the mask window, in whole percent. The model's units are
    /// 0 to 1, so the field multiplies on the way out and divides on the way
    /// back: nobody reads a window as 0.375.
    func maskField(_ label: String, _ value: Double, onCommit: @escaping (Double) -> Void) -> some View {
        ValueField(label: label, value: (value * 100).rounded(), palette: palette, unit: "%") {
            onCommit($0 / 100)
        }
    }

    /// The mask, with one part of it replaced. Everything the control did not
    /// touch goes back as it was, so setting the width never moves the window
    /// and picking an outline never resizes it. Kotlin clamps.
    func setMask(
        _ image: ImageFormat,
        kind: Int? = nil,
        x: Double? = nil,
        y: Double? = nil,
        width: Double? = nil,
        height: Double? = nil
    ) {
        host.setImageMask(
            kindIndex: Int32(kind ?? image.maskKind),
            x: Float(x ?? image.maskX),
            y: Float(y ?? image.maskY),
            w: Float(width ?? image.maskWidth),
            h: Float(height ?? image.maskHeight)
        )
    }

    /// One correction, previewed. The other two go back as they were: the three
    /// compose into one colour matrix, so they travel as one write.
    func setAdjust(
        _ image: ImageFormat,
        exposure: Double? = nil,
        saturation: Double? = nil,
        contrast: Double? = nil
    ) {
        host.setImageAdjust(
            exposure: Float(exposure ?? image.exposure),
            saturation: Float(saturation ?? image.saturation),
            contrast: Float(contrast ?? image.contrast),
            commit: false
        )
    }

    /// A correction slider. The drag streams previews through the loop and the
    /// release commits what they left, so the whole drag is one undo entry, the
    /// way the opacity slider works. [commit] reads the settled values back off
    /// the host rather than trusting this pass's snapshot, which is why it is a
    /// closure and not a value: an image and a gallery both wear these three,
    /// and each knows how to read its own.
    func adjustRow(
        _ label: String,
        _ value: Double,
        in range: ClosedRange<Double>,
        preview: @escaping (Double) -> Void,
        commit: @escaping () -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(label)
                .font(.system(size: Inspect.text))
                .foregroundStyle(palette.text)

            HStack(spacing: 8) {
                Slider(
                    value: Binding(get: { value }, set: preview),
                    in: range,
                    onEditingChanged: { editing in if !editing { commit() } }
                )
                .controlSize(.small)
                .tint(palette.accent)

                ValueField(
                    label: "",
                    value: (value * 100).rounded(),
                    palette: palette,
                    unit: "%",
                    minimum: range.lowerBound * 100,
                    maximum: range.upperBound * 100
                ) {
                    preview($0 / 100)
                    commit()
                }
                .frame(width: Inspect.field + Inspect.stepper + 5)
            }
        }
    }

    /// The image's three corrections as the loop now holds them, committed.
    func commitImageAdjust() {
        guard let live = host.selectedImage() else { return }
        host.setImageAdjust(
            exposure: live.exposure,
            saturation: live.saturation,
            contrast: live.contrast,
            commit: true
        )
    }

    // MARK: Gallery

    /// The gallery's own controls: the pictures it holds, which of them is being
    /// authored, what is written under that one, how the whole box is corrected,
    /// and the build order that walks an audience through it.
    ///
    /// A gallery is a box rather than a style, so every control here writes to
    /// the primary alone: there is no selection to spread a picture across.
    @ViewBuilder func gallerySection(_ gallery: GalleryFormat) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            galleryStrip(gallery)

            HStack(spacing: 6) {
                panelButton("Add Images...", symbol: "photo.on.rectangle") {
                    Media.addToGallery(in: host)
                }
                galleryStep("minus", help: "Remove") {
                    host.removeGalleryImage(index: Int32(gallery.current))
                }
                .disabled(gallery.count == 0)
                galleryStep("chevron.left", help: "Move Earlier") {
                    host.moveGalleryImage(
                        from: Int32(gallery.current),
                        to: Int32(gallery.current - 1)
                    )
                }
                .disabled(gallery.current <= 0)
                galleryStep("chevron.right", help: "Move Later") {
                    host.moveGalleryImage(
                        from: Int32(gallery.current),
                        to: Int32(gallery.current + 1)
                    )
                }
                .disabled(gallery.current >= gallery.count - 1)
            }

            // The current picture's, not the box's: a caption names what is on
            // screen. Nothing to caption in an empty gallery.
            StringField(placeholder: "Caption", value: gallery.caption, palette: palette) {
                host.setGalleryCaption(index: Int32(gallery.current), text: $0)
            }
            .disabled(gallery.count == 0)

            checkRow("Show Captions", on: gallery.showCaptions) {
                host.setGalleryShowCaptions(on: !gallery.showCaptions)
            }

            palette.divider.frame(height: 1)

            adjustRow("Exposure", gallery.exposure, in: -1...1) {
                previewGalleryAdjust(gallery, exposure: $0)
            } commit: {
                commitGalleryAdjust()
            }
            adjustRow("Saturation", gallery.saturation, in: 0...2) {
                previewGalleryAdjust(gallery, saturation: $0)
            } commit: {
                commitGalleryAdjust()
            }
            adjustRow("Contrast", gallery.contrast, in: 0...2) {
                previewGalleryAdjust(gallery, contrast: $0)
            } commit: {
                commitGalleryAdjust()
            }

            HStack(spacing: 8) {
                Spacer(minLength: 0)
                Button("Reset") {
                    host.setGalleryAdjust(
                        exposure: 0,
                        saturation: 1,
                        contrast: 1,
                        commit: true
                    )
                }
                .buttonStyle(.plain)
                .font(.system(size: 11.5))
                .foregroundStyle(gallery.adjusted ? palette.accent : palette.faint)
                .disabled(!gallery.adjusted)
            }

            palette.divider.frame(height: 1)

            // The walk through the pictures, written into the slide's build
            // order: one click each after the first. The core drops the ones it
            // already wrote, so pressing this twice leaves one set.
            panelButton("Add Slide Steps", symbol: "list.number") { host.addGallerySteps() }
                .disabled(gallery.count < 2)
        }
    }

    /// The pictures, in order, the one being authored ringed. Thumbnails are
    /// decoded off the main thread, so a picture that has not landed yet draws
    /// as an empty well and fills itself in when it arrives.
    @ViewBuilder func galleryStrip(_ gallery: GalleryFormat) -> some View {
        if gallery.count == 0 {
            Text("No pictures yet")
                .font(.system(size: 11.5))
                .foregroundStyle(palette.faint)
                .frame(height: Self.galleryThumb)
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(Array(0..<gallery.count), id: \.self) { index in
                        galleryThumbnail(index, current: index == gallery.current)
                    }
                }
                .padding(.vertical, 2)
            }
            .frame(height: Self.galleryThumb + 6)
        }
    }

    /// One picture in the strip. Clicking it is what picks the image the canvas
    /// shows and the caption field edits.
    @ViewBuilder func galleryThumbnail(_ index: Int, current: Bool) -> some View {
        let side = Self.galleryThumb
        let shape = RoundedRectangle(cornerRadius: 4, style: .continuous)
        Button { host.setGalleryCurrent(index: Int32(index)) } label: {
            Group {
                if let image = host.galleryThumbnail(index: Int32(index), width: Int32(side)) {
                    Image(nsImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } else {
                    palette.ctrl
                }
            }
            .frame(width: side, height: side)
            .clipShape(shape)
            .overlay {
                shape
                    .inset(by: current ? 1 : 0.5)
                    .stroke(
                        current ? palette.accent : palette.hairline,
                        lineWidth: current ? 2 : 1
                    )
            }
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .help("Image \(index + 1)")
    }

    /// A square icon button beside the strip: remove, and the two that reorder.
    /// The panel's raised look at the panel button's height, so a row of these
    /// lines up with the labelled one beside them.
    func galleryStep(
        _ symbol: String,
        help: String,
        action: @escaping () -> Void
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: 6, style: .continuous)
        return Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 11))
                .foregroundStyle(palette.ctrlText)
                .frame(width: 26, height: 26)
                .background(palette.buttonFill, in: shape)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
        .help(help)
    }

    /// One correction, previewed. The other two go back as they were: the three
    /// compose into one colour matrix, so they travel as one write, the way the
    /// image's do.
    func previewGalleryAdjust(
        _ gallery: GalleryFormat,
        exposure: Double? = nil,
        saturation: Double? = nil,
        contrast: Double? = nil
    ) {
        host.setGalleryAdjust(
            exposure: Float(exposure ?? gallery.exposure),
            saturation: Float(saturation ?? gallery.saturation),
            contrast: Float(contrast ?? gallery.contrast),
            commit: false
        )
    }

    /// The gallery's three corrections as the loop now holds them, committed.
    func commitGalleryAdjust() {
        guard let live = host.selectedGallery() else { return }
        host.setGalleryAdjust(
            exposure: live.exposure,
            saturation: live.saturation,
            contrast: live.contrast,
            commit: true
        )
    }

    /// How big a picture in the strip is drawn, in points.
    static let galleryThumb: CGFloat = 48

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

    func flipButton(
        _ symbol: String,
        help: String,
        action: @escaping () -> Void
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 13))
                .foregroundStyle(palette.inspectorTitle)
                .frame(width: 34, height: Inspect.control)
                .background(palette.inspectorControl, in: shape)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
        .help(help)
    }

    func opacitySection(_ element: Selection) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Opacity")

            HStack(spacing: 8) {
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

                // Percent in the field, a fraction in the document: the two
                // meet here, which is the only place anyone says "100%".
                ValueField(
                    label: "",
                    value: (element.opacity * 100).rounded(),
                    palette: palette,
                    unit: "%",
                    minimum: 0,
                    maximum: 100
                ) { host.setSelectedElementOpacity(opacity: Float($0 / 100), commit: true) }
                .frame(width: Inspect.field + Inspect.stepper + 5)
            }
        }
    }

    /// The panel's raised full-width button: the Arrange pairs, and the two
    /// verbs at the foot of slide formatting.
    func panelButton(
        _ label: String,
        symbol: String = "",
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) { panelButtonFace(label) }
            .buttonStyle(.plain)
    }

    /// The face of one: a centred label in a grey filled rounded rect. No
    /// glyph, the way Keynote draws Edit Slide Layout and Build Order, and the
    /// same fill the popups wear so a column of them reads as one material.
    func panelButtonFace(_ label: String) -> some View {
        let shape = RoundedRectangle(cornerRadius: Inspect.radius, style: .continuous)
        return Text(label)
            .font(.system(size: Inspect.text))
            .foregroundStyle(palette.text)
            .lineLimit(1)
            .minimumScaleFactor(0.85)
            .frame(maxWidth: .infinity)
            .frame(height: Inspect.button)
            .background(palette.inspectorControl, in: shape)
            .contentShape(shape)
    }

    /// A small bordered value field with its unit inside it and a stepper beside
    /// it: mono, committed on Enter or on losing focus, the way Keynote's are.
    /// Anything that is not a number reverts to what the document holds, so a
    /// half-typed field cannot push nonsense in.
    ///
    /// The stepper commits through `onCommit` too, one step per click, so typing
    /// 40 and clicking up twice are the same three edits by two routes. An arrow
    /// at a bound is dead rather than clamping silently.
    struct ValueField: View {
        let label: String
        let value: Double
        let palette: Palette
        /// Drawn inside the field, after the number ("pt", "%", "\u{00B0}", "s", "\u{00D7}").
        var unit: String? = nil
        /// 0 is the document-unit default: whole numbers, the way a frame reads.
        /// Line spacing is a multiplier, so it keeps its fraction.
        var decimals: Int = 0
        /// What one stepper click is worth. A point is 1; the fractional units
        /// (seconds, a line-spacing multiple, a scale) step a tenth.
        var step: Double = 1
        /// Where the arrows stop, for the numbers with a floor or a ceiling the
        /// core would clamp to anyway.
        var minimum: Double? = nil
        var maximum: Double? = nil
        let onCommit: (Double) -> Void

        @State private var text: String = ""
        @FocusState private var focused: Bool

        private var shape: RoundedRectangle {
            RoundedRectangle(cornerRadius: 5, style: .continuous)
        }

        /// A field is as wide as it is given room for, so a caller that wants
        /// Keynote's 60pt says so; the stepper is always beside it.
        /// Keynote writes "5 pt" but "100%" and "270\u{00B0}": the units that
        /// are words take a space, the ones that are signs do not.
        static func unitGap(_ unit: String?) -> CGFloat {
            guard let unit else { return 0 }
            return unit == "%" || unit == "\u{00B0}" ? 0 : 3
        }

        private var canStepUp: Bool { maximum.map { value < $0 } ?? true }
        private var canStepDown: Bool { minimum.map { value > $0 } ?? true }

        var body: some View {
            HStack(spacing: 5) {
                if !label.isEmpty {
                    Text(label)
                        .font(.system(size: Inspect.text))
                        .foregroundStyle(palette.text)
                }

                // The value and its unit share the field, hard against its
                // right edge: "5 pt", "100%", "270\u{00B0}".
                HStack(spacing: Self.unitGap(unit)) {
                    TextField("", text: $text)
                        .textFieldStyle(.plain)
                        .font(.system(size: Inspect.text))
                        .foregroundStyle(palette.text)
                        .multilineTextAlignment(.trailing)
                        .focused($focused)
                        .onSubmit { commit() }
                        .onChange(of: focused) { _, now in if !now { commit() } }

                    if let unit {
                        Text(unit)
                            .font(.system(size: Inspect.text))
                            .foregroundStyle(palette.text)
                    }
                }
                .padding(.horizontal, 8)
                .frame(maxWidth: .infinity)
                .frame(height: Inspect.control)
                .background(palette.inspectorField, in: shape)

                stepper
            }
            .onAppear { text = formatted(value) }
            // A canvas drag or an undo moves the element under the field. The
            // one being typed in is left alone until it loses focus.
            .onChange(of: value) { _, latest in if !focused { text = formatted(latest) } }
        }

        /// The two arrows, in their own well outside the field's right edge.
        private var stepper: some View {
            VStack(spacing: 1) {
                arrow("chevron.up", enabled: canStepUp) { stepBy(step) }
                arrow("chevron.down", enabled: canStepDown) { stepBy(-step) }
            }
            .frame(width: Inspect.stepper, height: Inspect.control)
            .background(palette.inspectorControl, in: shape)
        }

        /// Keynote's arrows are heavy and dark, not hairlines: at 10pt they are
        /// the only thing in the row you can hit without looking.
        private func arrow(
            _ symbol: String,
            enabled: Bool,
            action: @escaping () -> Void
        ) -> some View {
            Button(action: action) {
                Image(systemName: symbol)
                    .font(.system(size: 9, weight: .heavy))
                    .foregroundStyle(enabled ? palette.inspectorTitle : palette.faint)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!enabled)
        }

        /// One click, off whatever the document holds now rather than off the
        /// field's text: a half-typed field is not what the arrow steps from.
        private func stepBy(_ delta: Double) {
            var stepped = value + delta
            if let minimum { stepped = max(stepped, minimum) }
            if let maximum { stepped = min(stepped, maximum) }
            text = formatted(stepped)
            onCommit(stepped)
        }

        private func commit() {
            guard let typed = Double(text.trimmingCharacters(in: .whitespaces)) else {
                text = formatted(value)
                return
            }
            text = formatted(typed)
            onCommit(typed)
        }

        private func formatted(_ value: Double) -> String {
            if decimals == 0 { return String(Int(value.rounded())) }
            return String(format: "%.\(decimals)f", value)
        }
    }

    /// A line of text the document holds as one string: the text box's link, the
    /// shape's label. Committed on Enter or on losing focus, like the value
    /// fields; empty commits empty, and what that means is the setter's to say.
    struct StringField: View {
        let placeholder: String
        let value: String
        let palette: Palette
        let onCommit: (String) -> Void

        @State private var text: String = ""
        @FocusState private var focused: Bool

        private var shape: RoundedRectangle {
            RoundedRectangle(cornerRadius: 5, style: .continuous)
        }

        var body: some View {
            TextField(placeholder, text: $text)
                .textFieldStyle(.plain)
                .font(.system(size: 12))
                .foregroundStyle(palette.ctrlText)
                .focused($focused)
                .padding(.horizontal, 7)
                .frame(height: 22)
                .background(palette.ctrl, in: shape)
                .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
                .onSubmit { commit() }
                .onChange(of: focused) { _, now in if !now { commit() } }
                .onAppear { text = value }
                .onChange(of: value) { _, latest in if !focused { text = latest } }
        }

        private func commit() {
            let typed = text.trimmingCharacters(in: .whitespaces)
            text = typed
            onCommit(typed)
        }
    }

    /// How long the transition takes, in seconds, with the number beside it.
    /// The drag rides on this view's own value and the release is what commits,
    /// so the whole drag is one edit and one undo entry, the way the opacity
    /// slider's release is. A transition has no preview event to stream through
    /// the loop the way an element's opacity does, which is the one difference.
    ///
    /// The dragged value is cleared by the state coming back rather than by the
    /// release, so the thumb never flicks back to where it started for the frame
    /// between the two.
    struct DurationSlider: View {
        let value: Double
        let palette: Palette
        let onCommit: (Double) -> Void

        @State private var dragged: Double? = nil

        var body: some View {
            HStack(spacing: 8) {
                Slider(
                    value: Binding(get: { dragged ?? value }, set: { dragged = $0 }),
                    in: 0.1...3,
                    onEditingChanged: { editing in
                        guard !editing, let latest = dragged else { return }
                        onCommit(latest)
                    }
                )
                .controlSize(.small)
                .tint(palette.accent)

                EditorView.ValueField(
                    label: "",
                    value: dragged ?? value,
                    palette: palette,
                    unit: "s",
                    decimals: 1,
                    step: 0.1,
                    minimum: 0.1,
                    maximum: 3,
                    onCommit: onCommit
                )
                .frame(width: Inspect.field + Inspect.stepper + 5)
            }
            .onChange(of: value) { _, _ in dragged = nil }
        }
    }

    // MARK: Build order

    /// The order the slide's builds play in, and the editor for whichever row is
    /// picked. The picked row is this view's own state: a build is not a thing
    /// the document can be "on", so which one is being edited is the panel's
    /// business alone.
    ///
    /// The Animate tab swaps its segments for this when Build Order is pressed,
    /// which is where the pinned button at the foot of the panel leads.
    @ViewBuilder func buildOrderSection(_ ui: Chrome) -> some View {
        let slideIndex = host.selectedSlideIndex()

        VStack(alignment: .leading, spacing: 9) {
            // Above the list rather than under it: what the builds do is the
            // question the panel opens with, and playing the slide answers it.
            panelButton("Preview", symbol: "play.rectangle") { startPreview() }

            if ui.builds.isEmpty {
                Text("Nothing builds on this slide.")
                    .font(.system(size: 11.5))
                    .foregroundStyle(palette.faint)
            } else {
                buildList(ui)
            }

            if let entry = ui.builds.first(where: { $0.index == selectedBuild }) {
                palette.divider.frame(height: 1)
                buildControls(entry, ui)
            }
        }
        // The picked row is a place in this slide's order, so it means nothing
        // on the next slide.
        .onChange(of: slideIndex) { _, _ in selectedBuild = nil }
    }

    func buildList(_ ui: Chrome) -> some View {
        VStack(spacing: 4) {
            ForEach(ui.builds) { entry in
                BuildOrderRow(
                    entry: entry,
                    picked: entry.index == selectedBuild,
                    dragging: buildDrag?.index == entry.index,
                    liftY: buildDrag?.index == entry.index ? buildDrag?.translationY ?? 0 : 0,
                    palette: palette,
                    onTap: {
                        selectedBuild = entry.index
                        host.selectBuildElement(index: Int32(entry.index))
                    },
                    onDrag: { point, translation in
                        dragBuild(entry, to: point, by: translation, in: ui.builds)
                    },
                    onDrop: dropBuild
                )
            }
        }
        .coordinateSpace(name: BuildSpace.name)
        .onPreferenceChange(BuildRowFrames.self) { frames in buildRowFrames = frames }
        .overlay(alignment: .topLeading) { buildDropLine }
        .animation(.easeInOut(duration: 0.2), value: ui.builds.map(\.index))
    }

    /// The gap the drag is over: after the last row whose midpoint the pointer
    /// has passed, and above the first row until it passes one.
    func dragBuild(_ entry: BuildEntry, to point: CGPoint, by translation: CGSize, in builds: [BuildEntry]) {
        let placed: [(row: BuildEntry, frame: CGRect)] = builds.compactMap { row in
            buildRowFrames[row.index].map { (row, $0) }
        }
        guard let first = placed.first else { return }

        var gap = 0
        // Half the 4pt row gap above the first row, so the line sits in the gap
        // rather than on a row's edge.
        var lineY: CGFloat = first.frame.minY - 2
        for (row, frame) in placed {
            if frame.midY >= point.y { break }
            gap = row.index + 1
            lineY = frame.maxY + 2
        }

        buildDrag = BuildDrag(
            index: entry.index, gap: gap, lineY: lineY, translationY: translation.height
        )
    }

    /// A drop back into the row's own gap moves nothing, so it is not sent. A
    /// reorder renumbers the rows around it, so the pick follows the row it was
    /// on and is dropped when it was on another one.
    func dropBuild() {
        guard let drag = buildDrag else { return }
        let destination = drag.destination
        buildDrag = nil
        guard destination != drag.index else { return }

        selectedBuild = selectedBuild == drag.index ? destination : nil
        withAnimation(.easeInOut(duration: 0.2)) {
            host.moveBuild(from: Int32(drag.index), to: Int32(destination))
        }
    }

    @ViewBuilder var buildDropLine: some View {
        if let drag = buildDrag {
            palette.accent
                .frame(height: 2)
                .offset(y: drag.lineY - 1)
        }
    }

    /// How long the picked build takes, what starts it, and what it does to its
    /// element when it is an action. What it *plays* is not in here: the heading
    /// above it says the effect and its Change button is where it is picked.
    ///
    /// The build order list keeps the popups, since a row there is picked out of
    /// the whole slide rather than out of one element's segment.
    @ViewBuilder func buildControls(
        _ entry: BuildEntry,
        _ ui: Chrome,
        withPickers: Bool = true
    ) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            if entry.isAction {
                if withPickers {
                    sectionLabel("Action")
                    stylePopup(ui.actionKinds, selected: entry.actionKindIndex ?? 0) {
                        commitBuild(entry, actionKindIndex: $0)
                    }
                }

                actionFields(entry)
            } else {
                if withPickers {
                    sectionLabel("Effect")
                    stylePopup(ui.buildEffects, selected: entry.effectIndex) {
                        commitBuild(entry, effectIndex: $0)
                    }
                }

                sectionLabel("Delivery")

                // The element's own list, not the model's: what a build can be
                // handed over in is whatever it has pieces of.
                stylePopup(entry.deliveryTitles, selected: entry.deliveryIndex) {
                    commitBuild(entry, deliveryIndex: $0)
                }
            }

            sectionLabel("Duration")

            DurationSlider(value: entry.duration, palette: palette) {
                commitBuild(entry, duration: $0)
            }

            sectionLabel("Trigger")

            HStack(spacing: 2) {
                ForEach(Array(ui.buildTriggers.enumerated()), id: \.offset) { index, name in
                    segment(name, on: index == entry.triggerIndex) {
                        commitBuild(entry, triggerIndex: index)
                    }
                }
            }
            .padding(2)
            .frame(height: Inspect.control + 4)
            .background(palette.inspectorControl, in: Capsule())

            // Only a build that waits out the one before it has a wait to set.
            if entry.triggerIndex == 2 {
                sectionLabel("Delay")

                ValueField(
                    label: "",
                    value: entry.delay,
                    palette: palette,
                    unit: "s",
                    decimals: 1,
                    step: 0.1,
                    minimum: 0
                ) {
                    commitBuild(entry, delay: $0)
                }
                .frame(width: 104)
            }

            // Only an element with steps of its own has one to move to.
            if entry.hasStepTarget {
                sectionLabel("Step")

                ValueField(
                    label: "",
                    value: Double(entry.elementStep ?? 0),
                    palette: palette,
                    minimum: 0
                ) { commitBuild(entry, elementStep: Int($0.rounded())) }
                .frame(width: 104)
            }

            panelButton("Remove Build", symbol: "minus.circle") {
                selectedBuild = nil
                host.removeBuild(index: Int32(entry.index))
            }
        }
    }

    /// What the picked action changes: only the fields its kind reads, the way
    /// the model reads only those and leaves the rest where they are.
    @ViewBuilder func actionFields(_ entry: BuildEntry) -> some View {
        switch entry.actionKindIndex ?? 0 {
        case 0:
            HStack(spacing: 8) {
                ValueField(label: "X", value: entry.dx, palette: palette, unit: "pt") {
                    commitBuild(entry, dx: $0)
                }
                ValueField(label: "Y", value: entry.dy, palette: palette, unit: "pt") {
                    commitBuild(entry, dy: $0)
                }
            }

        case 1:
            RatioSlider(value: entry.opacity, palette: palette) {
                commitBuild(entry, opacity: $0)
            }

        case 2:
            ValueField(label: "", value: entry.rotation, palette: palette, unit: "\u{00B0}") {
                commitBuild(entry, rotation: $0)
            }
            .frame(width: 104)

        default:
            ValueField(
                label: "",
                value: entry.scale,
                palette: palette,
                unit: "\u{00D7}",
                decimals: 2,
                step: 0.1,
                minimum: 0.1
            ) {
                commitBuild(entry, scale: $0)
            }
            .frame(width: 104)
        }
    }

    /// A build commits whole, so a control that changes one thing sends the rest
    /// back as they stand. The same deal `commitTransition` takes.
    func commitBuild(
        _ entry: BuildEntry,
        effectIndex: Int? = nil,
        deliveryIndex: Int? = nil,
        triggerIndex: Int? = nil,
        duration: Double? = nil,
        delay: Double? = nil,
        elementStep: Int? = nil,
        actionKindIndex: Int? = nil,
        dx: Double? = nil,
        dy: Double? = nil,
        opacity: Double? = nil,
        rotation: Double? = nil,
        scale: Double? = nil
    ) {
        host.updateBuild(
            index: Int32(entry.index),
            effectIndex: Int32(effectIndex ?? entry.effectIndex),
            deliveryIndex: Int32(deliveryIndex ?? entry.deliveryIndex),
            triggerIndex: Int32(triggerIndex ?? entry.triggerIndex),
            durationMs: Int32(((duration ?? entry.duration) * 1000).rounded()),
            delayMs: Int32(((delay ?? entry.delay) * 1000).rounded()),
            elementStep: Int32(elementStep ?? entry.elementStep ?? -1),
            actionKindIndex: Int32(actionKindIndex ?? entry.actionKindIndex ?? -1),
            dx: Float(dx ?? entry.dx),
            dy: Float(dy ?? entry.dy),
            opacity: Float(opacity ?? entry.opacity),
            rotation: Float(rotation ?? entry.rotation),
            scale: Float(scale ?? entry.scale)
        )
    }

    /// One row of the build order: the badge, what it plays, and the grab glyph.
    /// The row under the editor wears the accent fill and border; a row whose
    /// element is merely selected on the canvas takes the accent badge alone, so
    /// the other builds on that element are visible without looking edited.
    struct BuildOrderRow: View {
        let entry: BuildEntry
        /// This is the row the editor below the list is about.
        let picked: Bool
        /// This row is the one being dragged, so it steps back while it travels.
        let dragging: Bool
        /// How far this row has been carried by the drag, 0 when it hasn't.
        let liftY: CGFloat
        let palette: Palette
        let onTap: () -> Void
        /// A drag sample, in [BuildSpace] plus its travel, and the release.
        let onDrag: (CGPoint, CGSize) -> Void
        let onDrop: () -> Void

        @State private var hovering = false

        private var shape: RoundedRectangle {
            RoundedRectangle(cornerRadius: 6, style: .continuous)
        }

        var body: some View {
            HStack(spacing: 9) {
                Text("\(entry.index + 1)")
                    .font(.system(size: 10.5, weight: .bold))
                    .foregroundStyle(
                        picked || entry.active ? palette.accentText : palette.badgeOffText
                    )
                    .frame(width: 17, height: 17)
                    .background(picked || entry.active ? palette.accent : palette.badgeOff, in: Circle())

                VStack(alignment: .leading, spacing: 1) {
                    Text(entry.title)
                        .font(.system(size: 12))
                        .foregroundStyle(palette.text)
                        .lineLimit(1)
                        .truncationMode(.middle)
                    Text(entry.meta)
                        .font(.system(size: 10.5))
                        .foregroundStyle(palette.subtle)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Text("⠿")
                    .font(.system(size: 11.5))
                    .foregroundStyle(palette.subtle)
            }
            .padding(EdgeInsets(top: 7, leading: 9, bottom: 7, trailing: 9))
            .background(fill, in: shape)
            .overlay {
                if picked { shape.inset(by: 0.5).stroke(palette.accent, lineWidth: 1) }
            }
            // Lifted: a touch translucent to show the rows it passes over, and
            // above them while it travels.
            .opacity(dragging ? 0.85 : 1)
            .offset(y: liftY)
            .zIndex(dragging ? 1 : 0)
            .contentShape(shape)
            .onTapGesture(perform: onTap)
            // Enough slop that a click is still a click, like the navigator's.
            .gesture(
                DragGesture(minimumDistance: 6, coordinateSpace: .named(BuildSpace.name))
                    .onChanged { value in onDrag(value.location, value.translation) }
                    .onEnded { _ in onDrop() }
            )
            .background {
                GeometryReader { proxy in
                    Color.clear.preference(
                        key: BuildRowFrames.self,
                        value: [entry.index: proxy.frame(in: .named(BuildSpace.name))]
                    )
                }
            }
            .onHover { hovering = $0 }
        }

        /// The design's active fill is the accent at 22%, which is what the
        /// picked row wears; everything else is the row fill, lifted on hover.
        private var fill: Color {
            if picked { return palette.accent.opacity(0.22) }
            return hovering ? palette.rowHov : palette.rowBg
        }
    }

    /// A fraction the element draws at, 0 to 1 with a percentage beside it. The
    /// drag rides on this view's own value and the release commits, so the whole
    /// drag is one edit, exactly like `DurationSlider`.
    struct RatioSlider: View {
        let value: Double
        let palette: Palette
        let onCommit: (Double) -> Void

        @State private var dragged: Double? = nil

        var body: some View {
            HStack(spacing: 8) {
                Slider(
                    value: Binding(get: { dragged ?? value }, set: { dragged = $0 }),
                    in: 0...1,
                    onEditingChanged: { editing in
                        guard !editing, let latest = dragged else { return }
                        onCommit(latest)
                    }
                )
                .controlSize(.small)
                .tint(palette.accent)

                EditorView.ValueField(
                    label: "",
                    value: ((dragged ?? value) * 100).rounded(),
                    palette: palette,
                    unit: "%",
                    minimum: 0,
                    maximum: 100
                ) { onCommit($0 / 100) }
                .frame(width: Inspect.field + Inspect.stepper + 5)
            }
            .onChange(of: value) { _, _ in dragged = nil }
        }
    }

    /// Default first, then the kinds. What a control shows below depends on
    /// which is picked: only the kinds that travel have a direction, and only an
    /// automatic slide has a delay to sit through.
    @ViewBuilder func transitionSection(_ ui: Chrome) -> some View {
        let transition = ui.transition

        VStack(alignment: .leading, spacing: 9) {
            // A slide on the deck's default plays nothing of its own, which is
            // the same empty state a segment with no build shows.
            if transition.kindIndex < 0 {
                effectEmptyState(
                    "No Transition Effect",
                    entries: transitionChoices(ui, transition)
                )
            } else {
                effectHeading(
                    transitionKindTitle(ui),
                    entries: transitionChoices(ui, transition)
                )
            }

            if Self.directionalKinds.contains(transitionKindTitle(ui)) {
                HStack(spacing: 2) {
                    ForEach(Array(ui.transitionDirections.enumerated()), id: \.offset) { index, name in
                        segment(name, on: index == transition.directionIndex) {
                            commitTransition(transition, directionIndex: index)
                        }
                    }
                }
                .padding(2)
                .frame(height: Inspect.control + 4)
                .background(palette.inspectorControl, in: Capsule())
            }

            if transition.kindIndex >= 0 {
                sectionLabel("Duration")

                DurationSlider(value: transition.duration, palette: palette) {
                    commitTransition(transition, duration: $0)
                }

                sectionLabel("Trigger")

                HStack(spacing: 2) {
                    segment("On Click", on: !transition.automatic) {
                        commitTransition(transition, automatic: false)
                    }
                    segment("Automatically", on: transition.automatic) {
                        commitTransition(transition, automatic: true)
                    }
                }
                .padding(2)
                .frame(height: Inspect.control + 4)
                .background(palette.inspectorControl, in: Capsule())

                if transition.automatic {
                    ValueField(
                        label: "",
                        value: transition.delay,
                        palette: palette,
                        unit: "s",
                        decimals: 1,
                        step: 0.1,
                        minimum: 0
                    ) {
                        commitTransition(transition, delay: $0)
                    }
                    .frame(width: 104)
                }
            }
        }
    }

    /// The transitions a slide may play, Default at the head so there is a way
    /// back to the deck's own. Handed to whichever affordance opens the list:
    /// Add an Effect when the slide plays none, Change when it plays one.
    func transitionChoices(_ ui: Chrome, _ transition: TransitionFormat) -> [MenuEntry] {
        [
            MenuEntry(title: "Default") { commitTransition(transition, kindIndex: -1) },
            .separator(),
        ] + ui.transitionKinds.enumerated().map { index, name in
            MenuEntry(title: name) { commitTransition(transition, kindIndex: index) }
        }
    }

    /// What the popup is set to, "" while the slide is on the deck's default.
    func transitionKindTitle(_ ui: Chrome) -> String {
        let index = ui.transition.kindIndex
        guard ui.transitionKinds.indices.contains(index) else { return "" }
        return ui.transitionKinds[index]
    }

    /// The kinds that travel, and so the only ones with a direction to pick. By
    /// name rather than by position: an ordinal from the other side of the
    /// boundary is not something this file should be spelling.
    static let directionalKinds: Set<String> = ["Push", "Move In", "Wipe"]

    /// A transition commits whole, so a control that changes one thing sends the
    /// other four back as they stand. The same deal `setFrame` takes.
    func commitTransition(
        _ transition: TransitionFormat,
        kindIndex: Int? = nil,
        directionIndex: Int? = nil,
        duration: Double? = nil,
        automatic: Bool? = nil,
        delay: Double? = nil
    ) {
        host.setTransition(
            kindIndex: Int32(kindIndex ?? transition.kindIndex),
            directionIndex: Int32(directionIndex ?? transition.directionIndex),
            durationMs: Int32(((duration ?? transition.duration) * 1000).rounded()),
            automatic: automatic ?? transition.automatic,
            delayMs: Int32(((delay ?? transition.delay) * 1000).rounded())
        )
    }

    // MARK: Document panel

    /// The whole deck: the theme it wears, the shape its slides are cut to, how
    /// it plays and what hangs behind every slide of it.
    ///
    /// What one slide has of its own moved to Format, which is where Keynote
    /// keeps it: with nothing selected, formatting the slide *is* the format.
    func documentPanel(_ ui: Chrome) -> some View {
        // Scrolls so a tall Document panel (every section open, or a deck with
        // several layouts) can't grow the window past its own edge.
        GeometryReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: Layout.panelPadding) {
                    themeSection(ui)
                    palette.divider.frame(height: 1)
                    slideSizeSection(ui)
                    palette.divider.frame(height: 1)
                    playbackSection(ui)
                    palette.divider.frame(height: 1)
                    deckBackgroundSection(ui)
                    Spacer(minLength: 0)
                }
                .padding(Layout.panelPadding)
                .frame(maxWidth: .infinity, minHeight: proxy.size.height, alignment: .top)
            }
            .scrollContentBackground(.hidden)
        }
    }

    /// The layout the slide is on, and the pick that moves it to another. "None"
    /// is a real choice, not an empty state: a slide on no layout keeps
    /// everything it has and simply inherits nothing.
    func slideLayoutCard(_ ui: Chrome) -> some View {
        let entries: [MenuEntry] = [
            MenuEntry(title: "None") { host.applyLayout(layoutId: nil) },
            .separator(),
        ] + ui.layouts.map { layout in
            MenuEntry(title: layout.name) { host.applyLayout(layoutId: layout.id) }
        }
        let shape = RoundedRectangle(cornerRadius: 9, style: .continuous)

        return PopUpButton(entries: entries) {
            HStack(spacing: 12) {
                layoutPreview(ui)
                VStack(alignment: .leading, spacing: 0) {
                    Text("Slide Layout")
                        .font(.system(size: Inspect.caption))
                        .foregroundStyle(palette.subtle)
                    Text(currentLayoutName(ui))
                        .font(.system(size: Inspect.text, weight: .bold))
                        .foregroundStyle(palette.text)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.down")
                    .font(.system(size: 9, weight: .semibold))
                    .foregroundStyle(palette.subtle)
            }
            .padding(10)
            // A white card, the way Keynote draws this one: it is the only
            // control in the panel that is a card rather than a control.
            .background(palette.ctrl, in: shape)
            .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
            .contentShape(shape)
        }
    }

    func currentLayoutName(_ ui: Chrome) -> String {
        ui.layouts.first { $0.id == ui.slideLayoutId }?.name ?? "None"
    }

    /// Every placeholder back where the layout puts it. Off while the slide is
    /// on no layout: there is nothing to put it back to.
    func reapplyLayoutButton(_ ui: Chrome) -> some View {
        panelButton("Reapply Layout", symbol: "arrow.clockwise") { host.reapplyLayout() }
            .disabled(!ui.canReapplyLayout)
            .opacity(ui.canReapplyLayout ? 1 : 0.45)
    }

    /// The layout the slide is on, rendered by the shared Compose renderer the
    /// way a navigator thumbnail is. The drawn bars are the fallback, for a slide
    /// on no layout and for the moment before a render lands.
    @ViewBuilder func layoutPreview(_ ui: Chrome) -> some View {
        let shape = RoundedRectangle(cornerRadius: 3, style: .continuous)
        if let id = ui.slideLayoutId, let image = host.layoutThumbnail(layoutId: id, width: 62) {
            Image(nsImage: image)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 62)
                .clipShape(shape)
                .overlay { shape.inset(by: 0.5).stroke(palette.thumbEdge, lineWidth: 1) }
        } else {
            layoutPreviewBars
        }
    }

    /// A slide the way a layout picker draws one: white paper, three grey bars.
    var layoutPreviewBars: some View {
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

    /// The layout's name, which is what the navigator row and the slide's layout
    /// popup both show. Committed like every other string field here.
    func layoutNameSection(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Name")
            StringField(
                placeholder: "Layout",
                value: ui.slideTitle,
                palette: palette
            ) { typed in
                guard !typed.isEmpty else { return }
                host.renameSelectedSlide(title: typed)
            }
        }
    }

    /// What a slide on this layout fills in. The buttons are the roles Kotlin
    /// lists, in its order: the index is what goes back, so a role added to the
    /// document model shows up here with nothing to change but its glyph.
    func placeholdersSection(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Placeholders")
            ForEach(Array(ui.placeholderRoles.enumerated()), id: \.offset) { index, role in
                panelButton(role, symbol: Self.placeholderSymbol(role)) {
                    host.addPlaceholder(role: Int32(index))
                }
            }
        }
    }

    /// The glyph for a role, by name. An unknown one still draws a button: the
    /// list is the document model's to grow, and a missing glyph is not a reason
    /// to hide the role it belongs to.
    static func placeholderSymbol(_ role: String) -> String {
        switch role {
        case "Title": return "textformat"
        case "Body": return "text.alignleft"
        case "Media": return "photo"
        case "Code": return "chevron.left.forwardslash.chevron.right"
        default: return "square.dashed"
        }
    }

    func previewBar(width: CGFloat, height: CGFloat, color: Color) -> some View {
        RoundedRectangle(cornerRadius: 1).fill(color).frame(width: width, height: height)
    }

    /// Title and Body are still inert: they switch a layout's placeholders off
    /// for this one slide, which the document model has no word for yet. The
    /// number is the slide's own.
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
            .font(.system(size: Inspect.text, weight: .semibold))
            .foregroundStyle(palette.inspectorTitle)
    }

    /// The name at the left of a row, beside the control it names: regular
    /// weight and dark, not the pale small label our chrome uses elsewhere.
    func rowLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: Inspect.text))
            .foregroundStyle(palette.inspectorTitle)
    }

    /// The caption Keynote writes under a field rather than beside it: the X
    /// under Position's first box, the Angle under the rotate field.
    func fieldCaption(_ text: String) -> some View {
        Text(text)
            .font(.system(size: Inspect.caption, weight: .semibold))
            .foregroundStyle(palette.inspectorCaption)
            .frame(maxWidth: .infinity)
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
    func backgroundSection(_ ui: Chrome) -> some View {
        let slide = ui.slide
        return backgroundControls(
            "Background",
            kind: slide.backgroundKind,
            color: slide.color,
            gradientStart: slide.gradientStart,
            gradientEnd: slide.gradientEnd,
            inherits: "The deck's own background.",
            onDefault: { host.setBackgroundDefault() },
            onColor: { host.setBackgroundColor(argb: $0) },
            onGradient: { host.setBackgroundGradient(start: $0, end: $1) }
        )
    }

    /// The same three kinds, one level down: what a slide falls back to when it
    /// carries none of its own. The controls are the slide's, handed the deck's
    /// four numbers and the deck's setters, because a background is a background
    /// wherever it hangs.
    func deckBackgroundSection(_ ui: Chrome) -> some View {
        let deck = ui.deck
        return backgroundControls(
            "Deck Background",
            kind: deck.backgroundKind,
            color: deck.color,
            gradientStart: deck.gradientStart,
            gradientEnd: deck.gradientEnd,
            inherits: "The app's own dark gradient.",
            onDefault: { host.setDeckBackgroundDefault() },
            onColor: { host.setDeckBackgroundColor(argb: $0) },
            onGradient: { host.setDeckBackgroundGradient(start: $0, end: $1) }
        )
    }

    /// [inherits] is what the Default segment means here: the thing behind is a
    /// different thing for a slide than for the deck, and it is the only word
    /// that changes between the two.
    @ViewBuilder func backgroundControls(
        _ title: String,
        kind: Int,
        color: Int64,
        gradientStart: Int64,
        gradientEnd: Int64,
        inherits: String,
        onDefault: @escaping () -> Void,
        onColor: @escaping (Int64) -> Void,
        onGradient: @escaping (Int64, Int64) -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel(title)

            HStack(spacing: 2) {
                segment("Default", on: kind == 0, action: onDefault)
                segment("Color", on: kind == 1) { onColor(color) }
                segment("Gradient", on: kind == 2) { onGradient(gradientStart, gradientEnd) }
            }
            .padding(2)
            .frame(height: Inspect.control + 4)
            .background(palette.inspectorControl, in: Capsule())

            switch kind {
            case 1:
                swatchGrid(selected: color, onPick: onColor)
            case 2:
                stopRow("Start", selected: gradientStart) { onGradient($0, gradientEnd) }
                stopRow("End", selected: gradientEnd) { onGradient(gradientStart, $0) }
            default:
                Text(inherits)
                    .font(.system(size: 12))
                    .foregroundStyle(palette.faint)
            }
        }
    }

    /// How the whole deck plays: the kind of show, and the numbers that only
    /// some kinds have. Deck-wide, so it sits with the theme and the slide size
    /// rather than with the slide's own background below.
    ///
    /// A self-playing deck is the only one that advances on its own, and a
    /// links-only deck the only one that goes back to the start on its own, so
    /// each field is here only while it means something.
    @ViewBuilder func playbackSection(_ ui: Chrome) -> some View {
        let playback = ui.playback

        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Playback")

            stylePopup(ui.playbackTypes, selected: playback.typeIndex) {
                commitPlayback(playback, typeIndex: $0)
            }

            if playback.typeIndex == Self.selfPlayingType {
                playbackField("Advance every", value: playback.advance) {
                    commitPlayback(playback, advance: $0)
                }
            }

            checkRow("Loop", on: playback.loop) {
                commitPlayback(playback, loop: !playback.loop)
            }

            if playback.typeIndex == Self.linksOnlyType {
                playbackField("Restart after", value: playback.restartAfterIdle) {
                    commitPlayback(playback, restartAfterIdle: $0)
                }
            }
        }
    }

    /// The two type indices with a number of their own, in `playbackTypes`
    /// order, which is the enum's. Spelled once here, like the link kinds.
    static let selfPlayingType = 1
    static let linksOnlyType = 2

    /// One of the playback numbers: what it is, and how many seconds it is.
    func playbackField(
        _ label: String,
        value: Double,
        onCommit: @escaping (Double) -> Void
    ) -> some View {
        HStack(spacing: 8) {
            Text(label)
                .font(.system(size: 12))
                .foregroundStyle(palette.subtle)
            Spacer(minLength: 0)
            ValueField(
                label: "",
                value: value,
                palette: palette,
                unit: "s",
                decimals: 1,
                step: 0.1,
                minimum: 0,
                onCommit: onCommit
            )
            .frame(width: 104)
        }
    }

    /// Playback commits whole, so a control that changes one thing sends the
    /// other three back as they stand. The same deal `commitTransition` takes.
    func commitPlayback(
        _ playback: PlaybackFormat,
        typeIndex: Int? = nil,
        advance: Double? = nil,
        loop: Bool? = nil,
        restartAfterIdle: Double? = nil
    ) {
        host.setPlayback(
            typeIndex: Int32(typeIndex ?? playback.typeIndex),
            autoAdvanceMs: Int32(((advance ?? playback.advance) * 1000).rounded()),
            loop: loop ?? playback.loop,
            restartAfterIdleMs: Int32(
                ((restartAfterIdle ?? playback.restartAfterIdle) * 1000).rounded()
            )
        )
    }

    /// The look the whole deck is on: the pick that swaps it, and the two verbs
    /// that make one of the user's own out of what the deck is already wearing.
    ///
    /// Delete is only offered for a theme the user saved: a built-in is not the
    /// library's to drop, and the popup would come back one short next launch.
    @ViewBuilder func themeSection(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Theme")

            PopUpButton(
                entries: ui.themeNames.map { name in
                    MenuEntry(title: name) { host.changeTheme(name: name) }
                }
            ) {
                popupLabel(ui.themeName)
            }

            panelButton("Save Theme...", symbol: "square.and.arrow.down") {
                saveThemeText = ui.themeName
                savingTheme = true
            }

            if ui.userThemeNames.contains(ui.themeName) {
                panelButton("Delete Theme", symbol: "trash") {
                    host.deleteUserTheme(name: ui.themeName)
                }
            }
        }
        // Saving is the one verb here with something to ask, so it takes a
        // sheet, the way renaming a layout does in the navigator. Prefilled with
        // the current name: saving over your own theme is the common case.
        .alert("Save Theme", isPresented: $savingTheme) {
            TextField("Name", text: $saveThemeText)
            Button("Save") {
                let typed = saveThemeText.trimmingCharacters(in: .whitespaces)
                guard !typed.isEmpty else { return }
                host.saveAsTheme(name: typed)
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    /// A panel-wide popup's face: what it is set to, and the chevron. Shared by
    /// the two deck-wide pickers so they cannot drift apart.
    func popupLabel(_ title: String) -> some View {
        popupFace(title)
    }

    /// The shape every slide in the deck is cut to. Deck-wide, so it sits with
    /// the theme rather than with the slide's own background below.
    ///
    /// Either pick asks whether the content comes with the slide before it
    /// moves: both answers are ones people want, and a deck resized the wrong
    /// way is not something the eye can put back.
    @ViewBuilder func slideSizeSection(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Slide Size")

            PopUpButton(
                entries: ui.slideSize.presetTitles.enumerated().map { index, title in
                    MenuEntry(title: title) { pendingSizePreset = index }
                } + [
                    .separator(),
                    MenuEntry(title: "Custom...") {
                        customWidthText = String(Int(ui.slideSize.width.rounded()))
                        customHeightText = String(Int(ui.slideSize.height.rounded()))
                        customSize = true
                    },
                ]
            ) {
                popupLabel(ui.slideSize.title)
            }
        }
        // The picked preset is the question's subject, so it presents the alert
        // and clears when it closes: no second flag to fall out of step with.
        .alert(
            "Scale content to fit the new size?",
            isPresented: Binding(
                get: { pendingSizePreset != nil },
                set: { shown in if !shown { pendingSizePreset = nil } }
            ),
            presenting: pendingSizePreset
        ) { index in
            Button("Scale") { host.setSlideSizePreset(index: Int32(index), scaleContent: true) }
            Button("Don't Scale") { host.setSlideSizePreset(index: Int32(index), scaleContent: false) }
            Button("Cancel", role: .cancel) {}
        }
        // A sheet rather than an alert, because the answer here is a checkbox
        // beside two fields, and an alert holds nothing but fields and buttons.
        .sheet(isPresented: $customSize) { customSizeSheet }
    }

    /// Two measurements and the same scale question the presets ask, answered
    /// before the resize. Nonsense in either field is no resize: the sheet
    /// closes and the deck keeps the slide it had.
    var customSizeSheet: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Custom Slide Size")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(palette.text)

            HStack(spacing: 10) {
                sizeField("Width", text: $customWidthText)
                sizeField("Height", text: $customHeightText)
            }

            Toggle("Scale content", isOn: $customScaleContent)
                .toggleStyle(.checkbox)
                .font(.system(size: 12))
                .foregroundStyle(palette.text)

            HStack(spacing: 8) {
                Spacer(minLength: 0)
                Button("Cancel") { customSize = false }
                    .keyboardShortcut(.cancelAction)
                Button("OK") { commitCustomSize() }
                    .keyboardShortcut(.defaultAction)
            }
        }
        .padding(16)
        .frame(width: 268)
    }

    /// One measurement of the custom sheet, in document units.
    func sizeField(_ label: String, text: Binding<String>) -> some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return VStack(alignment: .leading, spacing: 5) {
            Text(label)
                .font(.system(size: 11))
                .foregroundStyle(palette.subtle)
            TextField("", text: text)
                .textFieldStyle(.plain)
                .font(.system(size: 12, design: .monospaced))
                .foregroundStyle(palette.ctrlText)
                .multilineTextAlignment(.trailing)
                .padding(.horizontal, 7)
                .frame(height: 22)
                .background(palette.ctrl, in: shape)
                .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
                .onSubmit { commitCustomSize() }
        }
    }

    func commitCustomSize() {
        customSize = false
        let width = Double(customWidthText.trimmingCharacters(in: .whitespaces))
        let height = Double(customHeightText.trimmingCharacters(in: .whitespaces))
        guard let width, let height else { return }
        host.setSlideSize(
            width: Float(width),
            height: Float(height),
            scaleContent: customScaleContent
        )
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

    /// One segment of a track: a capsule that fills with the accent while it is
    /// on, its label going white and semibold with it.
    func segment(_ label: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: Inspect.text, weight: on ? .semibold : .regular))
                .foregroundStyle(on ? palette.accentText : palette.text)
                // Three of them across the panel is a tight fit, so a long name
                // shrinks rather than truncating to nothing readable.
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(
                    on ? AnyShapeStyle(palette.accent) : AnyShapeStyle(Color.clear),
                    in: Capsule()
                )
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    /// The inner segmented controls: the same track as the panel's, at control
    /// height. Colour/Gradient, the transition trigger, the build trigger.
    func segmentTrack<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        HStack(spacing: 0) { content() }
            .padding(2)
            .frame(height: Inspect.control + 4)
            .background(palette.inspectorControl, in: Capsule())
    }

    /// A row of joined icon cells in one grey group, hairlined between them:
    /// B/I/U/S, the alignment four, and the Arrange pairs. The on cell fills
    /// with the accent, the way a segment does.
    func joinedIcons(_ cells: [IconCell]) -> some View {
        HStack(spacing: 0) {
            ForEach(Array(cells.enumerated()), id: \.offset) { index, cell in
                if index > 0 {
                    let touches = cells[index - 1].on || cell.on
                    (touches ? Color.clear : palette.tabDivider)
                        .frame(width: 1, height: 14)
                }
                Button(action: cell.action) {
                    Group {
                        if let symbol = cell.symbol {
                            Image(systemName: symbol).font(.system(size: 12))
                        } else {
                            Text(cell.title ?? "")
                                .font(.system(size: Inspect.text, weight: .medium))
                        }
                    }
                    .foregroundStyle(
                        cell.on ? palette.accentText : (cell.enabled ? palette.text : palette.faint)
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(
                        cell.on ? AnyShapeStyle(palette.accent) : AnyShapeStyle(Color.clear),
                        in: RoundedRectangle(cornerRadius: 5, style: .continuous)
                    )
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(!cell.enabled)
                .help(cell.help)
            }
        }
        .padding(1)
        .frame(height: Inspect.control)
        .background(palette.inspectorControl, in: RoundedRectangle(cornerRadius: Inspect.radius, style: .continuous))
    }

    /// Into layout mode, on the layout this slide is already on. No symbol, the
    /// way the design draws it: a bare label across the foot of the panel.
    var editLayoutButton: some View {
        panelButton("Edit Slide Layout") { host.editSlideLayouts() }
    }
}
