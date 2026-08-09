# Roadmap

What we're building and in what order. See `GOALS.md` for the why, `design/README.md` for the UI spec.

## Phase 0: Research & spikes

- [x] Assess CuP viability. Verdict: use it as the presentation runtime (Play mode, steps/builds, speaker window, export, `cup-source-code`), not the editing canvas. Runtime `Slide` instances can be built from a document model via the direct `Slide(name, stepCount) {}` constructor. Apache-2.0, actively maintained, beta.
- [x] Verify Compose runs natively on macOS without a JVM. It does: `macosApp` builds a real arm64 binary with material3, animation, input, and state all working. Gated behind `org.jetbrains.compose.experimental.macos.enabled=true`. All our pinned deps (compose 1.11.1, material3, resources, lifecycle) publish `macos_arm64`.
- [x] Known rough edge: bare `.kexe` binaries don't bundle compose resources; the reader falls back to `src/commonMain/composeResources/` relative to cwd. Real fix is proper `.app` packaging (needed for distribution anyway).
- [x] Spike: `ComposeNSView` hosted in SwiftUI via `NSViewRepresentable`. **Works.** `macosApp` builds a `CupboardCanvas.framework`; `ComposeNSView` (adapted from `ComposeWindow.macos.kt`, ~200 lines) renders, clicks, drags, and types inside a real SwiftUI app (`macosApp/swift-host/`, run via `run.sh`). Needed internals are `@InternalComposeUiApi` (opt-in), only ~60 lines truly copied. Gotcha found: bare executables need `NSApp.setActivationPolicy(.regular)` or the window never becomes key (fixed in both hosts; moot once we ship a real `.app`).
- [x] Spike: text input on native macOS. **Basic typing works** (flows through raw `keyDown` events, embedded and standalone). Known limitation: compose 1.11.1's `MacosTextInputService` is a stub with no `NSTextInputClient`, so IME composition (dead keys, CJK, press-and-hold accents) is not wired up. Editor-grade text needs a real `NSTextInputClient` implementation in `ComposeNSView` eventually; not a blocker for now.

## Phase 1: Shared canvas + document model

- [ ] Serializable document model in `:cupboard`: slides, elements (text box, shape, image, code), builds/animations, speaker notes.
- [ ] Element renderers as pure common composables (no JVM-only deps anywhere; must compile for `jvm` and `macosArm64`).
- [ ] Canvas composable: fixed 944x531 @1x, container scaling, document-dark styling per the design.
- [ ] Editing layer: selection, 8 resize handles, drag, alignment guides + snap.
- [ ] Thumbnails reuse the same renderers at miniature scale.

## Phase 2: macOS app (SwiftUI)

Dependency chain: Emoji.kt macOS PR → CuP fork with `macosArm64` → `ComposeNSView`.

- [ ] Fork CuP as a git submodule, wire with Gradle `includeBuild` + dependency substitution. Keep the diff to target additions + `actual`s so it stays mergeable upstream.
- [ ] PR macOS targets to kosi-libs/Emoji.kt (blocks CuP core's `macosArm64`: it's an `api` dep). Coordinate on Kotlin Slack `#cup-presentations` first.
- [ ] SwiftUI chrome: unified NSToolbar, native inspector, navigator per the design.
- [ ] Embed the canvas via `ComposeNSView` + `NSViewRepresentable`.
- [ ] `.app` packaging with `compose-resources` in `Contents/Resources`.
- [ ] Play mode via CuP behind our own interface: document compiled to runtime `Slide`s, `PresentationState` driven by our UI.
- [ ] Code highlighting on native: JavaScriptCore-backed hljs actual, or precompute highlighting into the document model.
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
