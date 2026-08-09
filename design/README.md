# Handoff: slides-kt — Cross-Platform Slides Editor

## Overview
Main editor window for **slides-kt**, a Keynote-style presentation editor built with Kotlin Multiplatform for macOS, Windows, and Linux desktop. Core architecture principle: **each platform renders its app chrome (toolbars, inspector, controls) with its native UI toolkit, while the slide canvas in the center is one shared Compose Multiplatform (Skia) surface, pixel-identical on all three platforms.**

Target repo: `xxfast/slides-kt` (branch `main`). At design time the repo was a fresh KMP template (`shared/src/commonMain/kotlin/io/github/xxfast/slides/` had only Greeting/Platform scaffolding) — these designs are the UI spec to build toward.

## About the Design Files
The files in this bundle are **design references created in HTML** — interactive prototypes showing intended look and behavior, **not production code to copy**. The task is to recreate these designs in the slides-kt codebase:
- **macOS chrome** → SwiftUI/AppKit (NSToolbar, NSSegmentedControl, NSPopUpButton…)
- **Windows chrome** → WinUI 3 (CommandBar, ComboBox, NumberBox, Slider…)
- **Linux chrome** → Compose Multiplatform Material 3 (FilledTonalButton, SegmentedButton, OutlinedTextField…)
- **Slide canvas** → shared Compose Multiplatform module, identical everywhere

`Slides Editor v2.dc.html` is the single source of truth: open it in a browser and use its tweak props (`os`: macOS/Windows/Linux, `theme`: Dark/Light) to view all 6 chrome variants. `Slides Editor.dc.html` is an earlier iteration kept for reference only.

## Fidelity
**High-fidelity.** Colors, typography, spacing, control sizing, and states are intentional and should be matched closely — *except* where a platform's real native control already embodies the pattern (e.g. a real NSToolbar or WinUI CommandBar). Prefer the genuine native control; use the mock's measurements to configure it, not to hand-draw it.

## Window Layout (all platforms)
One window, 3-column layout below the top chrome:

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

- **Navigator (left, 224px fixed):** vertically scrolling slide thumbnails. Each row: mono slide number (11px, right-aligned, 16px col) + 16:9 thumbnail with 8px gap; 12px gap between rows; 12px/14px padding. Selected thumbnail gets a 2px accent ring. Thumbnails render miniatures of slide content (document-dark gradient `linear-gradient(140deg, #2a2452, #1a1c2e)`), corner radius follows the OS token `thumbR` (mac 5px / win 4px / linux 10px).
- **Canvas well (center, flexible):** recessed background (`well` token), slide centered with 28px padding. Above the slide, a pill badge (mono 10.5px, violet `#9579e8` on `rgba(127,82,255,0.12)`, 1px `rgba(127,82,255,0.35)` border): "◆ compose canvas — identical on all platforms" — a design annotation; keep as a dev-build watermark or drop in production.
- **Speaker notes strip (toggleable):** below canvas; panel background, 1px top border, label "SPEAKER NOTES" (10.5px, 700, 1.2px letter-spacing, faint) + body 13.5px/1.5.
- **Status bar (30px):** IBM Plex Mono 11.5px, faint. Left→right: `slide 4 / 8`, `944 × 531 @ 1x`, UI-toolkit label (e.g. `ui: SwiftUI / AppKit · dark`), spacer, `● synced` in green `#4caf7d`.
- **Inspector (right, 282px fixed):** native per OS; two modes — Format and Animate (see Interactions).

## The Shared Canvas (Compose Multiplatform — identical on every OS)
Slide fixed at **944×531 (16:9) @ 1x**, radius 4px, background `linear-gradient(140deg, #2a2452 0%, #171930 55%, #101223 100%)`, shadow `0 12px 40px rgba(0,0,0,0.5)` + 1px `#33363d` ring. **Slide content is document-owned and stays dark in both app themes.**

