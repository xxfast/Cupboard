# Goals

`Cupboard` is a Keynote clone tailored for developers to present their code in style.

## Product

- Presentation editor for macOS, Windows, and Linux desktop.
- Each platform uses its native UI toolkit for app chrome:
  - macOS: SwiftUI / AppKit
  - Windows: WinUI 3
  - Linux: Compose for Desktop (Material 3)
- The center slide canvas is one shared Compose Multiplatform surface, pixel-identical on all three platforms. Fixed 944x531 (16:9) @1x, document-owned dark styling regardless of app theme.
- First-class code slides: syntax-highlighted source with step-based reveal/highlight animations.
- Full UI spec lives in `design/README.md`; `design/Slides Editor v2.dc.html` is the source of truth.

## Architecture

- Document model (slides, elements, builds) is serializable data in `shared`, not compiled-in composables.
- Editing canvas (selection, handles, alignment guides, drag) is our own Compose code.
- [CuP](https://github.com/KodeinKoders/CuP) drives the presentation runtime: Play mode, step/build animation, transitions, speaker window, laser, image/PDF export, and `cup-source-code` for highlighted code. Runtime `Slide` instances get built from the document model.
- CuP stays behind our own interface so it's swappable (it's beta, and not all targets exist yet).

## macOS strategy

- The macOS app has to be a SwiftUI app; only the canvas is CMP.
- Primary route: experimental Compose `macosArm64` target, hand-rolled `ComposeNSView` hosted via `NSViewRepresentable`.
- Safety net: we build Linux with Compose for Desktop anyway, so the same JVM shell works as the macOS fallback (native-looking chrome instead of real AppKit).

## CuP fork

- Fork CuP, add as a git submodule with Gradle `includeBuild` + dependency substitution, so we're unblocked locally.
- Contribute target support upstream as we go.
- Dependency chain for the native path: Emoji.kt macOS PR → CuP fork with `macosArm64` → our `ComposeNSView` spike.
- Keep the fork's diff to target additions + `actual`s only, so it stays mergeable upstream.

## Build order

1. Shared canvas + document model (pure common code, compiles for `jvm` and `macosArm64`)
2. Linux app on Compose for Desktop (doubles as the fallback shell for all three platforms)
3. macOS `NSViewRepresentable` spike, then the SwiftUI app
4. Windows (WinUI 3 chrome, HWND/islands embedding)
