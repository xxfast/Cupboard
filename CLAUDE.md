# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`Cupboard` is a Keynote-style presentation editor for developers to present code in style. Cross-platform desktop (macOS, Windows, Linux): each platform renders its app chrome with its native UI toolkit, while the slide canvas in the center is one shared Compose Multiplatform surface, pixel-identical everywhere. See `GOALS.md` for the full goal breakdown and `design/README.md` for the UI spec.

## Commands

- There is no aggregate build: each shell builds with its own toolchain (Gradle for desktop, `xcodebuild` for macOS with a build phase linking Kotlin via Gradle, `dotnet build` for Windows later). `./gradlew build` is known-broken (composite-build quirk in the CuP fork's JS tooling) and not worth fixing; verify with the per-target tasks below.
- Desktop app (hot reload): `./gradlew :desktopApp:hotRun --auto`
- Desktop app (standard): `./gradlew :desktopApp:run`
- Tests (all targets): `./gradlew :cupboard:allTests :cupboard:ui:allTests`
- Tests (JVM only, fastest): `./gradlew :cupboard:jvmTest :cupboard:ui:jvmTest`
- Single test class: `./gradlew :cupboard:jvmTest --tests "io.github.xxfast.cupboard.SomeTest"`
- Core stays mingw-clean: `./gradlew :cupboard:compileKotlinMingwX64 :winuiApp:linkDebugSharedMingwX64`
- Windows NuGet package: `./gradlew :winuiApp:packNuget` (outputs `winuiApp/build/nuget/`)
- Desktop distributables: `./gradlew :desktopApp:packageDistributionForCurrentOS` (Dmg/Msi/Deb)
- macOS SwiftUI host (dev loop): `./macosApp/run.sh`, or open `macosApp/Cupboard.xcodeproj` in Xcode
- macOS app bundle: `./macosApp/package.sh` (outputs `macosApp/build/Cupboard.app`)

## Module structure

- `cupboard/` – the UI-free core: document model, presenters, view models. Targets: `android`, `iosArm64`/`iosSimulatorArm64`, `jvm`, `macosArm64`, `mingwX64`, `js`, `wasmJs`. Compose *runtime* only (molecule runs the presenters); no UI toolkit, which is what buys `mingwX64`. All product code goes in `commonMain` unless it genuinely needs a platform API.
- `cupboard/ui/` – `:cupboard:ui`, everything that draws: `canvas/`, `editor/EditorCanvas.kt`, `screens/*/XxxScreen.kt`, the `cup` play layer, and the iOS `Cupboard` framework. Same targets minus `mingwX64` (no Compose UI artifact publishes for it). `api`-depends on `:cupboard`, so shells depend on this one alone. The android/ios/web targets have no app module yet; iPad, Android tablet and web apps are planned later.
- `desktopApp/` – Compose for Desktop (JVM) entry point, `io.github.xxfast.cupboard.MainKt`. This is the Linux app and the fallback shell for all platforms.
- `macosApp/` – SwiftUI host + `CupboardCanvas.framework` (`macosArm64`), built via `Cupboard.xcodeproj`.
- `winuiApp/` – the Windows route: packs `:cupboard` as the `Cupboard.Kotlin` NuGet package (kotlin-native-nuget, `mingwX64` + `macosArm64` shared libs), plus the C# adapter (`Shared/`) and WinUI 3 skeleton (`WinUiApp/`). The C# half needs Windows and has never been compiled; see `winuiApp/README.md`.
- `design/` – HTML design prototypes + handoff spec (`design/README.md`). Reference only, not production code.

Versions live in `gradle/libs.versions.toml` (Kotlin 2.4.x, Compose Multiplatform 1.11.x, AGP 9.x).

## Architecture constraints (these drive every design decision)

1. **Both shared modules stay target-clean, at different strictnesses.** `:cupboard:ui` (slide canvas, element renderers) takes no JVM-only deps (AWT, Swing, `java.*`): it must compile for `jvm` (Linux + fallback) *and* `macosArm64` (native SwiftUI route). `:cupboard` is stricter: no UI toolkit at all, only Compose runtime, because `mingwX64` has no Compose UI to link against. Anything that touches a pixel goes in `:cupboard:ui`. Platform hosting (windows, menus, file dialogs) stays out of both.
2. **Slide content is document-owned.** Slides render from a serializable document model (slides, elements, builds), not compiled-in composables. Slide stays dark in both app themes; fixed 944x531 @1x with container scaling.
3. **CuP integration goes behind our own interface.** [CuP](https://github.com/KodeinKoders/CuP) (`net.kodein.cup`) drives Play mode, step/build animation, speaker window, export, and source-code highlighting. It's beta, JVM/js/wasmJs-only, so it must be swappable. Runtime `Slide` instances are built from the document model via CuP's direct `Slide(name, stepCount) {}` constructor.
4. **macOS is planned as a real SwiftUI app** embedding the canvas via the experimental Compose `macosArm64` target (`NSViewRepresentable` + hand-rolled `ComposeNSView`), with Compose for Desktop as the safety fallback. Windows: WinUI 3 chrome, same pattern. Don't introduce anything into shared code that would break the native route.
5. **CuP will be forked as a git submodule** (Gradle `includeBuild` + dependency substitution) to add `macosArm64`, with changes PRed upstream. Known blocker chain: `org.kodein.emoji:emoji-compose` needs macOS targets first (also Kodein's, kosi-libs/Emoji.kt). Keep fork diffs minimal so rebases stay cheap.
6. **Screens are shared MVI, modeled on NYTimes-KMP** (minus its Decompose routing). Each screen is a folder, `screens/<name>/`, with four files. The first three live in `:cupboard`, the fourth in `:cupboard:ui`:
   - `XxxStateModels.kt` – `@Serializable data class XxxState` (all derivations computed, never stored) + `sealed interface XxxEvent`.
   - `XxxPresenter.kt` – `@Composable fun XxxPresenter(initialState, events: Flow<XxxEvent>, deps): XxxState`. Events fold into state, side effects (persistence, IO) are `LaunchedEffect`s keyed on state.
   - `XxxViewModel.kt` – plain class, no base class: `states: StateFlow<XxxState>` from `moleculeFlow(Immediate) { … }.stateIn(scope, Lazily, initialState)`, `onXxx()` event methods, `close()`. The scope is private so it never leaks into the ObjC/NuGet export surface. Lazily shared: the presenter starts with the first collector, so every host must collect.
   - `XxxScreen.kt` – `XxxScreen(viewModel)` collects and delegates to a stateless `XxxView(state, callbacks)`. In `:cupboard:ui`, since it draws.

   Platform shells stay thin adapters (observation bridging, platform types) and own their navigation. Non-Compose hosts (SwiftUI and WinUI, the latter via kotlin-native-nuget in `winuiApp/`) collect `states` on their main dispatcher and poke their own observation primitive; no snapshot machinery crosses the boundary. Shells obtain their view models from `Cupboard.<screen>()` platform factory functions (plain factories plus constructor injection, no DI framework): construction of the graph lives there, not in the shells.

   Events carry intent-sized facts, one per completed user action. Continuous gestures stream transient preview events through the loop and commit one event on release, so one gesture is still one undo entry and one autosave write (see the editor's `PreviewSlide`/`UpdateSlide`/`CancelPreview`).

   Lessons from the drag-freeze bug (`9bc97ce`, resolved note in ROADMAP.md): anything a gesture shows must come back through `states`, never from local snapshot state written in pointer handlers (those writes can silently skip repaint on desktop). And when the loop looks broken, root-cause it; bypassing it only masks the symptom.