Mocked-in canvas elements (slide "Rendering Pipeline"):
1. **Selected text box** at (72, 64), 800×118, padding 10×14: title "Rendering Pipeline" (Ubuntu 46px/700, white, -0.5px tracking) + subtitle 19px `#a9a0d8`. Selection: 1.5px `#7F52FF` border + **8 resize handles** (9×9px, white fill, 1.5px `#7F52FF` border, 2px radius) at corners and edge midpoints.
2. **Shape flow** at (72, 250): four 150×76 rounded-10px boxes (Compose → Layout → Draw → Present) joined by `→` glyphs (18px, `#6f66a8`). Normal box: `rgba(127,82,255,0.22)` fill, 1.5px `rgba(169,143,255,0.7)` border, 15px/600 `#d9cfff` text. Highlighted "Draw" box: `rgba(245,197,24,0.14)` fill, `rgba(245,197,24,0.6)` border, `#ffe28a` text.
3. **Image placeholder** at (72, 372), 380×110: 1.5px dashed `rgba(255,255,255,0.3)`, radius 10, centered icon + "Drop frame capture here" 13px `rgba(255,255,255,0.45)`.
4. **Body text** right column at (right:72, 372), 400w: 15px/1.55 `#b8b3d6`, contains a link (`#a98fff`).
5. **Alignment guide** (visible when dragging / `showGuides`): vertical 1px dashed `#f5c518` through slide center, extending 12px past edges, with a "center x" chip (10px/700 mono, `#17181c` on `#f5c518`, 1×6 padding, 3px radius) floating above.
6. **Build order badges** (Animate mode only): 18px violet `#7F52FF` circle with white number at each animated element's top-left, first badge also labeled "Fade Up · 0.4s" (11px `#a98fff`).

Canvas typography: **Ubuntu** for slide content, **IBM Plex Mono** for chrome metadata. Document accent: **Kotlin violet `#7F52FF`**.

## Per-OS Native Chrome

### macOS (SwiftUI / AppKit)
- Window: 12px corner radius. Font: SF Pro (`-apple-system`).
- **Unified NSToolbar** (title bar merged with toolbar): traffic lights (12px circles: `#ff5f57` `#febc2e` `#28c840`, 8px gap) top-left; centered window title "Rendering Pipeline ﹀" (13px/600) on the top edge; toolbar items below as **icon-over-label** buttons (icon ~17px stroke 1.2–1.3, label 11px), 7px hover-rounded. Groups left→right: [View, Zoom (popup "100% ▾"), Add Slide] · [Play (filled triangle)] centered-ish · [Text, Shape, Media] · [Format, Animate] right. Active Format/Animate item: background `hov2`, icon+label tinted violet (`#b9a3ff` dark / `#6f42e0` light).
- **Inspector**: centered header title ("Format — Text" / "Animate — Build", 13px/600). AppKit-style small controls (~24px): popup buttons with the violet ▲▼ stepper cap (17px wide, `#7F52FF`), tiny segmented controls (B/I/U; alignment) with raised selected segment, 3px slider track with 15px white knob, small bordered value fields, mono values.

### Windows (WinUI 3)
- Window: 8px radius. Font: Segoe UI Variable.
- **Title bar (40px):** app icon (17px violet-gradient rounded square "S") + "Rendering Pipeline.slides — slides-kt" (12px); caption buttons ─ ▢ ✕ (46px wide, close hovers `#c42b1c`).
- **CommandBar (48px):** accent-filled Play button (34px h, 4px radius, accent `#8961ff` dark / `#6f42e0` light), 1px divider, then quiet 34px buttons with 14px icons + 13px labels (New slide, Text, Shapes, Media); right: zoom ComboBox (bordered, `ctrlB` border with darker bottom edge `ctrlBB` — the Fluent "underline" affordance) and a "…" overflow button.
- **Inspector**: text tabs Format/Animate with 2.5px accent underline on the active tab. Fluent controls: 32px ComboBoxes/NumberBox (spinner ⌃⌄ inside right edge), B/I/U as 32px toggle buttons (active = accent fill), 4px-radius fields, slider with ring-style thumb (18px ring, 10px accent dot). Build-order rows: 4px radius with a 3px accent **left** border on the active row.

