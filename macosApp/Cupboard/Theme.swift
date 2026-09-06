import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Chrome tokens

/// macOS chrome, transcribed from design/platform-theme.js. Chrome follows the
/// system appearance; slide content and thumbnails stay document-dark in both.
struct Palette {
    let text: Color
    let title: Color
    let label: Color
    let icon: Color
    let subtle: Color
    let dim: Color
    let faint: Color
    let ctrl: Color
    let ctrlText: Color
    let segBg: Color
    let segOn: Color
    let segOnText: Color
    let segOff: Color
    let track: Color
    let panel: Color
    let divider: Color
    let accent: Color
    let accentText: Color
    let accentSoft: Color
    let hover: Color
    let hover2: Color
    /// The glass fill painted over the blur.
    let glassFill: LinearGradient
    /// The raised-button fill, for the one button that is not a capsule.
    let buttonFill: LinearGradient
    let hairline: Color
    let innerHighlight: Color
    /// Active inspector tab: a grey pill, not a raised white one.
    let tabOn: Color
    let tabDivider: Color
    /// Navigator selection capsule and its specular top edge.
    let selection: Color
    let selectionEdge: Color
    /// The hairline around a thumbnail, identical selected or not.
    let thumbEdge: Color
    /// Build order rows: the resting fill, the hover, and the numbered badge a
    /// row wears while its element is not selected. An active row takes the
    /// accent for both.
    let rowBg: Color
    let rowHov: Color
    let badgeOff: Color
    let badgeOffText: Color

    static let dark = Palette(
        text: Color(rgb: 0xE8E8EA),
        title: Color(rgb: 0xD8D8DC),
        label: Color(rgb: 0xB8B8BE),
        icon: Color(rgb: 0xD0D0D5),
        subtle: Color(rgb: 0x98989F),
        dim: Color(rgb: 0xA0A0A8),
        faint: Color(rgb: 0x6E6E76),
        ctrl: Color(rgb: 0x414147),
        ctrlText: Color(rgb: 0xECECEE),
        segBg: Color(rgb: 0x313136),
        segOn: Color(rgb: 0x5C5C64),
        segOnText: .white,
        segOff: Color(rgb: 0xC8C8CC),
        track: Color(rgb: 0x4A4A50),
        panel: Color(rgb: 0x28282C),
        divider: Color(rgb: 0x3A3A3E),
        accent: Color(rgb: 0x7F52FF),
        accentText: .white,
        accentSoft: Color(rgb: 0xB9A3FF),
        hover: Color.white.opacity(0.07),
        hover2: Color.white.opacity(0.14),
        // rgba(44,44,50,0.56) to rgba(32,32,38,0.48)
        glassFill: LinearGradient(
            colors: [Color(rgb: 0x2C2C32).opacity(0.56), Color(rgb: 0x202026).opacity(0.48)],
            startPoint: .top,
            endPoint: .bottom
        ),
        buttonFill: LinearGradient(
            colors: [Color(rgb: 0x525259), Color(rgb: 0x47474D)],
            startPoint: .top,
            endPoint: .bottom
        ),
        hairline: Color.white.opacity(0.10),
        innerHighlight: Color.white.opacity(0.05),
        tabOn: Color.white.opacity(0.16),
        tabDivider: Color.white.opacity(0.18),
        selection: Color.white.opacity(0.17),
        selectionEdge: Color.white.opacity(0.30),
        thumbEdge: Color.white.opacity(0.16),
        rowBg: Color(rgb: 0x333338),
        rowHov: Color(rgb: 0x3C3C42),
        badgeOff: Color(rgb: 0x4A4A50),
        badgeOffText: Color(rgb: 0xD8D8DC)
    )

    static let light = Palette(
        text: Color(rgb: 0x2A2A2C),
        title: Color(rgb: 0x3A3A3C),
        label: Color(rgb: 0x5C5C5E),
        icon: Color(rgb: 0x4A4A4C),
        subtle: Color(rgb: 0x7A7A7E),
        dim: Color(rgb: 0x6A6A6E),
        faint: Color(rgb: 0x9A9A9E),
        ctrl: .white,
        ctrlText: Color(rgb: 0x2A2A2C),
        segBg: Color(rgb: 0xE1E0DE),
        segOn: .white,
        segOnText: Color(rgb: 0x1D1D1F),
        segOff: Color(rgb: 0x5A5A5C),
        track: Color(rgb: 0xCFCECC),
        panel: Color(rgb: 0xF1F0EE),
        divider: Color(rgb: 0xD8D7D5),
        accent: Color(rgb: 0x7F52FF),
        accentText: .white,
        accentSoft: Color(rgb: 0x6F42E0),
        hover: Color.black.opacity(0.06),
        hover2: Color.black.opacity(0.1),
        // rgba(252,251,249,0.64) to rgba(244,243,241,0.54)
        glassFill: LinearGradient(
            colors: [Color(rgb: 0xFCFBF9).opacity(0.64), Color(rgb: 0xF4F3F1).opacity(0.54)],
            startPoint: .top,
            endPoint: .bottom
        ),
        buttonFill: LinearGradient(
            colors: [.white, Color(rgb: 0xF1F1F1)],
            startPoint: .top,
            endPoint: .bottom
        ),
        hairline: Color.black.opacity(0.10),
        innerHighlight: Color.white.opacity(0.7),
        tabOn: Color.black.opacity(0.10),
        tabDivider: Color.black.opacity(0.14),
        selection: Color.white.opacity(0.68),
        selectionEdge: Color.white.opacity(0.95),
        thumbEdge: Color.black.opacity(0.14),
        rowBg: Color(rgb: 0xE7E6E4),
        rowHov: Color(rgb: 0xDEDEDD),
        badgeOff: Color(rgb: 0xC9C8C6),
        badgeOffText: Color(rgb: 0x3A3A3C)
    )

