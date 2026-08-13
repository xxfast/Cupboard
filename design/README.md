# Handoff: Cupboard — Cross-Platform Slides Editor

## Overview
Main editor window for **Cupboard**, a Keynote-style presentation editor built with Kotlin Multiplatform for macOS, Windows, and Linux desktop. Core architecture principle: **each platform renders its app chrome (toolbars, inspector, controls) with its native UI toolkit, while the slide canvas in the center is one shared Compose Multiplatform (Skia) surface, pixel-identical on all three platforms.**

Target repo: `xxfast/slides-kt` (branch `main`). At design time the repo was a fresh KMP template (`shared/src/commonMain/kotlin/io/github/xxfast/slides/` had only Greeting/Platform scaffolding) — these designs are the UI spec to build toward.

## About the Design Files
The files in this bundle are **design references created in HTML** — interactive prototypes showing intended look and behavior, **not production code to copy**. The task is to recreate these designs in the Cupboard codebase:
- **macOS chrome** → SwiftUI/AppKit (NSToolbar, NSSegmentedControl, NSPopUpButton…)
- **Windows chrome** → WinUI 3 (CommandBar, ComboBox, NumberBox, Slider…)
- **Linux chrome** → Compose Multiplatform Material 3 (FilledTonalButton, SegmentedButton, OutlinedTextField…)
- **Slide canvas** → shared Compose Multiplatform module, identical everywhere

`Slides Editor.dc.html` is the single source of truth: open it in a browser and use its tweak props (`os`: macOS/Windows/Linux, `theme`: Dark/Light) to view all 6 chrome variants. It is assembled from the component files listed under **Component Structure** below — each region of the window is its own file, so a change to (say) the navigator lands everywhere at once.

## Component System
The design is not one monolithic file. Each region of the editor is its own component file, and `Slides Editor.dc.html` is a thin shell that owns state and composes them:

```
Slides Editor.dc.html        page shell — window frame, all state, layout per platform
├── EditorToolbar            top chrome (three platform variants inside)
├── SlideNavigator           slide list: nesting, disclosure, selection
│   └── Slide                real miniature render, one per row
├── SlideCanvas              canvas surface + status bar
│   └── Slide                the same component, at zoom size
├── SpeakerNotes             presenter notes strip (floating on macOS, docked elsewhere)
├── Inspector                right panel: Format + Animate, three platform variants
│   └── BuildOrderRow        one build-order row, ×3
└── platform-theme.js        the 6-way token table (3 OS × dark/light) + sample deck
```

Two consequences worth carrying into the Kotlin port:

**One definition per thing.** `Slide` is authored once and appears in the canvas and in every navigator thumbnail, so the sidebar shows real renders of the document rather than look-alike graphics. `BuildOrderRow` is defined once and used nine times across three platform inspectors. Change either and every appearance follows.

**State lives at the top.** The shell holds selected slide, collapsed groups, active inspector tab, and zoom, and passes values plus callbacks down; every child is presentational. That maps directly onto Compose state hoisting — the shell becomes the window composable holding `remember`/`State`, children take parameters and emit events.

Colour, type, radius, and font tokens come from `platform-theme.js` via `SlidesTheme.resolve(os, mode)`; no component hardcodes a palette. Treat that file as the canonical token source when building the Kotlin theme.

## Fidelity
**High-fidelity.** Colors, typography, spacing, control sizing, and states are intentional and should be matched closely — *except* where a platform's real native control already embodies the pattern (e.g. a real NSToolbar or WinUI CommandBar). Prefer the genuine native control; use the mock's measurements to configure it, not to hand-draw it.

## Window Layout
The two platform families arrange the window **differently**, and this is deliberate — see *macOS 26 window arrangement* below for the reasoning and exact values.

### Windows / Linux — stacked
One full-width toolbar above three side-by-side opaque columns:

```
┌────────────────────────────────────────────────┐
│  Top chrome (NATIVE, per-OS — see below)       │
├────────┬───────────────────────────┬───────────┤
│ Slide  │  Canvas well (CMP)        │ Inspector │
│ nav    │  ┌─────────────────────┐  │ (NATIVE)  │
│ 224px  │  │ slide 944×531 @1x   │  │ 282px     │
│        │  └─────────────────────┘  │           │
│        │  Speaker notes (toggle)   │           │
│        │  Status bar 30px          │           │
└────────┴───────────────────────────┴───────────┘
```