### Linux (Compose Desktop, Material 3)
- Window: 16px radius. Font: Roboto/Ubuntu.
- **Header bar (50px):** 22px rounded-7px accent app chip "S" + filename (14px/500); circular window buttons (30px, close hovers `#b3261e`).
- **Toolbar (60px):** M3 pill buttons 40px tall, radius 20 — filled Play (accent `#d0bcff`/on-accent `#381e72` dark; `#6750a4`/white light), tonal "Add slide" (`tonal`/`tonalText`); 40px circular icon buttons (Text/Shape/Image/Media); outlined pill zoom dropdown.
- **Inspector**: M3 tabs (centered label + 52×3px rounded indicator). Outlined text fields 44px with floating labels cut into the border; B/I/U and alignment as **outlined segmented button rows** (radius 20, ✓ check on selected, `segOn` `#4f378b`/`#e8ddff` dark, `#e8def8`/`#1d192b` light); 20px round slider thumb in accent; build-order rows as 12px-radius tonal cards.

## Theme (Dark / Light)
Theme switches **app chrome only** — slide content, thumbnails, and canvas stay document-dark. Every chrome color is a token; the full 6-way token table (3 OS × dark/light) lives in the `themes` object inside `Slides Editor v2.dc.html` — treat it as the canonical token source. Summary of shared token roles:

`chrome` (window bg) · `bar`/`barBorder` (toolbar) · `panel` (navigator/notes/status) · `well` (canvas backdrop) · `border`/`div` (separators) · `text`/`dim`/`subtle`/`subtle2`/`faint` (type ramp) · `hov`/`hov2` (hover fills) · `insBg` (inspector bg) · `ctrl`/`ctrlText`/`ctrlHov` (+ Win `ctrlB`/`ctrlBB`) (controls) · `segBg`/`segOn`/`segOnText`/`segOff` (segments) · `track`/`knob` (sliders) · `rowBg`/`rowHov`/`badgeOff`/`badgeOffText` (build rows) · Linux `outline`/`tonal`/`tonalText` · `accent`/`accentText` (platform accent).

Accents: mac `#7F52FF` (both modes); win `#8961ff` dark / `#6f42e0` light; linux `#d0bcff` dark / `#6750a4` light. Document accent is always `#7F52FF`.

## Interactions & Behavior
- **Format / Animate switch:** clicking toolbar Format/Animate (mac) or inspector tabs (win/linux) swaps the inspector panel. Entering Animate reveals build-order badges on the canvas; leaving hides them.
- **Slide selection:** clicking a navigator thumbnail selects it (accent ring moves, status bar count updates). Design shows 8 slides: slides-kt, Agenda, Why KMP, Rendering Pipeline (selected, #4), Scene Graph, Native Interop, Benchmarks, Roadmap.
- **Hover states:** every toolbar/inspector control has one (see per-OS notes: mac rounded `hov` fill; win `hov`/`ctrlHov` fills, red close; linux state-layer `hov`, brightness lift on filled pills).
- **Selection handles:** 8 handles on the selected canvas element; edge midpoints resize one axis, corners both.
- **Alignment guides:** appear during drag when element center/edges align with slide center; snap + yellow dashed guide with label chip.
- **Build order list:** rows are drag-reorderable (⠿ affordance); active row highlighted (per-OS treatment); "Add build" button appends.
- **Toggles:** speaker notes strip and guides are view toggles (`showNotes`, `showGuides` props in the mock).

## State Management
- `selectedSlideIndex: Int` — navigator + status bar
- `inspectorTab: Format | Animate` — inspector content + canvas badge visibility
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

## Assets
None required — all icons in the mock are simple inline strokes (~1.2–1.4px weight); use each platform's native icon set (SF Symbols / Segoe Fluent Icons / Material Symbols) with these as sizing reference. Fonts: Ubuntu + IBM Plex Mono (Google Fonts) for document content.

## Files
- `Slides Editor v2.dc.html` — **source of truth.** All 6 variants via `os` + `theme` props; token table in the `themes` object; Format/Animate interaction working.
- `Slides Editor.dc.html` — earlier iteration, reference only.
- `github.md` — repo association notes.
