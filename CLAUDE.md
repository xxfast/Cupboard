# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`Cupboard` is a Keynote-style presentation editor for developers to present code in style. Cross-platform desktop (macOS, Windows, Linux): each platform renders its app chrome with its native UI toolkit, while the slide canvas in the center is one shared Compose Multiplatform surface, pixel-identical everywhere. See `GOALS.md` for the full goal breakdown and `design/README.md` for the UI spec.

## Commands

- Build everything: `./gradlew build`
- Desktop app (hot reload): `./gradlew :desktopApp:hotRun --auto`
- Desktop app (standard): `./gradlew :desktopApp:run`
- Web: `./gradlew :webApp:wasmJsBrowserDevelopmentRun` (or `jsBrowserDevelopmentRun`)
- Android: `./gradlew :androidApp:assembleDebug`
- iOS: open `iosApp/` in Xcode
- Tests (all targets): `./gradlew :cupboard:allTests`
- Tests (JVM only, fastest): `./gradlew :cupboard:jvmTest`
- Single test class: `./gradlew :cupboard:jvmTest --tests "io.github.xxfast.cupboard.SomeTest"`
- Desktop distributables: `./gradlew :desktopApp:packageDistributionForCurrentOS` (Dmg/Msi/Deb)
- macOS SwiftUI host (dev loop): `./macosApp/run.sh`, or open `macosApp/Cupboard.xcodeproj` in Xcode
- macOS app bundle: `./macosApp/package.sh` (outputs `macosApp/build/Cupboard.app`)

## Module structure

- `cupboard/` – KMP library, targets: `android`, `iosArm64`/`iosSimulatorArm64` (static framework `Cupboard`), `jvm`, `js`, `wasmJs`. All product code goes in `commonMain` unless it genuinely needs a platform API.
- `desktopApp/` – Compose for Desktop (JVM) entry point, `io.github.xxfast.cupboard.MainKt`. This is the Linux app and the fallback shell for all platforms.
- `androidApp/`, `webApp/`, `iosApp/` – template entry points, not part of the core desktop goal (kept for now).
- `design/` – HTML design prototypes + handoff spec (`design/README.md`). Reference only, not production code.

Versions live in `gradle/libs.versions.toml` (Kotlin 2.4.x, Compose Multiplatform 1.11.x, AGP 9.x).

## Architecture constraints (these drive every design decision)

1. **The canvas module must stay target-clean.** No JVM-only deps (AWT, Swing, `java.*`) in the slide canvas, document model, or element renderers. They must compile for `jvm` (Linux + fallback) *and* `macosArm64` (native SwiftUI route). Platform hosting (windows, menus, file dialogs) stays out.
2. **Slide content is document-owned.** Slides render from a serializable document model (slides, elements, builds), not compiled-in composables. Slide stays dark in both app themes; fixed 944x531 @1x with container scaling.
3. **CuP integration goes behind our own interface.** [CuP](https://github.com/KodeinKoders/CuP) (`net.kodein.cup`) drives Play mode, step/build animation, speaker window, export, and source-code highlighting. It's beta, JVM/js/wasmJs-only, so it must be swappable. Runtime `Slide` instances are built from the document model via CuP's direct `Slide(name, stepCount) {}` constructor.
4. **macOS is planned as a real SwiftUI app** embedding the canvas via the experimental Compose `macosArm64` target (`NSViewRepresentable` + hand-rolled `ComposeNSView`), with Compose for Desktop as the safety fallback. Windows: WinUI 3 chrome, same pattern. Don't introduce anything into shared code that would break the native route.
5. **CuP will be forked as a git submodule** (Gradle `includeBuild` + dependency substitution) to add `macosArm64`, with changes PRed upstream. Known blocker chain: `org.kodein.emoji:emoji-compose` needs macOS targets first (also Kodein's, kosi-libs/Emoji.kt). Keep fork diffs minimal so rebases stay cheap.
6. **Screen state and mutations live in shared stores in `:cupboard`** (`EditorStore`); platform shells keep only thin adapters (observation bridging, platform types) and their own navigation. Stores notify non-Compose hosts via explicit subscribe callbacks, not snapshot machinery.
