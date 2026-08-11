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

## Generated namespaces

kotlin-native-nuget writes one C# file, `contentFiles/cs/any/Interop.cs`, which
`Shared/` compiles in through the package reference. It maps Kotlin packages to
C# namespaces by stripping the `rootPackage` from `build.gradle.kts`
(`io.github.xxfast.cupboard`), prefixing the package id, and PascalCasing what is
left:

| Kotlin | C# |
| --- | --- |
| `io.github.xxfast.cupboard.*` | `Cupboard.Kotlin` |
| `io.github.xxfast.cupboard.winui.WinEditorViewModel` | `Cupboard.Kotlin.Winui.WinEditorViewModel` |
| `io.github.xxfast.cupboard.winui.WinEditorState` | `Cupboard.Kotlin.Winui.WinEditorState` |
| `io.github.xxfast.cupboard.winui.WinOutlineRow` | `Cupboard.Kotlin.Winui.WinOutlineRow` |
| `io.github.xxfast.cupboard.winui.WindowsApp` | `Cupboard.Kotlin.Winui.WindowsApp` |

`StateFlow<T>` projects as `KotlinStateFlow<T>`: a synchronous `.Value` plus async
enumeration. Every generated class is `IDisposable` over native memory, so copy
what you need out of a snapshot rather than holding it.

**Do not alias that namespace as `Kotlin`.** The adapter lives in
`Cupboard.Windows`, so C# name lookup finds the sibling namespace `Cupboard.Kotlin`
before it ever reaches a compilation-unit `using Kotlin = ...` alias, and the
alias silently loses. It fails as `CS0234: 'WinEditorState' does not exist in the
namespace 'Cupboard.Kotlin'`, which reads like a missing type but is a shadowing
bug. Use a name that is not a member of `Cupboard`:

```csharp
using KotlinApp = Cupboard.Kotlin.Winui;
```

To check the real names rather than guessing, pack and read the file:

```
./gradlew :winuiApp:packNuget
less winuiApp/build/nuget/Cupboard.Kotlin.0.1.0/contentFiles/cs/any/Interop.cs
```

## Status

**The Kotlin half builds anywhere. The C# half needs Windows.**

`:cupboard:ui` (the Compose canvas) has no `mingwX64` variant, because no Compose
UI artifact publishes for it. So Windows gets the shared document model, editor
state and undo/redo, and renders its own chrome. The slide canvas is not ported
yet: `WinUiApp` shows a placeholder where it will live.

`Shared/` does compile on macOS and Linux, which is the fast way to check the
adapter against the generated interop without waiting for CI:

```
./gradlew :winuiApp:packNuget
dotnet build winuiApp/Shared/Shared.csproj -p:SkipCupboardKotlinNuGetPack=true
```

`WinUiApp/` does not: its XAML compiler ships only as a Windows executable
(`XamlCompiler.exe`, .NET Framework), so markup errors surface on Windows or in
the `windows-app` CI job and nowhere else. Keep the bindings conservative.

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