- **Navigator (left, 224px fixed):** vertically scrolling slide thumbnails. Each row: 14px chevron gutter + mono slide number (11px, right-aligned, 14px col) + 136px 16:9 thumbnail, 4–6px gaps; 6px gap between rows; 10px padding (14px at the bottom). Selected thumbnail gets a 2px accent ring. Thumbnails render miniatures of slide content (document-dark gradient `linear-gradient(140deg, #2a2452, #1a1c2e)`), corner radius follows the OS token `thumbR` (mac 5px / win 4px / linux 10px).
- **Canvas well (center, flexible):** recessed background (`well` token), slide centered with 28px padding. Above the slide, a pill badge (mono 10.5px, violet `#9579e8` on `rgba(127,82,255,0.12)`, 1px `rgba(127,82,255,0.35)` border): "◆ compose canvas — identical on all platforms" — a design annotation; keep as a dev-build watermark or drop in production.
- **Speaker notes strip (toggleable):** its own component (`SpeakerNotes`), docked below the canvas in the middle column; panel background, 1px top border, label "SPEAKER NOTES" (10.5px, 700, 1.2px letter-spacing, faint) + body 13.5px/1.5.
- **Status bar (30px):** IBM Plex Mono 11.5px, faint. Left→right: `slide 4 / 8`, `944 × 531 @ 1x`, UI-toolkit label (e.g. `ui: SwiftUI / AppKit · dark`), spacer, `● synced` in green `#4caf7d`.
- **Inspector (right, 282px fixed):** native per OS; two modes — Format and Animate (see Interactions).

### macOS — layered
No title bar and no stacked toolbar. The canvas is a full-bleed layer edge to edge; the sidebar, inspector, and toolbar float **on top of it** as translucent glass, and the window controls live inside the sidebar:

```
┌────────────────────────────────────────────────┐
│ ●●● ▣ │ Untitled  ⏵ ⊞ ▣▣▣▣ ▣▣▣▣ 58%⌄ │ ⇪ ✎◇▣ │  ← sidebar header | floating toolbar | inspector header
│ 1 ▭   │·······································│       │
│ ⌄2 ▭  │······  slide, full-bleed canvas  ·····│ Text  │  ← canvas runs UNDER both panels
│  3 ▭  │·······································│       │
│  4 ▭  │·······································│       │
│ 5 ▭   │······ Speaker notes (floating) ·······│       │
└───────┴───────────────────────────────────────┴───────┘
  212px            full window width               282px
```

The dotted region is one continuous canvas layer at `inset: 0`; the panels are drawn over it. There is no status bar on macOS.

Two macOS details that differ from a plain "glass column" reading:

- **The navigator is a floating card, not a full-height column.** It is inset from the window — 10px left, 8px top, 10px bottom — with a 14px radius, a 1px inset hairline, and a soft drop shadow (`0 8px 26px rgba(0,0,0,0.18)`). The canvas and the speaker-notes strip both run underneath it.
- **The toolbar has no bar of its own.** There is no fill, no blur strip, and no bottom hairline behind it: the individual capsules float directly over the canvas. Only the controls are opaque.

## The Shared Canvas (Compose Multiplatform — identical on every OS)
Slide fixed at **944×531 (16:9) @ 1x**, radius 4px, background `linear-gradient(140deg, #2a2452 0%, #171930 55%, #101223 100%)`, shadow `0 12px 40px rgba(0,0,0,0.5)` + 1px `#33363d` ring. **Slide content is document-owned and stays dark in both app themes.**

Mocked-in canvas elements. **All copy in the mock is placeholder (lorem ipsum)** — it exists to show hierarchy, sizing, and element types, not to specify wording. Substitute real content when building.

