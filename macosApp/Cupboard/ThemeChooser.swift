// File > New's theme chooser, after Keynote's: categories down the side, the
// theme grid beside them, Wide or Standard top right, Cancel and Create below.
// The pick lives in Kotlin's `ThemeChooserViewModel`; this file draws it and
// hands the made bundle's path to whoever opened it.
import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - State

/// [ThemeChooserHost]'s state, copied out whenever it changes: Swift can't
/// observe a Kotlin StateFlow, so the host calls back and this re-reads.
@Observable
final class ThemeChooserModel {
    let host = ThemeChooserHost()
    private(set) var categories: [String] = []
    private(set) var themes: [String] = []
    private(set) var category = ""
    private(set) var theme = ""
    private(set) var size = 0
    private(set) var sizeTitles: [String] = []
    private(set) var aspect: CGFloat = 16.0 / 9.0

    @ObservationIgnored private var unsubscribe: (() -> Void)?

    init() {
        // Subscribing is what starts the presenter, and it fires once at once.
        unsubscribe = host.onChange { [weak self] in self?.refresh() }
        refresh()
    }

    private func refresh() {
        categories = host.categoryNames()
        category = host.category()
        themes = host.themeNames(category: category)
        theme = host.theme()
        size = Int(host.size())
        sizeTitles = host.sizeTitles()
        aspect = CGFloat(host.aspect())
    }

    func close() {
        unsubscribe?()
        unsubscribe = nil
        host.close()
    }

    deinit { unsubscribe?() }
}

// MARK: - View

struct ThemeChooserView: View {
    /// Called with the new bundle's path. The caller opens it.
    let onCreate: (String) -> Void
    @State private var model = ThemeChooserModel()
    @Environment(\.dismissWindow) private var dismissWindow

    private static let thumbnailWidth: CGFloat = 164
    private let columns = Array(repeating: GridItem(.fixed(ThemeChooserView.thumbnailWidth), spacing: 20), count: 4)

