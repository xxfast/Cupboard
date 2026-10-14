import SwiftUI
import CupboardCanvas

/// The deck theme's swatches as Swift values: Kotlin's grid, six to a row and
/// lightest row first, and the greys under it, white to black. Read when a
/// popover opens rather than on every render: nothing else draws them.
struct ThemeSwatches {
    let grid: [Int64]
    let greys: [Int64]

    init(_ host: EditorHost) {
        grid = host.paletteGrid().map { $0.int64Value }
        greys = host.paletteGreys().map { $0.int64Value }
    }
}

/// Keynote's colour well: a swatch of the current colour that opens the deck
/// theme's palette as a popover. The current colour on top, six hues by four
/// tints, the greys, and `none` ("No Fill", "No Border", "No Shadow") at the
/// foot, ticked while `isNone`. A nil `none` is a well that can't be empty.
///
/// A swatch commits through `onPick`, the same call the colour wheel makes, so a
/// pick is one edit and one undo entry. Open or closed is the popover's own.
struct ColourWell: View {
    let host: EditorHost
    let argb: Int64
    let palette: Palette
    var none: String? = nil
    var isNone = false
    var onNone: () -> Void = {}
    let onPick: (Int64) -> Void

    @State private var open = false

    var body: some View {
        Button { open.toggle() } label: {
            WellSwatch(argb: isNone ? nil : argb, palette: palette)
        }
        .buttonStyle(.plain)
        .popover(isPresented: $open, arrowEdge: .bottom) {
            ColourPopover(
                swatches: ThemeSwatches(host),
                argb: argb,
                palette: palette,
                none: none,
                isNone: isNone,
                onPick: { picked in
                    open = false
                    onPick(picked)
                },
                onNone: {
                    open = false
                    onNone()
                }
            )
        }
    }
}

/// What the well opens onto, top to bottom the way Keynote's runs. Gradient
/// and image rows are still to come.
private struct ColourPopover: View {
    let swatches: ThemeSwatches
    let argb: Int64
    let palette: Palette
    let none: String?
    let isNone: Bool
    let onPick: (Int64) -> Void
    let onNone: () -> Void

    private let columns = Array(repeating: GridItem(.fixed(28), spacing: 6), count: 6)

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Text("Current Colour")
                    .font(.system(size: 12))
                    .foregroundStyle(palette.text)
                WellSwatch(argb: isNone ? nil : argb, palette: palette)
                    .frame(height: 20)
            }

            Divider()

            Text("Colour Fills")
                .font(.system(size: 11))
                .foregroundStyle(palette.subtle)

            LazyVGrid(columns: columns, spacing: 6) {
                ForEach(Array(swatches.grid.enumerated()), id: \.offset) { _, swatch in
                    cell(swatch)
                }
            }

            HStack(spacing: 6) {
                ForEach(Array(swatches.greys.enumerated()), id: \.offset) { _, swatch in
                    cell(swatch)
                }
            }

            if let none {
                Divider()
                Button(action: onNone) {
                    HStack(spacing: 6) {
                        Image(systemName: "checkmark")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(palette.accent)
                            .opacity(isNone ? 1 : 0)
                        Text(none)
                            .font(.system(size: 13))
                            .foregroundStyle(palette.text)
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(12)
        .frame(width: 6 * 28 + 5 * 6 + 24)
        // Opaque like the Add Slide popover: the material lets the canvas through.
        .presentationBackground(palette.panel)
    }

    func cell(_ swatch: Int64) -> some View {
        let shape = RoundedRectangle(cornerRadius: 4, style: .continuous)
        let picked = !isNone && swatch == argb
        return Button { onPick(swatch) } label: {
            shape
                .fill(Color(argb: swatch))
                .frame(width: 28, height: 20)
                .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
                .overlay {
                    if picked { shape.inset(by: -2).stroke(palette.accent, lineWidth: 2) }
                }
                .contentShape(shape)
        }
        .buttonStyle(.plain)
    }
}

/// A colour drawn as a well, or the struck-through "none" well for nil.
private struct WellSwatch: View {
    let argb: Int64?
    let palette: Palette

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        shape
            .fill(argb.map { Color(argb: $0) } ?? palette.track)
            .overlay {
                if argb == nil {
                    GeometryReader { size in
                        Path { path in
                            path.move(to: CGPoint(x: 0, y: size.size.height))
                            path.addLine(to: CGPoint(x: size.size.width, y: 0))
                        }
                        .stroke(Color(red: 0.75, green: 0.29, blue: 0.29), lineWidth: 1.5)
                    }
                    .clipShape(shape)
                }
            }
            .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
            .contentShape(shape)
    }
}
