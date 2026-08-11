# Roadmap

What we're building and in what order. See `GOALS.md` for the why, `design/README.md` for the UI spec. Detail on finished work lives in git history; this file stays forward-looking.

## Phase 0: Scaffolding (done)

Research, shared foundations, architecture. The short version:

- [x] CuP adopted as the presentation runtime behind our own interface (play mode, steps/builds; swappable). Forked as submodules `cup/` + `emoji-kt/` (branch `ir/macos-targets` in each, composite-built) to add `macosArm64`. Upstream drafts: KodeinKoders/CuP#12, kosi-libs/Emoji.kt#19.
- [x] Compose runs natively on macOS without a JVM; `ComposeNSView` embeds the shared canvas in SwiftUI via `NSViewRepresentable`. Known edge: no IME on native text input (`NSTextInputClient` in `ComposeNSView` eventually).
- [x] Serializable document model: flat Keynote-style nesting (depth + collapse, absolute numbering), native 1920x1080 slide space, rendered by pure common composables. Editor canvas with selection, 8 resize handles, guides + snap, zoom (Fit / 25-200%), screen-space overlays; pure-render thumbnails; syntax-highlighted code elements (`dev.snipme:highlights`, every target, no JS bridge).
- [x] Screen architecture per CLAUDE.md constraint 6: NYTimes-KMP-shaped MVI in `screens/<name>/` (serializable state + events, composable presenter run by Molecule, plain view model exposing `StateFlow`, stateless views), thin shell adapters, navigation per platform. Gestures commit once on release. Undo/redo: bounded document history in the presenter, Cmd+Z in both shells' real menus; revisit when deletion lands (selection can dangle) and when text editing needs burst coalescing.
- [x] Interim persistence: KStore autosaves `~/.cupboard/document.json` (debounced, both shells share the file) until the `.cupboard` bundle format exists.
- [x] Template app modules removed; `:cupboard` keeps its android/ios/web targets because iPad, Android tablet, and web apps are planned later.

## Phase 1: macOS app (SwiftUI)

- [x] Native shell scaffolding: `macosApp/Cupboard.xcodeproj` (Gradle `embedAndSignAppleFrameworkForXcode` phase + compose-resources staging, ad-hoc signing), `.app` packaging via `./macosApp/package.sh`, IDE run config through the KMP plugin.
- [x] Play mode wired in both shells (`EditorHost.startPlay` + toolbar Play button on macOS; Play window on desktop). Needs a visual pass.
- [ ] Promote the upstream fork PRs: mark KodeinKoders/CuP#12 and kosi-libs/Emoji.kt#19 ready, coordinate on Kotlin Slack `#cup-presentations`.
- [ ] SwiftUI chrome per design v3's layered window: full-bleed canvas with translucent glass panels floating over it (sidebar 212px with traffic lights in its header, inspector 282px, toolbar 52px spanning the gap); Format/Animate lives in the inspector, no status bar on macOS.
- [ ] Navigator rows per the Keynote 26 spec: capsule selection (no thumbnail ring), number outside the thumbnail, 14px chevron gutter, 20px/level indent.
- [ ] Embed the canvas full-bleed via `ComposeNSView` + `NSViewRepresentable` (as the window content layer at origin 0,0 this also retires the skiko Metal-layer offset band seen in the spike).
- [ ] Native inspector: Format + Animate panels with AppKit-style small controls, `BuildOrderRow` list with drag reorder.
- [ ] Fallback if the native route stalls: pull the Compose for Desktop shell (Phase 2) forward and ship it with mac-styled tokens.

## Phase 2: Linux app (Compose for Desktop)

- [ ] Full editor chrome in Material 3 per the design: navigator, toolbar, inspector (Format/Animate), speaker notes strip, status bar.
- [ ] This app doubles as the fallback shell for macOS and Windows, so keep OS-specific styling tokenized (`design/platform-theme.js` is the token source).
- [ ] CuP on JVM comes for free here: full plugin set (speaker window, export) plus `cup-source-code` (highlight.js via GraalVM).

## Phase 3: Windows app (WinUI 3)

- [ ] Same pattern as macOS: WinUI 3 chrome, embedded Compose canvas (HWND/islands embedding, better supported than the macOS direction), view models consumed via kotlin-native-nuget.
- [ ] Fallback: Compose for Desktop shell with Fluent-styled tokens.

## Open questions

- File format: single-file document vs bundle (embedded images argue for a bundle or zip). Interim is plain JSON via KStore; the plan is a `.cupboard` folder holding the JSON plus assets and whatever else a document grows.
- Sync (`● synced` in the design status bar) implies a backend at some point; out of scope for now.