1. **Selected text box** at (72, 64), 800×118, padding 10×14: title (Ubuntu 46px/700, white, -0.5px tracking) + subtitle 19px `#a9a0d8`. Selection: 1.5px `#7F52FF` border + **8 resize handles** (9×9px, white fill, 1.5px `#7F52FF` border, 2px radius) at corners and edge midpoints.
2. **Shape flow** at (72, 250): four 150×76 rounded-10px boxes joined by `→` glyphs, the third highlighted (18px, `#6f66a8`). Normal box: `rgba(127,82,255,0.22)` fill, 1.5px `rgba(169,143,255,0.7)` border, 15px/600 `#d9cfff` text. Highlighted (third) box: `rgba(245,197,24,0.14)` fill, `rgba(245,197,24,0.6)` border, `#ffe28a` text.
3. **Image placeholder** at (72, 372), 380×110: 1.5px dashed `rgba(255,255,255,0.3)`, radius 10, centered icon + drop-target label ("Drop image here") 13px `rgba(255,255,255,0.45)`.
4. **Body text** right column at (right:72, 372), 400w: 15px/1.55 `#b8b3d6`, contains a link (`#a98fff`).
5. **Alignment guide** (visible when dragging / `showGuides`): vertical 1px dashed `#f5c518` through slide center, extending 12px past edges, with a "center x" chip (10px/700 mono, `#17181c` on `#f5c518`, 1×6 padding, 3px radius) floating above.
6. **Build order badges** (Animate mode only): 18px violet `#7F52FF` circle with white number at each animated element's top-left, first badge also labeled "Fade Up · 0.4s" (11px `#a98fff`).

Canvas typography: **Ubuntu** for slide content, **IBM Plex Mono** for chrome metadata. Document accent: **Kotlin violet `#7F52FF`**.

## Per-OS Native Chrome

### macOS (SwiftUI / AppKit)
- Window: 12px corner radius. Font: SF Pro (`-apple-system`).
- **Unified NSToolbar** (title bar merged with toolbar): traffic lights (12px circles: `#ff5f57` `#febc2e` `#28c840`, 8px gap) top-left; centered window title — document name + ﹀ disclosure chevron (13px/600) on the top edge; toolbar items below as **icon-over-label** buttons (icon ~17px stroke 1.2–1.3, label 11px), 7px hover-rounded. Groups left→right: [View, Zoom (popup "100% ▾"), Add Slide] · [Play (filled triangle)] centered-ish · [Text, Shape, Media] · [Format, Animate] right. Active Format/Animate item: background `hov2`, icon+label tinted violet (`#b9a3ff` dark / `#6f42e0` light).
- **Inspector**: centered header title ("Format — Text" / "Animate — Build", 13px/600). AppKit-style small controls (~24px): popup buttons with the violet ▲▼ stepper cap (17px wide, `#7F52FF`), tiny segmented controls (B/I/U; alignment) with raised selected segment, 3px slider track with 15px white knob, small bordered value fields, mono values.

### Windows (WinUI 3)
- Window: 8px radius. Font: Segoe UI Variable.
- **Title bar (40px):** app icon (17px violet-gradient rounded square "S") + document name, `<name>.slides — Cupboard` (12px); caption buttons ─ ▢ ✕ (46px wide, close hovers `#c42b1c`).
- **CommandBar (48px):** accent-filled Play button (34px h, 4px radius, accent `#8961ff` dark / `#6f42e0` light), 1px divider, then quiet 34px buttons with 14px icons + 13px labels (New slide, Text, Shapes, Media); right: zoom ComboBox (bordered, `ctrlB` border with darker bottom edge `ctrlBB` — the Fluent "underline" affordance) and a "…" overflow button.
- **Inspector**: text tabs Format/Animate with 2.5px accent underline on the active tab. Fluent controls: 32px ComboBoxes/NumberBox (spinner ⌃⌄ inside right edge), B/I/U as 32px toggle buttons (active = accent fill), 4px-radius fields, slider with ring-style thumb (18px ring, 10px accent dot). Build-order rows: 4px radius with a 3px accent **left** border on the active row.

### Linux (Compose Desktop, Material 3)
- Window: 16px radius. Font: Roboto/Ubuntu.
- **Header bar (50px):** 22px rounded-7px accent app chip "S" + filename (14px/500); circular window buttons (30px, close hovers `#b3261e`).
- **Toolbar (60px):** M3 pill buttons 40px tall, radius 20 — filled Play (accent `#d0bcff`/on-accent `#381e72` dark; `#6750a4`/white light), tonal "Add slide" (`tonal`/`tonalText`); 40px circular icon buttons (Text/Shape/Image/Media); outlined pill zoom dropdown.
- **Inspector**: M3 tabs (centered label + 52×3px rounded indicator). Outlined text fields 44px with floating labels cut into the border; B/I/U and alignment as **outlined segmented button rows** (radius 20, ✓ check on selected, `segOn` `#4f378b`/`#e8ddff` dark, `#e8def8`/`#1d192b` light); 20px round slider thumb in accent; build-order rows as 12px-radius tonal cards.