    var body: some View {
        NavigationSplitView(columnVisibility: .constant(.all)) {
            sidebar
                .navigationSplitViewColumnWidth(220)
        } detail: {
            content
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea(edges: .vertical)
        .background(WindowFit())
        .onDisappear { model.close() }
    }

    // MARK: Sidebar

    private var sidebar: some View {
        List {
            ForEach(model.categories, id: \.self) { name in
                let selected = name == model.category
                HStack(spacing: 10) {
                    Image(systemName: Self.symbol(name))
                        .font(.system(size: 16))
                        .frame(width: 20)
                    Text(name)
                        .font(.system(size: 15))
                }
                    .foregroundStyle(selected ? Color.accentColor : Color.primary)
                    .frame(maxWidth: .infinity, minHeight: 36, maxHeight: 36, alignment: .leading)
                    .contentShape(Rectangle())
                    .onTapGesture { model.host.selectCategory(name: name) }
                    .listRowBackground(
                        RoundedRectangle(cornerRadius: 10)
                            .fill(selected ? AnyShapeStyle(.quaternary) : AnyShapeStyle(.clear))
                            .padding(.horizontal, 10)
                    )
            }
        }
        .listStyle(.sidebar)
        .padding(.top, 4)
        .environment(\.sidebarRowSize, .medium)
    }

    private static func symbol(_ category: String) -> String {
        switch category {
        case "Basic": "text.rectangle"
        case "Cupboard": "cabinet"
        default: "square.grid.2x2"
        }
    }

    // MARK: Content

    private var content: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                LazyVGrid(columns: columns, alignment: .leading, spacing: 0) {
                    ForEach(model.themes, id: \.self) { name in cell(name) }
                }
                .padding(.horizontal, 40)
                .padding(.top, 33)
                .padding(.bottom, 24)
            }
            Divider()
            footer
        }
        .ignoresSafeArea(edges: .vertical)
    }

    private var header: some View {
        HStack {
            Text(model.category)
                .font(.system(size: 34, weight: .bold))
            Spacer()
            Menu {
                ForEach(Array(model.sizeTitles.enumerated()), id: \.offset) { index, title in
                    Button {
                        model.host.selectSize(index: Int32(index))
                    } label: {
                        if index == model.size { Image(systemName: "checkmark") }
                        Text(Self.short(title))
                    }
                }
            } label: {
                HStack(spacing: 6) {
                    Text(model.sizeTitles.indices.contains(model.size) ? Self.short(model.sizeTitles[model.size]) : "")
                        .font(.system(size: 17))
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.system(size: 10, weight: .semibold))
                }
            }
            .menuStyle(.button)
            .buttonStyle(.plain)
            .menuIndicator(.hidden)
            .foregroundStyle(Color.accentColor)
            .fixedSize()
            .offset(y: 6.5)
        }
        .padding(.leading, 40)
        .padding(.trailing, 45)
        .padding(.top, 35)
        .padding(.bottom, 0)
    }

    /// "Widescreen (16:9)" is Kotlin's title for the size inspector; Keynote's
    /// chooser says "Wide".
    private static func short(_ title: String) -> String {
        title.replacingOccurrences(of: "Widescreen", with: "Wide")
    }

    private func cell(_ name: String) -> some View {
        let selected = name == model.theme
        let height = (Self.thumbnailWidth / model.aspect).rounded()
        return VStack(spacing: 6) {
            Group {
                if let image = model.host.thumbnail(themeName: name, width: Int32(Self.thumbnailWidth)) {
                    Image(nsImage: image).resizable().interpolation(.high)
                } else {
                    Color.gray
                }
            }
            .frame(width: Self.thumbnailWidth, height: height)
            .clipShape(RoundedRectangle(cornerRadius: 4))
            .overlay(RoundedRectangle(cornerRadius: 4).strokeBorder(Color.primary.opacity(0.12), lineWidth: 0.5))
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .strokeBorder(Color.accentColor, lineWidth: selected ? 3 : 0)
                    .padding(-4)
            )
            Text(name)
                .font(.system(size: 12))
                .foregroundStyle(selected ? Color.white : Color.primary)
                .padding(.horizontal, 4)
                .padding(.vertical, 2.5)
                .background(RoundedRectangle(cornerRadius: 3).fill(selected ? Color.accentColor : .clear))
        }
        .frame(height: 132, alignment: .top)
        .contentShape(Rectangle())
        // Selects on the first click; the double-click rides alongside rather
        // than ahead, or every click waits out the double-click interval.
        .onTapGesture { model.host.selectTheme(name: name) }
        .simultaneousGesture(TapGesture(count: 2).onEnded { create() })
    }

    // MARK: Footer

    private var footer: some View {
        HStack(spacing: 8) {
            Spacer()
            Button("Cancel") { dismissWindow(id: Self.windowId) }
                .keyboardShortcut(.cancelAction)
            Button("Create") { create() }
                .keyboardShortcut(.defaultAction)
                .buttonStyle(.borderedProminent)
        }
        .controlSize(.regular)
        .padding(.trailing, 20)
        .frame(height: 64)
    }

    static let windowId = "theme-chooser"

    private func create() {
        let path = model.host.create()
        guard !path.isEmpty else { return }
        onCreate(path)
        dismissWindow(id: Self.windowId)
    }
}


/// Pins the window to Keynote's 1024x768 with nothing above the content: the
/// sidebar's glass and the traffic lights sit on it, so there is no title bar
/// for SwiftUI to reserve room for.
private struct WindowFit: NSViewRepresentable {
    func makeNSView(context: Context) -> NSView {
        let view = NSView()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            guard let window = view.window else { return }
            window.styleMask.insert(.fullSizeContentView)
            window.contentMinSize = NSSize(width: 1024, height: 768)
            window.contentMaxSize = NSSize(width: 1024, height: 768)
            window.isRestorable = false
            // The split view's toggle is what gives the sidebar its inset glass
            // look, so the item stays and is only hidden.
            for item in window.toolbar?.items ?? [] {
                item.view?.isHidden = true
                if #available(macOS 15.0, *) { item.isHidden = true }
            }
            window.titlebarAppearsTransparent = true
            window.titleVisibility = .hidden
            window.setContentSize(NSSize(width: 1024, height: 768))
            for button in [NSWindow.ButtonType.closeButton, .miniaturizeButton, .zoomButton] {
                window.standardWindowButton(button)?.isHidden = false
            }
        }
        return view
    }

    func updateNSView(_ view: NSView, context: Context) {}
}
