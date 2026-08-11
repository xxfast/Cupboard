# Roadmap

What we're building and in what order. See `GOALS.md` for the why, `design/README.md` for the UI spec.

## Phase 0: Research & spikes

- [x] Assess CuP viability. Verdict: use it as the presentation runtime (Play mode, steps/builds, speaker window, export, `cup-source-code`), not the editing canvas. Runtime `Slide` instances can be built from a document model via the direct `Slide(name, stepCount) {}` constructor. Apache-2.0, actively maintained, beta.
- [x] Verify Compose runs natively on macOS without a JVM. It does: `macosApp` builds a real arm64 binary with material3, animation, input, and state all working. Gated behind `org.jetbrains.compose.experimental.macos.enabled=true`. All our pinned deps (compose 1.11.1, material3, resources, lifecycle) publish `macos_arm64`.
- [x] Known rough edge: bare `.kexe` binaries don't bundle compose resources; the reader falls back to `src/commonMain/composeResources/` relative to cwd. Real fix is proper `.app` packaging (needed for distribution anyway).
- [x] Spike: `ComposeNSView` hosted in SwiftUI via `NSViewRepresentable`. **Works.** `macosApp` builds a `CupboardCanvas.framework`; `ComposeNSView` (adapted from `ComposeWindow.macos.kt`, ~200 lines) renders, clicks, drags, and types inside a real SwiftUI app (`macosApp/Cupboard/`, run via `macosApp/run.sh`). Needed internals are `@InternalComposeUiApi` (opt-in), only ~60 lines truly copied. Gotcha found: bare executables need `NSApp.setActivationPolicy(.regular)` or the window never becomes key (moot now that the host is a real `.app` built by `Cupboard.xcodeproj`).
- [x] Spike: text input on native macOS. **Basic typing works** (flows through raw `keyDown` events, embedded and standalone). Known limitation: compose 1.11.1's `MacosTextInputService` is a stub with no `NSTextInputClient`, so IME composition (dead keys, CJK, press-and-hold accents) is not wired up. Editor-grade text needs a real `NSTextInputClient` implementation in `ComposeNSView` eventually; not a blocker for now.

## Phase 1: Shared canvas + document model

- [x] Serializable document model in `:cupboard`: slides (nestable groups per design v2, navigator indents 22px/depth with collapse), elements (text box, shape, image, code), builds/animations, speaker notes. The model is data, not composables: it compiles to CuP `Slide`/`SlideGroup` at runtime for Play mode, and could later export as a CuP Kotlin project.
- [x] Element renderers as pure common composables (no JVM-only deps anywhere; must compile for `jvm` and `macosArm64`).
- [x] Canvas composable: fixed 944x531 @1x, container scaling, document-dark styling per the design.
- [x] Editing layer: selection, 8 resize handles, drag, alignment guides + snap.
- [x] Thumbnails reuse the same renderers at miniature scale.

## Phase 1.5: Design v3 alignment (shared)

The 2026-08 design revision (`design/HANDOFF.md`) reshapes the shared layer; these supersede parts of Phase 1.

- [x] Flat Keynote-style nesting: no `SlideGroup` nodes or header rows; every navigator row is a slide with `depth` + `collapsed`, chevron in a 14px gutter, absolute numbering that survives collapse. (`f6e823b`)
- [x] Native slide space 1920x1080 (zoom is a percentage of native, like Keynote); sample deck and geometry rescaled. (`f6e823b`, same commit: both rewrite the sample deck)
- [x] Zoom-aware `SlideSurface` (Fit / 25-200%) with clipping past Fit, no reflow. (`d8664e8`; the zoom menu UI itself is chrome, tracked under each platform phase)
- [x] Editing overlays (handles, guides, chips, build badges) hold constant screen size at every zoom: drawn in screen space, not slide space. (`ce58f56`; build badges land with Animate mode)
- [x] Thumbnails become pure renders (no baked-in selection ring); selection chrome belongs to each host shell. (`287c8d6`)

## Phase 2: macOS app (SwiftUI)