## Theme (Dark / Light)
Theme switches **app chrome only** — slide content, thumbnails, and canvas stay document-dark. Every chrome color is a token; the full 6-way token table (3 OS × dark/light) lives in `platform-theme.js` (`window.SlidesTheme.resolve(os, mode)`) — treat that file as the canonical token source. Summary of shared token roles:

`chrome` (window bg) · `bar`/`barBorder` (toolbar) · `panel` (navigator/notes/status) · `well` (canvas backdrop) · `border`/`div` (separators) · `text`/`dim`/`subtle`/`subtle2`/`faint` (type ramp) · `hov`/`hov2` (hover fills) · `insBg` (inspector bg) · `ctrl`/`ctrlText`/`ctrlHov` (+ Win `ctrlB`/`ctrlBB`) (controls) · `segBg`/`segOn`/`segOnText`/`segOff` (segments) · `track`/`knob` (sliders) · `rowBg`/`rowHov`/`badgeOff`/`badgeOffText` (build rows) · Linux `outline`/`tonal`/`tonalText` · `accent`/`accentText` (platform accent).

Accents: mac `#7F52FF` (both modes); win `#8961ff` dark / `#6f42e0` light; linux `#d0bcff` dark / `#6750a4` light. Document accent is always `#7F52FF`.

