# winuiApp

The Windows shell: WinUI 3 chrome over the shared editor core, reached through a
NuGet package rather than a framework.

## What's here

- `build.gradle.kts` + `src/nativeMain/` — the Kotlin side. Links `:cupboard` (the
  UI-free core) into a native shared library and packs it as the `Cupboard.Kotlin`
  NuGet package via [kotlin-native-nuget](https://github.com/xxfast/kotlin-native-nuget).
  `WindowsApp.bootstrap(storageDirectory)` points the editor at its document file;
  `WinEditorViewModel` is the C#-facing wrapper over the shared `EditorViewModel`.
- `Shared/` — the managed adapter. Enumerates the Kotlin `StateFlow`, marshals it
  onto the UI `SynchronizationContext`, and projects it into `INotifyPropertyChanged`
  shapes for XAML.
- `WinUiApp/` — a minimal WinUI 3 window: the navigator outline bound to the
  adapter, undo/redo, and a placeholder where the canvas will go.

## Status

**The Kotlin half builds anywhere. The C# half needs Windows.**

`:cupboard:ui` (the Compose canvas) has no `mingwX64` variant, because no Compose
UI artifact publishes for it. So Windows gets the shared document model, editor
state and undo/redo, and renders its own chrome. The slide canvas is not ported
yet: `WinUiApp` shows a placeholder where it will live.

The C# projects are a skeleton to build on from a Windows machine, not something
verified here. They need Windows plus the WinUI 3 / Windows App SDK workload, and
have never been compiled.

## Building

Kotlin side, from the repo root, on any host:

```
./gradlew :winuiApp:linkDebugSharedMingwX64
./gradlew :winuiApp:packNuget     # -> winuiApp/build/nuget/Cupboard.Kotlin.0.1.0.nupkg
```

C# side, on Windows:

```
dotnet build winuiApp/WinUiApp/WinUiApp.csproj -r win-x64
```

`Directory.Build.props` adds `winuiApp/build/nuget` as a package source and
`Directory.Build.targets` runs `packNuget` before restore, so the C# build picks
up the Kotlin code without a separate step.
