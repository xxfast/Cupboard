import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Text

/// The Text segment of the Format panel for a text box, top to bottom the way
/// Keynote's Format > Text runs: the paragraph style card, the Style | Layout
/// switch, then the font, the character styles, the colour, the alignments,
/// Spacing and Bullets & Lists.
extension EditorView {
    /// Every control reads back off the document through the host, so what it
    /// shows is what the document holds and never a local copy.
    ///
    /// The character controls (font, weight, size, the toggles, the advanced
    /// options, the character style, the colour) go on the words selected in the
    /// box being edited, and with none selected on the whole of every text box in
    /// the selection; where the text disagrees with itself they read Multiple.
    /// The paragraph style, alignment, indents, spacing and lists belong to a
    /// whole line, so they are always the box's.
    func textSection(_ text: TextFormat, _ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            paragraphStyleCard(text)

            palette.divider.frame(height: 1)

            TextPanes { layout in
                VStack(alignment: .leading, spacing: 12) {
                    segmentTrack {
                        segment("Style", on: !layout.wrappedValue) { layout.wrappedValue = false }
                        segment("Layout", on: layout.wrappedValue) { layout.wrappedValue = true }
                    }

                    palette.divider.frame(height: 1)

                    if layout.wrappedValue {
                        notYet("Not yet: columns, text inset, indents, tabs, paragraph borders and background.")
                    } else {
                        textStyleBody(text, ui)
                    }
                }
            }
        }
    }

    /// The Style side, under the switch.
    func textStyleBody(_ text: TextFormat, _ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            fontRows(text)

            palette.divider.frame(height: 1)

            // The system colour panel is live: it commits on every sample it
            // sends, so a slow drag through it spends an undo entry per sample.
            // Same-colour writes cost nothing (the core drops an edit that
            // changes nothing), which takes the worst of it off.
            colorWell(text.isColorMixed ? "Text Colour: \(Inspect.multiple)" : "Text Colour", argb: text.color) {
                host.setSelectedTextColor(argb: $0)
            }

            palette.divider.frame(height: 1)

            alignmentRows(text)

            palette.divider.frame(height: 1)

            spacingSection(text, ui)

            palette.divider.frame(height: 1)

            listsSection(text, ui)
        }
    }

    // MARK: Paragraph style

    /// The card on top: the style's name set in the style's own face, and the
    /// styles the slide offers in its menu. A pick puts the whole box in it.
    func paragraphStyleCard(_ text: TextFormat) -> some View {
        let entries = text.paragraphStyleNames.enumerated().map { index, name in
            MenuEntry(title: name, checked: name == text.paragraphStyle) {
                host.setSelectedTextParagraphStyle(index: Int32(index))
            }
        }
        let shape = RoundedRectangle(cornerRadius: 9, style: .continuous)

        return PopUpButton(entries: entries) {
            HStack(spacing: 8) {
                Text(text.paragraphStyle)
                    .font(paragraphStyleFace(text, size: 22))
                    .foregroundStyle(palette.text)
                    .lineLimit(1)
                Spacer(minLength: 0)
                Image(systemName: "chevron.down")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(palette.inspectorTitle)
            }
            .padding(.horizontal, 12)
            .frame(height: 46)
            // A white card like the Slide Layout one: the only two controls in
            // the panel that are cards rather than controls.
            .background(palette.ctrl, in: shape)
            .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
            .contentShape(shape)
        }
    }

    /// The style's face at [size]: its named family where this Mac has it, else
    /// the generic one it stands in for, at its weight either way.
    func paragraphStyleFace(_ text: TextFormat, size: CGFloat) -> Font {
        let step = min(max(text.paragraphStyleWeight / 100, 1), 9) - 1
        if let family = text.paragraphStyleFontName,
           let face = NSFontManager.shared.font(
               withFamily: family,
               traits: text.paragraphStyleItalic ? .italicFontMask : [],
               weight: Self.appKitWeights[step],
               size: size
           ) {
            return Font(face)
        }

        let design: Font.Design =
            text.paragraphStyleFont == TextFont.serif ? .serif
            : text.paragraphStyleFont == TextFont.monospace ? .monospaced
            : .default
        let face = Font.system(size: size, weight: Self.swiftWeights[step], design: design)
        return text.paragraphStyleItalic ? face.italic() : face
    }

    /// 100 to 900 on AppKit's 0 to 15 scale, where 5 is regular and 9 bold.
    static let appKitWeights = [2, 3, 4, 5, 6, 8, 9, 10, 11]
    static let swiftWeights: [Font.Weight] = [
        .ultraLight, .thin, .light, .regular, .medium, .semibold, .bold, .heavy, .black,
    ]

    // MARK: Font

    /// What the text is set in and how it is dressed: the family, the weight and
    /// size, the four switches with the advanced options beside them, and the
    /// character style they add up to.
    func fontRows(_ text: TextFormat) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            sectionLabel("Font")

            fontPicker(text)

            HStack(spacing: 8) {
                stylePopup(
                    Self.weightTitles,
                    selected: text.weight.map(Self.weightIndex) ?? -1,
                    placeholder: Inspect.multiple
                ) { index in
                    host.setSelectedTextWeight(weight: Int32(Self.weights[index]))
                }

                ValueField(
                    label: "",
                    value: text.size,
                    palette: palette,
                    unit: "pt",
                    minimum: 1,
                    mixed: text.isSizeMixed
                ) { host.setSelectedTextSize(size: Float($0)) }
                .frame(width: Inspect.field + Inspect.stepper + 5)
            }

            HStack(spacing: 8) {
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

                textOptionsButton(text)
                    .frame(width: Inspect.field + Inspect.stepper + 5)
            }

            HStack(spacing: 8) {
                rowLabel("Character Styles")
                Spacer(minLength: 0)
                stylePopup(
                    CharacterStyle.entries.map(\.name),
                    selected: text.characterStyle.map { Int($0.ordinal) } ?? -1,
                    placeholder: Inspect.multiple,
                    pullDown: true
                ) { index in
                    host.setSelectedTextCharacterStyle(style: CharacterStyle.entries[index])
                }
                .frame(width: Inspect.rowPopup)
            }
        }
    }

    /// The gear beside the switches: Keynote's advanced options, the ones too
    /// rare for a row of their own, in a popover.
    func textOptionsButton(_ text: TextFormat) -> some View {
        let shape = RoundedRectangle(cornerRadius: Inspect.radius, style: .continuous)
        return PopoverButton {
            HStack(spacing: 2) {
                Image(systemName: "gearshape")
                    .font(.system(size: 13))
                Image(systemName: "chevron.down")
                    .font(.system(size: 8, weight: .bold))
            }
            .foregroundStyle(palette.text)
            .frame(maxWidth: .infinity)
            .frame(height: Inspect.control)
            .background(palette.inspectorControl, in: shape)
            .contentShape(shape)
            .help("Advanced Options")
        } content: {
            textOptions(text)
        }
    }

    /// The popover's rows, label left and control right.
    func textOptions(_ text: TextFormat) -> some View {
        Grid(alignment: .leading, horizontalSpacing: 12, verticalSpacing: 9) {
            GridRow {
                rowLabel("Baseline")
                stylePopup(
                    TextBaseline.entries.map(\.name),
                    selected: text.isBaselineMixed ? -1 : Int(text.baseline.ordinal),
                    placeholder: Inspect.multiple
                ) { index in
                    host.setSelectedTextBaseline(baseline: TextBaseline.entries[index])
                }
            }

            GridRow {
                rowLabel("Capitalisation")
                stylePopup(
                    Self.capitalizationTitles,
                    selected: text.isCapitalizationMixed ? -1 : Int(text.capitalization.ordinal),
                    placeholder: Inspect.multiple
                ) { index in
                    host.setSelectedTextCapitalization(capitalization: TextCapitalization.entries[index])
                }
            }

            // Default is the face's own ligatures; None turns them off. A box's
            // alone, so never Multiple.
            GridRow {
                rowLabel("Ligatures")
                stylePopup(Self.ligatureTitles, selected: text.ligatures ? 0 : 1) { index in
                    host.setSelectedTextLigatures(ligatures: index == 0)
                }
            }

            GridRow {
                rowLabel("Character Spacing")
                ValueField(
                    label: "",
                    value: text.letterSpacing,
                    palette: palette,
                    unit: "pt",
                    decimals: Self.decimals(text.letterSpacing, most: 1),
                    minimum: -100,
                    maximum: 100,
                    mixed: text.isLetterSpacingMixed
                ) { host.setSelectedTextLetterSpacing(points: Float($0)) }
                .frame(width: Inspect.field + Inspect.stepper + 5)
            }
        }
        .frame(width: 280)
        .padding(14)
    }

    /// `TextCapitalization.entries`, in order, Keynote's words for them.
    static let capitalizationTitles = ["None", "All Caps", "Small Caps", "Title Case"]
    static let ligatureTitles = ["Use Default", "Use None"]

    // MARK: Alignment

    /// Which edge the lines run to, the indent pair, and where the text sits
    /// down its box.
    func alignmentRows(_ text: TextFormat) -> some View {
        VStack(spacing: 8) {
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
                IconCell(
                    symbol: "text.justify",
                    on: text.align == TextAlign.justify,
                    help: "Justify"
                ) { host.setSelectedTextAlign(align: TextAlign.justify) },
            ])

            // Momentary: neither is ever on, and outdent greys while no line is
            // nested.
            joinedIcons([
                IconCell(symbol: "decrease.indent", enabled: text.canOutdent, help: "Decrease Indent") {
                    host.outdentSelectedText()
                },
                IconCell(symbol: "increase.indent", help: "Increase Indent") {
                    host.indentSelectedText()
                },
            ])

            joinedIcons([
                IconCell(
                    symbol: "arrow.up.to.line",
                    on: text.verticalAlign == VerticalAlign.top,
                    help: "Align Top"
                ) { host.setSelectedTextVerticalAlign(align: VerticalAlign.top) },
                IconCell(
                    symbol: "arrow.down.and.line.horizontal.and.arrow.up",
                    on: text.verticalAlign == VerticalAlign.middle,
                    help: "Align Middle"
                ) { host.setSelectedTextVerticalAlign(align: VerticalAlign.middle) },
                IconCell(
                    symbol: "arrow.down.to.line",
                    on: text.verticalAlign == VerticalAlign.bottom,
                    help: "Align Bottom"
                ) { host.setSelectedTextVerticalAlign(align: VerticalAlign.bottom) },
            ])
        }
    }

    // MARK: Spacing

    /// How far apart the lines and paragraphs sit. Collapsed, the header popup
    /// says the line spacing and offers the common multiples; open, the popup
    /// goes and the rows under it say all of it.
    func spacingSection(_ text: TextFormat, _ ui: Chrome) -> some View {
        let open = ui.expandedSections.contains("Spacing")
        return popupDisclosure("Spacing", ui) {
            if !open {
                PopUpButton(entries: linePresetEntries(text)) {
                    popupFace(lineSpacingTitle(text))
                }
                .frame(width: Inspect.rowPopup)
            }
        } body: {
            VStack(spacing: 8) {
                HStack(spacing: 8) {
                    stylePopup(
                        text.lineSpacingModeTitles,
                        selected: text.lineSpacingModes.firstIndex(of: text.lineSpacingMode) ?? -1
                    ) { index in
                        host.setSelectedTextLineSpacingMode(mode: text.lineSpacingModes[index])
                    }
                    .frame(width: 100)

                    Spacer(minLength: 0)

                    lineSpacingField(text)
                        .frame(width: Inspect.field + Inspect.stepper + 5)
                }

                pointsRow("Before Paragraph", text.spacingBefore) {
                    host.setSelectedTextParagraphSpacingBefore(points: Float($0))
                }

                pointsRow("After Paragraph", text.spacingAfter) {
                    host.setSelectedTextParagraphSpacingAfter(points: Float($0))
                }
            }
        }
    }

    /// The spacing value in the box's mode: a multiple of the size in Lines,
    /// points in the other three.
    @ViewBuilder func lineSpacingField(_ text: TextFormat) -> some View {
        if text.lineSpacingMode == LineSpacingMode.lines {
            ValueField(
                label: "",
                value: text.lineHeight,
                palette: palette,
                decimals: Self.decimals(text.lineHeight, least: 1, most: 2),
                step: 0.1,
                minimum: 0.5
            ) { host.setSelectedTextLineHeight(lineHeight: Float($0)) }
        } else {
            ValueField(
                label: "",
                value: text.lineHeight,
                palette: palette,
                unit: "pt",
                decimals: Self.decimals(text.lineHeight, most: 1),
                minimum: text.lineSpacingMode == LineSpacingMode.exactly ? 1 : 0
            ) { host.setSelectedTextLineHeight(lineHeight: Float($0)) }
        }
    }

    /// A label and a points field at the row's right.
    func pointsRow(_ label: String, _ value: Double, onCommit: @escaping (Double) -> Void) -> some View {
        HStack(spacing: 8) {
            rowLabel(label)
            Spacer(minLength: 0)
            ValueField(
                label: "",
                value: value,
                palette: palette,
                unit: "pt",
                decimals: Self.decimals(value, most: 1),
                minimum: 0,
                onCommit: onCommit
            )
            .frame(width: Inspect.field + Inspect.stepper + 5)
        }
    }

    /// What the collapsed header says: "1.3" in lines, "24 pt" otherwise.
    func lineSpacingTitle(_ text: TextFormat) -> String {
        if text.lineSpacingMode == LineSpacingMode.lines {
            return Self.number(text.lineHeight, least: 1, most: 2)
        }
        return "\(Self.number(text.lineHeight, most: 1)) pt"
    }

    /// The header popup's menu: the common multiples, ticked when the box is on one.
    func linePresetEntries(_ text: TextFormat) -> [MenuEntry] {
        Self.linePresets.map { lines in
            let on = text.lineSpacingMode == LineSpacingMode.lines && abs(text.lineHeight - lines) < 0.005
            return MenuEntry(title: String(format: "%.1f", lines), checked: on) {
                host.setSelectedTextLines(lines: Float(lines))
            }
        }
    }

    static let linePresets: [Double] = [1.0, 1.2, 1.5, 2.0]

    /// As few decimals as [value] needs, between [least] and [most]: 1.3 and
    /// 1.25 rather than 1.30, 24 pt rather than 24.0 pt. Read off a Float, so a
    /// few millionths either way still count as none.
    static func decimals(_ value: Double, least: Int = 0, most: Int) -> Int {
        var places = least
        while places < most {
            let scale = pow(10, Double(places))
            if abs((value * scale).rounded() - value * scale) < 0.001 { break }
            places += 1
        }
        return places
    }

    static func number(_ value: Double, least: Int = 0, most: Int) -> String {
        String(format: "%.\(decimals(value, least: least, most: most))f", value)
    }

    // MARK: Bullets & Lists

    /// What marks each paragraph, picked in the header the way Keynote's is.
    func listsSection(_ text: TextFormat, _ ui: Chrome) -> some View {
        popupDisclosure("Lists", ui, title: "Bullets & Lists") {
            stylePopup(
                text.listTitles,
                selected: text.lists.firstIndex(of: text.list) ?? -1,
                pullDown: true
            ) { index in
                host.setSelectedTextList(style: text.lists[index])
            }
            .frame(width: Inspect.rowPopup)
        } body: {
            notYet("Not yet: bullet, number and indent settings.")
        }
    }

    // MARK: Placeholders

    /// A line standing in for controls still to come, as quiet as the panel's
    /// other empty states.
    func notYet(_ words: String) -> some View {
        Text(words)
            .font(.system(size: 11.5))
            .foregroundStyle(palette.faint)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// The Text section's Style | Layout switch. Which side is showing is
/// view-local, like a style pager's page: it says nothing about the document.
struct TextPanes<Content: View>: View {
    @ViewBuilder let content: (Binding<Bool>) -> Content

    @State private var layout = false

    var body: some View { content($layout) }
}

/// A button that opens its content in a popover, open or closed being the
/// popover's own, like a colour well's.
struct PopoverButton<Label: View, Content: View>: View {
    @ViewBuilder let label: () -> Label
    @ViewBuilder let content: () -> Content

    @State private var open = false

    var body: some View {
        Button { open.toggle() } label: { label() }
            .buttonStyle(.plain)
            .popover(isPresented: $open, arrowEdge: .bottom) { content() }
    }
}