## Interactions & Behavior
- **Inspector tabs:** on macOS the Format/Animate/Document group lives in the toolbar; on Windows/Linux they are inspector tabs (Format/Animate only). Switching swaps the panel; clicking the already-active tab closes the inspector entirely (macOS). Build-order badges appear on the canvas only while Animate is active *and* the inspector is open.
- **Sidebar toggle:** the icon at the navigator card's top-right hides/shows it; hidden, the traffic lights and a floating toggle capsule remain at the top-left.
- **Slide selection:** clicking a navigator thumbnail selects it (accent ring moves, status bar count updates). The mock deck is 8 slides, one of which (#3) has 3 slides nested under it; slide #4 is selected. See **Slide nesting** for the header/collapse behaviour.
- **Hover states:** every toolbar/inspector control has one (see per-OS notes: mac rounded `hov` fill; win `hov`/`ctrlHov` fills, red close; linux state-layer `hov`, brightness lift on filled pills).
- **Selection handles:** 8 handles on the selected canvas element; edge midpoints resize one axis, corners both.
- **Alignment guides:** appear during drag when element center/edges align with slide center; snap + yellow dashed guide with label chip.
- **Build order list:** rows are drag-reorderable (⠿ affordance); active row highlighted (per-OS treatment); "Add build" button appends.
- **Toggles:** speaker notes strip and guides are view toggles (`showNotes`, `showGuides` props in the mock).

## Speaker notes
One component, two placements, one visual treatment (panel background, 1px top border, "SPEAKER NOTES" label + 13.5px/1.5 body):

- **macOS:** a 122px strip pinned to the bottom of the window, spanning its **full width** and passing *behind* the navigator card (notes layer z-index sits below the panels, above the canvas). Its text is inset clear of whatever is open — 256px left with the sidebar shown (48px without), 314px right with the inspector open (48px without).
- **Windows/Linux:** docked at the bottom of the canvas column. The column is a grid (`grid-template-rows: minmax(0, 1fr) auto`) so the canvas fills and the notes bar sits flush.

## State Management
- `selectedSlideIndex: Int` — navigator + status bar
- `sidebarOpen`, `inspectorOpen: Boolean` — drive layout gutters, notes insets, and Fit
- `inspectorTab: Format | Animate | Document` — inspector content + canvas badge visibility (Document is macOS-only)
- `selectedElement: ElementId?` — handles + Format panel values (x/y/w/h, font, size, opacity)
- `showNotes`, `showGuides: Boolean`
- Per-element animation: `effect` (Fade Up/Pop/Dissolve), `duration` (s), `order`, `trigger` (after N / with N)
- Document model (slides, elements, builds) lives in shared KMP code; sync status surfaces as the `● synced` indicator.

## Design Tokens (non-theme)
- Slide: 944×531 @1x (16:9), radius 4
- Panels: navigator 224px, inspector 282px, status bar 30px
- Per-OS: window radius 12/8/16; thumb radius 5/4/10; control heights ~24 (mac) / 32 (win) / 40–44 (linux)
- Type: SF Pro / Segoe UI Variable / Roboto — chrome; Ubuntu — slide content; IBM Plex Mono — numerics & status
- Spacing: 16px inspector padding, 16px section gap, 1px `div` hairlines between sections

## App icon
The product mark ships in `icon/`: a two-shelf cupboard (Cup + Board) drawn on a 128-unit grid — 100×100 rounded-20 carcass with an 8px stroke, shelf on the horizontal centre axis, a 54×30 (16:9) board on the upper shelf with a play triangle knocked out of it, and a mug bottom-right (36-unit rim, r19 bowl, ring handle overlapping the wall). The gradient is CuP-derived: `#FFC24B → #FF7A59 → #F0357B` at 135°, with `#3C2A4D` plum ink on light and white ink on dark. `cupboard-icon-grid.svg` keeps the construction grid on a separate `#grid` layer.

## Assets
None required for the editor UI — all icons in the mock are simple inline strokes (~1.2–1.4px weight); use each platform's native icon set (SF Symbols / Segoe Fluent Icons / Material Symbols) with these as sizing reference. Fonts: Ubuntu + IBM Plex Mono (Google Fonts) for document content.

## Component Structure
The design is split into components mirroring the intended Compose structure. State is hoisted to the page and passed down, so each component is presentational apart from the row-building logic in the navigator.

| File | Role | Key props |
|---|---|---|
| `Slides Editor.dc.html` | Page shell — owns window frame + all state | `os`, `theme`, `showNotes`, `showGuides` |
| `EditorToolbar.dc.html` | Top chrome, all three platforms | `os`, `theme`, `tab`, `onFormat`, `onAnimate` |
| `SlideNavigator.dc.html` | Left slide list: nesting, disclosure, selection | `theme`, `slides`, `selected`, `collapsed`, `onSelect`, `onToggle` |
| `SlideCanvas.dc.html` | Shared Compose canvas + status bar | `theme`, `showGuides`, `showNotes`, `showBadges`, `slideNum`, `slideTotal`, `sidebarOpen`, `inspectorOpen` |
| `SpeakerNotes.dc.html` | Presenter notes: full-width floating strip on macOS, docked bar elsewhere | `os`, `theme`, `notes`, `sidebarOpen`, `inspectorOpen` |
| `Inspector.dc.html` | Right panel, Format + Animate, all three platforms | `os`, `theme`, `tab`, `onFormat`, `onAnimate` |
| `BuildOrderRow.dc.html` | One build-order row (used 3× per inspector) | `os`, `theme`, `n`, `label`, `meta`, `active` |
| `Slide.dc.html` | The slide itself — one authored render, reused at every size | `width`, `interactive`, `showGuides`, `showBadges`, `radius`, `shadow` |
| `platform-theme.js` | Canonical token table + sample deck | — |

Suggested Kotlin mapping: the page shell is the window `@Composable` holding state; `EditorToolbar` and `Inspector` become `expect/actual` (or interface + per-platform impl) since their chrome is native per OS; `SlideNavigator`, `SlideCanvas`, and `BuildOrderRow` are common Compose.

### The Slide component
The slide is authored **once** in `Slide.dc.html` at 944×531 and reused wherever a slide appears — the editor canvas and every navigator thumbnail are the same component at different sizes. Navigator thumbnails are therefore *real renders*, not stand-in bar graphics: what you see in the sidebar is what is on the canvas.

The only sizing input is `width` (px); the component derives its own height (16:9) and internal scale, so a consumer just asks for the width it has room for. The canvas passes `width = zoom% × 1920`; the navigator passes 150 (macOS) or 136 (Windows/Linux). Editing chrome — selection border, resize handles, guides, build-order badges — is gated behind `interactive`, so thumbnails render the artwork alone.

**Editing affordances hold a constant screen size at every zoom** — selection handles (9px), resize-handle borders, the alignment-guide line and its "center x" chip (10px), and build-order badges (18px) are UI overlays, not document content, so they must not grow with the slide. Keynote behaves the same way. Because they live inside the scaled board, each authored length is pre-divided by the scale factor, so painting multiplies it back to the intended pixel size. In Compose, draw these in the canvas's screen space (or divide by the zoom factor) rather than in slide coordinates.

In Compose terms this is the natural split: one `Slide` composable taking a size and a read-only flag, called from both the canvas and the thumbnail list; the 944×531 authored space becomes the layout's design coordinate system.

### Canvas zoom
The zoom control is live, not decorative. Slides are **1920×1080 natively** and zoom is a percentage of that native size, as in Keynote — so the number in the toolbar means the same thing it does there. Clicking the zoom pill opens a menu: *Fit in window*, then 25 / 50 / 75 / 100 / 125 / 150 / 200%, with a check mark on the current choice. All three platforms share the menu; each renders it with its own radius, fill, and hairline, anchored under its own zoom control.

"Fit" is **measured, not a constant.** It is the smaller of the two axis fits against the space the slide may actually occupy:

```
fitW = (shellWidth  - leftGutter - rightGutter) / 1920
fitH = (shellHeight - toolbarStrip - notesStrip) / 1080
fit  = clamp(20, 200, floor(min(fitW, fitH) * 100))
```

Gutters are live: the navigator contributes 212px only while it is shown, the inspector 282px only while it is open, and the notes strip 122px only while notes are on (Windows/Linux use 224px + 56px of padding instead). Measure the editor shell itself — a `ResizeObserver` on the shell element in the mock, the window/layout size in Compose — not the outer viewport. In the default macOS layout this lands around 58%; hiding both panels grows it accordingly. Zooming past Fit clips at the canvas bounds rather than reflowing anything.

Note the interaction with layering: the under-panel bleed that sells the glass only appears at or near Fit on macOS. At 50% the slide sits wholly inside the visible gap and the panels read as flat — that is correct behaviour, not a regression.

### macOS 26 window arrangement
macOS uses a **layered** window, not a stacked one — this differs structurally from Windows/Linux, which keep a single full-width toolbar above three side-by-side columns.

Layer order, back to front:
1. **Canvas** — full-bleed, edge to edge across the whole window. The slide is centred in the *window*, sized to run under both side panels, so it is partly obscured by them.
2. **Toolbar** — floats over the canvas, spanning only the gap between the two side panels (52px tall).
3. **Sidebar and inspector** — full-height glass panels pinned to the left (212px) and right (282px) edges, drawn over the canvas.

The two side panels are translucent **and blurred**: `backdrop-filter: blur(34px) saturate(190%)` over a fill of roughly 50–56% opacity, so the slide behind them shows through softened and colour-lifted rather than sharply. That is the point — the panels must read as glass sitting on top of the document, not as opaque columns beside it. Each carries a 1px hairline on its inner edge and an inset top highlight for the specular edge. The toolbar layer has no surface at all (its buttons are individually opaque capsules). Windows and Linux panels stay fully opaque.

**Both side panels are collapsible, and the layout reflows around them:**

| Control | Effect |
|---|---|
| Sidebar toggle (top-right of the navigator card) | Hides the navigator. Traffic lights and a floating "show sidebar" capsule take its place at the top-left; the toolbar shifts left (232px → 112px); canvas left gutter and notes inset drop to 0. |
| Clicking the **active** inspector tab | Closes the inspector; the tab pill clears so no tab reads active. Clicking any tab reopens it on that panel. |

Fit recomputes on every one of these transitions, and the speaker-notes insets follow the same state.

**Window controls live inside the sidebar**, not in a title bar: traffic lights top-left of the sidebar, sidebar-toggle icon at its top-right, both in a 52px header above the thumbnails. The document name and "Edited" state sit at the left of the floating toolbar instead. There is no status bar on macOS; speaker notes render as a floating glass strip along the bottom, spanning the same gap as the toolbar.

Toolbar contents, left to right: document name + "Edited" state; then centred — play and add-slide (36×30 round capsules), an insert cluster of five icons grouped in one raised capsule, and comment; then, right-aligned: the **zoom pill**, **Share**, and the **inspector tab group**.

The right end is positioned against the inspector column, mirroring Keynote:

- The **zoom pill** stays outside the panel, 14px clear of its left edge.
- **Share** and the **tab group** sit in a fixed 254px region laid out `space-between`, so Share is inset 14px inside the panel's left edge and the tabs are inset 14px from the window's right edge — both floating *over* the panel glass (toolbar layer is above the panels in z-order).
- With the inspector closed that region collapses to `auto` and the three controls fall back into a normal spaced row.

**The tab group has three tabs, not two: Format (angled paintbrush) · Animate (rounded diamond with a centre line) · Document (filled slide).** They live in one raised white capsule with 1px hairline dividers between them; the active tab is a **grey pill** (`rgba(0,0,0,0.10)` light / `rgba(255,255,255,0.16)` dark — not a raised white pill), and the divider adjacent to the active tab is hidden. All three icons keep the same ink colour. The inspector title beneath reads **Text / Build / Slide** to match.

The **Document panel** (macOS) contains: a Slide Layout card (62×35 mini preview + label + value + ⌄), an Appearance checklist (Title, Body, Slide Number), a Background section (Standard/Dynamic segmented control, "Colour Fill" popup, colour swatch + colour-wheel button), and an "Edit Slide Layout" button pinned to the bottom. Windows and Linux keep two tabs (Format/Animate).

### Slide nesting
Row chrome differs per platform (see below); the nesting *model* is shared and follows Keynote exactly: **there is no separate header row type.** Every navigator row is an ordinary slide (`{ title: String, depth?: Int }`) with a thumbnail and a number. A slide that has deeper slides directly beneath it renders a disclosure chevron; where that chevron sits differs per platform (below). 

**The chevron's placement differs per platform.** On Windows/Linux it sits in a fixed 14px gutter at the row's leading edge, outside the thumbnail, and slides without children leave that gutter empty so all thumbnails stay aligned. **On macOS there is no leading gutter at all** — a parent slide renders its disclosure chevron on its own 22px full-width strip *underneath* the parent row (⌄ expanded / › collapsed, centred), which is what keeps macOS rows flush to the sidebar's left edge. Children indent per level: 16px on macOS, 18px on Windows/Linux.

**macOS row chrome** matches Keynote on macOS 26 (the Liquid Glass build): the slide number sits *outside* the thumbnail, bottom-aligned to its lower edge — 11px system font, right-aligned in a 12px column, indenting with the thumbnail. Selection is a translucent capsule (10px radius, 5px 6px padding) that **hugs its content** — number + thumbnail — rather than spanning the row width, with a 1px inset top-edge highlight; the thumbnail takes **no accent ring** — the capsule alone marks selection, so thumbnails read identically selected or not. Thumbnails are 150px wide with 4px corners and a 1px inset hairline, no drop shadow, no title caption; rows sit 2px apart. Windows and Linux keep the tighter mono-numbered rows (136px thumbnails, 6px gaps) with an accent ring on selection. Numbering is absolute over the whole deck — collapsing a group hides its children but never renumbers the slides after it. Clicking the chevron toggles the group and does not change the selection (the handler stops propagation); clicking anywhere else in the row selects that slide.

## Files in this bundle
This folder is self-contained — commit it as `design/` in the repo.
- `Slides Editor.dc.html` — open in a browser; its tweak props (`os`, `theme`) give all 6 chrome variants.
- The other `.dc.html` files + `platform-theme.js` + `support.js` — the components it composes and the token table.
- `App Icon.dc.html` — the icon exploration page (turns 1–4, including the construction grid).
- `icon/` — app icon SVGs (light, dark, and a grid-overlay construction version).

- `Slides Editor.dc.html` — **source of truth.** All 6 variants via `os` + `theme` props; Format/Animate interaction working.
- `EditorToolbar` / `SlideNavigator` / `SlideCanvas` / `SpeakerNotes` / `Inspector` / `BuildOrderRow` `.dc.html` — the components it composes.
- `platform-theme.js` — token table + sample deck.
- `github.md` — repo association notes.