    static func of(_ scheme: ColorScheme) -> Palette { scheme == .dark ? .dark : .light }
}

enum Layout {
    static let sidebar: CGFloat = 212
    static let inspector: CGFloat = 282
    static let header: CGFloat = 52
    /// The navigator is a floating card, so it is inset from the window edges.
    static let cardLeading: CGFloat = 10
    static let cardTop: CGFloat = 8
    static let cardBottom: CGFloat = 10
    static let cardRadius: CGFloat = 14
    /// Left edge of the toolbar strip: clear of the card, or of the traffic
    /// lights and the collapsed toggle alone.
    static let toolbarOpen: CGFloat = 232
    static let toolbarClosed: CGFloat = 112
    /// Where the window controls sit inside the header, and how wide the group
    /// runs at the system pitch. The real extent is read off the buttons; this
    /// is only what the layout around them reserves.
    static let trafficLightsInset: CGFloat = 14
    static let trafficLightsWidth: CGFloat = 54
    /// Collapsed, the group sits at 18 and the show-sidebar capsule follows it.
    static let collapsedToggle: CGFloat = inset + trafficLightsWidth + edge
    static let notes: CGFloat = 122
    static let notesLeading: CGFloat = 256
    static let notesTrailing: CGFloat = 314
    static let notesInset: CGFloat = 48
    static let thumbnail: CGFloat = 150
    /// Share and the tab group ride over the inspector glass in a fixed region.
    static let tabRegion: CGFloat = 254
    static let edge: CGFloat = 14
    static let inset: CGFloat = 18
    static let panelPadding: CGFloat = 16
}

extension Color {
    /// Packed ARGB, the document model's colour format: what the background
    /// swatches carry, alpha included.
    init(argb: Int64) {
        self.init(
            .sRGB,
            red: Double((argb >> 16) & 0xFF) / 255,
            green: Double((argb >> 8) & 0xFF) / 255,
            blue: Double(argb & 0xFF) / 255,
            opacity: Double((argb >> 24) & 0xFF) / 255
        )
    }

    init(rgb: UInt32) {
        self.init(
            .sRGB,
            red: Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >> 8) & 0xFF) / 255,
            blue: Double(rgb & 0xFF) / 255
        )
    }
}

// MARK: - Glass

/// The blur under a glass surface. Within-window so it samples the Compose
/// canvas layered behind it rather than the desktop.
struct GlassBackdrop: NSViewRepresentable {
    let material: NSVisualEffectView.Material

    func makeNSView(context: Context) -> NSVisualEffectView {
        let view = NSVisualEffectView()
        view.material = material
        view.blendingMode = .withinWindow
        view.state = .active
        return view
    }

    func updateNSView(_ view: NSVisualEffectView, context: Context) {
        view.material = material
    }
}

enum GlassEdge { case leading, trailing }

struct Glass: ViewModifier {
    let material: NSVisualEffectView.Material
    let edge: GlassEdge
    let palette: Palette

    func body(content: Content) -> some View {
        content
            .background(GlassBackdrop(material: material))
            .background(palette.glassFill)
            .overlay(alignment: edge == .leading ? .leading : .trailing) { hairline }
    }

    /// The inner edge carries the 1px hairline with a fainter highlight just
    /// inside it (CSS: border + an inset shadow offset one pixel inward).
    @ViewBuilder private var hairline: some View {
        switch edge {
        case .leading:
            HStack(spacing: 0) {
                palette.hairline.frame(width: 1)
                palette.innerHighlight.frame(width: 1)
            }
        case .trailing:
            HStack(spacing: 0) {
                palette.innerHighlight.frame(width: 1)
                palette.hairline.frame(width: 1)
            }
        }
    }
}

/// The navigator's surface: the same glass, but as a rounded floating card with
/// a hairline all the way round and a soft shadow onto the canvas.
struct GlassCard: ViewModifier {
    let palette: Palette

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: Layout.cardRadius, style: .continuous)
    }

    func body(content: Content) -> some View {
        content
            .background(GlassBackdrop(material: .sidebar))
            .background(palette.glassFill)
            .clipShape(shape)
            .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
            .shadow(color: .black.opacity(0.18), radius: 13, x: 0, y: 8)
    }
}

extension View {
    func glass(_ material: NSVisualEffectView.Material, edge: GlassEdge, palette: Palette) -> some View {
        modifier(Glass(material: material, edge: edge, palette: palette))
    }

    func glassCard(palette: Palette) -> some View {
        modifier(GlassCard(palette: palette))
    }
}