Dependency chain: Emoji.kt macOS PR → CuP fork with `macosArm64` → `ComposeNSView`.

- [x] Fork CuP as a git submodule (`cup/`, branch `ir/macos-targets`), wire with Gradle `includeBuild` + dependency substitution. The fork adds `macosArm64` to all modules except `cup-widgets-source-code` (no native hljs) and bumps to Kotlin 2.4.10 / Compose 1.11.1; upstream draft PR: KodeinKoders/CuP#12.
- [x] PR macOS targets to kosi-libs/Emoji.kt (blocks CuP core's `macosArm64`: it's an `api` dep). Fork submodule at `emoji-kt/`, upstream draft PR: kosi-libs/Emoji.kt#19. Still to do: coordinate on Kotlin Slack `#cup-presentations` and promote both drafts.
- [ ] SwiftUI chrome per design v3's layered window: full-bleed canvas with translucent glass panels floating over it (sidebar 212px with traffic lights in its header, inspector 282px, toolbar 52px spanning the gap); Format/Animate lives in the inspector, no status bar on macOS.
- [ ] Navigator rows per the Keynote 26 spec: capsule selection (no thumbnail ring), number outside the thumbnail, 14px chevron gutter, 20px/level indent.
- [ ] Embed the canvas full-bleed via `ComposeNSView` + `NSViewRepresentable` (as the window content layer at origin 0,0 this also retires the skiko Metal-layer offset band seen in the spike).
- [ ] Native inspector: Format + Animate panels with AppKit-style small controls, `BuildOrderRow` list with drag reorder.
- [x] `.app` packaging with `compose-resources` in `Contents/Resources`. (`06dd768`, `./macosApp/package.sh`; retires the cwd-relative resource hack and the bare-binary activation-policy need)
- [x] Real Xcode project: `macosApp/Cupboard.xcodeproj` replaces the swiftc/run.sh harness. Gradle script phase runs `embedAndSignAppleFrameworkForXcode` (same pattern as `iosApp`), a second phase stages compose resources into the bundle, ad-hoc signing, shared scheme for headless `xcodebuild`. `run.sh` and `package.sh` are thin wrappers now; `xcodebuild archive` is available for real distribution later. (`9bb7b03`)
- [x] Play mode via CuP behind our own interface: document compiled to runtime `Slide`s, `PresentationState` driven by our UI. (`6e09398`; adapter + desktopApp wiring done and tested. SwiftUI host wired via `EditorHost.startPlay` + toolbar Play button in `2284b2c`. Needs a visual pass on both play surfaces)
- [x] Code highlighting on native: solved in the shared canvas instead. `dev.snipme:highlights` (pure Kotlin, all our targets) highlights `CodeElement`s in `commonMain`, so editor, thumbnails, and play mode get it on every platform; no JavaScriptCore bridge or `cup-source-code` needed. Atom One Dark until the design defines a code palette. (`0e66339`)
- [ ] Fallback if the native route stalls: pull the Compose for Desktop shell (Phase 3) forward and ship it with mac-styled tokens.

## Phase 3: Linux app (Compose for Desktop)

- [ ] Full editor chrome in Material 3 per the design: navigator, toolbar, inspector (Format/Animate), speaker notes strip, status bar.
- [ ] This app doubles as the fallback shell for macOS and Windows, so keep OS-specific styling tokenized (the `themes` object in the design mock is the token source).
- [ ] CuP on JVM comes for free here: full plugin set (speaker window, export) plus `cup-source-code` (highlight.js via GraalVM).

## Phase 4: Windows app (WinUI 3)

- [ ] Same pattern as macOS: WinUI 3 chrome, embedded Compose canvas (HWND/islands embedding, better supported than the macOS direction).
- [ ] Fallback: Compose for Desktop shell with Fluent-styled tokens.

## Open questions

- Do we keep the template `androidApp` / `webApp` / `iosApp` targets? Web could become CuP's web export as a share feature later.
- File format: single-file document vs bundle (embedded images argue for a bundle or zip).
- Sync (`● synced` in the design status bar) implies a backend at some point; out of scope for now.
