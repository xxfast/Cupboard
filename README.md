# Cupboard

[![Stability](https://kotl.in/badges/experimental.svg)](https://kotlinlang.org/docs/components-stability.html#stability-of-subcomponents)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.10-blue.svg?logo=kotlin)](http://kotlinlang.org)

A Keynote-style presentation editor for developers who present code. Cross-platform desktop: macOS, Windows, Linux.

Each platform renders its own chrome with its native UI toolkit (SwiftUI on macOS, WinUI 3 on Windows, Compose Desktop Material on Linux). The slide canvas in the middle is one shared Compose Multiplatform surface, so slides render the same everywhere. Slides live in a serializable document model, and play mode (steps, builds, speaker window, export) is driven by [CuP](https://github.com/KodeinKoders/CuP).

> [!WARNING]
> 🚧 Early work in progress!

## Layout

- [`cupboard/`](./cupboard) is the KMP library where the bulk of the app lives: document model, slide renderers, editor canvas
- [`desktopApp/`](./desktopApp) is the Compose for Desktop shell, which is the Linux app and the fallback shell everywhere else
- [`macosApp/`](./macosApp) builds `CupboardCanvas.framework` for the SwiftUI host in [`macosApp/swift-host`](./macosApp/swift-host)
- [`cup/`](https://github.com/xxfast/CuP) and [`emoji-kt/`](https://github.com/xxfast/Emoji.kt) are fork submodules of CuP and Emoji.kt
- [`design/`](./design) holds the HTML design prototypes and the UI spec
- `androidApp/`, `webApp/`, `iosApp/` are template leftovers, fate undecided

See [`GOALS.md`](./GOALS.md) for what we're building and why, [`ROADMAP.md`](./ROADMAP.md) for the order we're building it in.

## Running

Clone with submodules: `git clone --recursive`, or `git submodule update --init` after a plain clone.

- Desktop, hot reload: `./gradlew :desktopApp:hotRun --auto`
- macOS SwiftUI host: `./macosApp/swift-host/run.sh`
- Tests: `./gradlew :cupboard:allTests`, or `:cupboard:jvmTest` for the quick loop
