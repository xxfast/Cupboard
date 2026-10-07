import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Add a Slide

/// The toolbar's "+": a popover of the deck's layouts, the way Keynote's is, and
/// the slide it adds goes after the selected one on the layout picked. While the
/// layouts themselves are being edited it adds a layout instead, no popover.
/// The pill and the fallback bars come from the call site, so the button wears
/// the same capsule the Play button does.
struct AddSlideButton<Label: View, Bars: View>: View {
    let host: EditorHost
    let ui: Chrome
    let palette: Palette
    let bars: Bars
    @ViewBuilder let label: () -> Label

    @State private var open = false

    var body: some View {
        Button {
            if ui.editingLayouts { host.addLayout() } else { open.toggle() }
        } label: {
            label()
        }
        .buttonStyle(.plain)
        .help(ui.editingLayouts ? "Add layout" : "Add slide")
        .popover(isPresented: $open, arrowEdge: .bottom) {
            AddSlidePicker(host: host, layouts: ui.layouts, palette: palette, bars: bars) {
                open = false
            }
        }
    }
}

/// Three columns of layouts under a small caption. Picking one adds the slide
/// and closes the popover; the core selects the new slide.
struct AddSlidePicker<Bars: View>: View {
    let host: EditorHost
    let layouts: [LayoutChoice]
    let palette: Palette
    let bars: Bars
    let close: () -> Void

    private let columns = Array(repeating: GridItem(.fixed(AddSlideCell<Bars>.width), spacing: 10), count: 3)

    var body: some View {
        VStack(spacing: 10) {
            Text("Add a Slide")
                .font(.system(size: 11))
                .foregroundStyle(palette.subtle)
            LazyVGrid(columns: columns, spacing: 10) {
                ForEach(layouts) { layout in
                    AddSlideCell(host: host, layout: layout, palette: palette, bars: bars) {
                        host.addSlideAfterSelection(layoutId: layout.id)
                        close()
                    }
                }
            }
        }
        .padding(12)
        // Opaque, the way Keynote's is: the system material lets the canvas
        // through. The presentation background fills the arrow too.
        .presentationBackground(palette.panel)
    }
}

/// One layout: its thumbnail, drawn like the inspector's layout preview but
/// wider, and its name under it. The name always takes two lines' height so the
/// rows line up however the names wrap.
struct AddSlideCell<Bars: View>: View {
    static var width: CGFloat { 94 }

    let host: EditorHost
    let layout: LayoutChoice
    let palette: Palette
    let bars: Bars
    let pick: () -> Void

    @State private var hovering = false

    var body: some View {
        Button(action: pick) {
            VStack(spacing: 5) {
                thumbnail
                    .scaleEffect(hovering ? 1.08 : 1)
                    .animation(.easeOut(duration: 0.12), value: hovering)
                Text(layout.name)
                    .font(.system(size: 11))
                    .foregroundStyle(palette.subtle)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .frame(width: Self.width, height: 28, alignment: .top)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
    }

    /// The Compose render, or the drawn bars scaled up to the same width while
    /// it has not landed yet.
    @ViewBuilder var thumbnail: some View {
        let shape = RoundedRectangle(cornerRadius: 4, style: .continuous)
        if let image = host.layoutThumbnail(layoutId: layout.id, width: Int32(Self.width)) {
            Image(nsImage: image)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: Self.width)
                .clipShape(shape)
                .overlay { shape.inset(by: 0.5).stroke(palette.thumbEdge, lineWidth: 1) }
        } else {
            bars
                .scaleEffect(Self.width / 62, anchor: .topLeading)
                .frame(width: Self.width, height: 35 * Self.width / 62, alignment: .topLeading)
        }
    }
}
