# The canvas problem

Design/spike notes for embedding the shared Compose slide canvas into the WinUI 3
shell. Thinking document for the first working session on a Windows machine, not a
settled plan. Read `winuiApp/README.md` first for what already exists.

## Why this is the hard one

The product constraint (CLAUDE.md): chrome native per platform, canvas
pixel-identical shared Compose. macOS got this cheaply, because Compose UI and
skiko publish `macosArm64` artifacts, so `ComposeNSView` runs the canvas in the
same process as the SwiftUI chrome (`macosApp/src/macosMain/kotlin/canvas/`).

Windows has no such luxury. Skiko ships Windows support only as JVM artifacts
(`skiko-awt-runtime-windows-x64`); there is no `mingwX64` skiko or Compose UI, and
JetBrains have said they don't plan native desktop targets (YouTrack CMP-1923).
That's why `:cupboard` is split: the UI-free core carries `mingwX64` and feeds the
`Cupboard.Kotlin` NuGet, while `:cupboard:ui` (everything that touches a pixel)
does not exist for Windows native.

So on Windows the canvas can only run on a JVM (Compose for Desktop, AWT-hosted),
and the chrome consumes view models from a Kotlin/Native shared lib. Two separate
runtimes. Two problems, in order of hardness:

1. **State**: who owns the one true `EditorViewModel`, and how does the other
   runtime see it. Two instances over one `document.json` is a fork factory: two
   presenters, two undo stacks, two debounced autosaves clobbering one file.
2. **Pixels**: how a window rendered by an AWT/skiko JVM ends up visually inside
   a WinUI 3 window, with input, focus, DPI and z-order behaving.

## Embedding mechanics

Common ground for (a) and (b): WinUI 3 has no `HwndHost` equivalent. Hosting a
foreign HWND is an open community proposal (microsoft-ui-xaml #10050) with no
Microsoft answer; ContentIslands (`DesktopChildSiteBridge`) host framework-aware
`ContentIsland`s, not arbitrary HWNDs. The only route is Win32 interop: take the
`AppWindow` HWND, make the Compose window a `WS_CHILD` of it (`SetParent` does not
set the style; clear `WS_POPUP`, set `WS_CHILD` first), and position it over the
XAML placeholder rect on layout changes. XAML renders into its own swapchain, so
the child HWND always draws above all XAML in the main island (airspace). WinUI
popups/flyouts get their own top-level HWNDs, so menus should still appear over
the canvas; verify.

Getting the HWND from the JVM side is easy: `ComposeWindow` is a `JFrame`, JNA's
`Native.getWindowPointer(window)` returns it. Skiko renders via DirectX 12 with
OpenGL and software fallbacks (`skiko.renderApi`), all fine inside a child HWND.

### (a) In-process JVM (JNI invocation API)

C# P/Invokes `JNI_CreateJavaVM` from a bundled `jvm.dll`, boots a canvas entry
point, reparents its HWND into the WinUI window.

- Lifecycle: one JVM per process, ever; `DestroyJavaVM` can't be followed by a
  re-create. A canvas wedge means restarting the whole app.
- Crash isolation: none. A skiko/DirectX native crash takes the chrome with it.
  Three runtimes (CLR, JVM, K/N) share one address space, signal/exception
  handling and debugging all get worse.
- Input/DPI/z-order: identical to (b). Same-process reparenting doesn't help;
  AWT still owns its own thread's message pump, so it behaves like a foreign
  window either way.
- The supposed payoff, shared state, is fake: the K/N NuGet lib and the JVM are
  separate Kotlin runtimes with separate heaps even in one process. The bridge
  would be hand-rolled JNI marshaling instead of IPC. Worse, not better.

Verdict: all of (b)'s window problems plus process fragility. Only worth
revisiting if IPC latency measurably hurts, which at human rates it won't.

### (b) Child JVM process + `SetParent` (recommended to spike)

Chrome spawns the canvas as a child process (a jpackage app-image launcher, so
the JRE ships inside; `:desktopApp:packageDistributionForCurrentOS` already
builds one). Child reports its HWND over the IPC channel; chrome reparents it.

- Legality: cross-process `SetParent` is legal (the old "not supported" doc note
  is gone; Raymond Chen confirms, while comparing it to juggling chainsaws).
  Chromium's windowed NPAPI plugins and DAW bridges (jBridge, yabridge) shipped
  on exactly this. No published prior art for Compose-into-WinUI specifically;
  we would be first.
- Input: the real risk. Cross-process parent/child implicitly attaches the two
  threads' input queues (transitively), so focus and typing interleave, and a
  hung canvas process can hang the chrome's input too. #10050 reports people
  failing to get keyboard/pointer into hosted child HWNDs from WinUI 3; there is
  an undocumented workaround floating around (discussion #9912). Mouse into the
  child should just work (it's the window under the cursor); keyboard focus
  handoff between XAML controls and the canvas HWND is the part to prove.
- Accelerators: with focus on the canvas, Ctrl+Z lands in the JVM. Either the
  canvas handles it against the shared VM (it already does in the Compose shell)
  or forwards unhandled chords over IPC. Both fine; pick during the spike.
- DPI: `SetParent` across processes with mismatched DPI awareness force-resets
  the child process's awareness. Both sides should be Per-Monitor V2: WinUI 3 is,
  and the JVM launcher manifests PMv2 since JDK 11 (JDK-8199627). Should match;
  verify on a mixed-DPI setup because skiko's scale handling on reparent is
  untested territory.
- UIPI: both processes are ours at the same integrity level, so no message
  filtering, unless someone runs the chrome elevated. Document "don't".
- Crash isolation: good for crashes (chrome survives, relaunches the child,
  reparents a fresh HWND; state survives if the chrome owns it, or replays from
  disk if not). Poor for hangs, see input-queue attachment above.
- Z-order/airspace: canvas floats above XAML in the main window; the placeholder
  in `MainWindow.xaml` becomes a hole. Inspector/sidebar must be real XAML
  siblings positioned around it, never overlapping it. Flyouts are separate
  HWNDs, expected to work; transient XAML overlays over the canvas are off the
  table on this platform.

Also considered and parked: off-screen rendering (`ImageComposeScene` into a
shared-memory buffer, blitted into a `SwapChainPanel`). Kills every window
problem at once, but the headless scene has no IME or accessibility, needs a
hand-driven frame loop, and turns input into a full manual translation layer.
It's the direction Microsoft's own ContentIslands story would want, and worth a
second look only if `SetParent` fails on input.

### (c) Do nothing (the standing fallback)

`:desktopApp` (Compose for Desktop) already runs on Windows and ships meanwhile;
ROADMAP's fallback rule says a stalled native route ships the tokenized Compose
shell (Fluent tokens) without blocking other platforms. Zero risk, but concedes
the product constraint on Windows, and "wait for native skiko" is not a plan
(CMP-1923 says it isn't coming).

## The state bridge

The MVI shape makes this unusually IPC-friendly, and that's not luck, it's
constraint 6 paying out: `EditorState` is `@Serializable` by design, `EditorEvent`
is a sealed set of intent-sized facts (`SelectSlide(id)`, `UpdateSlide(slide)`,
`Undo`) over the `@Serializable` document model, and gestures commit once on
release, so no per-frame drag traffic ever needs to cross. Serialize events one
way, state snapshots the other, and the process boundary lands exactly on the
existing `states`-out/events-in seam. The protocol types can live in `:cupboard`
`commonMain`, compiled by both `jvm` and `mingwX64`.

1. **JVM owns the VM** (recommended): the canvas process holds the one true
   `EditorViewModel` plus the KStore. The K/N NuGet lib keeps the exact
   `WinEditorViewModel` surface but becomes a proxy: events serialize over IPC,
   state snapshots come back and feed the existing `StateFlow`. C# adapter code
   doesn't change at all. The canvas gets zero-latency state (it's in-process
   with the VM), which matters because every future high-frequency consumer is
   canvas-side: on-canvas text editing (Phase 2), thumbnails via
   `renderComposeScene`, play mode. Chrome consumes outline/selection/undo flags
   at human rates, where IPC latency is invisible.
2. **Native owns the VM, JVM renders**: closer than it sounds, because
   `:winuiApp` already runs the real presenter on K/N (molecule is in its deps;
   today's `WinEditorViewModel` is this, minus a canvas). Undo history would
   survive a canvas crash, and the NuGet stays "real". But it puts the process
   boundary on the wrong side of the chatty edge: every canvas intent
   (element select, slide update, each future text keystroke) round-trips before
   the canvas sees its own echo, and thumbnails/play still need the full
   document streamed over. Keep as the fallback bridge if (1)'s proxy fights
   kotlin-native-nuget somehow.
3. **Shared persistence + event log**: both sides own a VM, coordinate through
   the document file or an appended event log. Rejected: ephemeral state
   (selection, collapse) isn't persisted, file watchers are laggy and racy, and
   there are still two undo stacks. This is the fork factory formalized.

Transport: localhost TCP over loopback, not named pipes. A JVM pipe *server*
needs JNA/JNI; a K/N pipe client needs Win32 `CreateFile`. TCP is stdlib on the
JVM and winsock-via-`platform.windows` on K/N, port handed to the child via argv.
Frames: newline-delimited kotlinx-serialization JSON, same engine both ends.
Bootstrap: `WindowsApp.bootstrap(storageDirectory)` stops building a KStore and
instead records the path to pass to the child; the store moves canvas-side.

## Spike plan (first Windows session)

Smallest end-to-end proof first. Each step has a product on its own.

1. Compile the C# skeleton at all (`dotnet build winuiApp/WinUiApp/WinUiApp.csproj
   -r win-x64`). It has never been built. Fix the NuGet restore + interop fallout;
   outline binds, undo/redo works. This alone retires half the module's risk.
2. Naked reparent: launch the stock `:desktopApp` jpackage image as a child
   process, grab its HWND (JNA in a tiny patch, printed to stdout for now),
   `WS_CHILD` + `SetParent` into the WinUI window, `MoveWindow` over the
   placeholder rect. Success: Compose pixels inside WinUI chrome, resizing with
   the window. No IPC yet, wrong content, doesn't matter.
3. Input gauntlet: click/drag elements on the embedded canvas; type; Tab and
   click between a XAML `TextBox` and the canvas and back; open a flyout over it.
   This step is where the approach lives or dies (#10050 says keyboard focus is
   the killer). Timebox: two days of fighting, then invoke kill criteria.
4. DPI gauntlet: 100% and 150%/175% monitors, drag between them, change scale
   live. Watch for the forced-awareness-reset and skiko scale artifacts.
5. Lifecycle: HWND handshake over the socket instead of stdout; child crash test
   (`kill`), relaunch + reparent; clean shutdown ordering (unparent before
   `DestroyWindow` teardown so AWT doesn't die confused).
6. State bridge: trim the child to a canvas-only window (new JVM entry point,
   canvas + `EditorViewModel` + KStore, no chrome), turn the K/N
   `WinEditorViewModel` into the pipe proxy. Success: click a slide in the WinUI
   navigator, canvas changes; drag an element on the canvas, outline title and
   undo flags update in XAML. That's the whole architecture proven.
7. Polish list for later: accelerator routing, IME in the child, window
   snapping/min-size, canvas placeholder letterboxing.

Kill criteria (any one triggers shipping (c), the tokenized Compose shell, per
the ROADMAP fallback rule; the NuGet/view-model work is kept either way):

- Step 3: keyboard focus between XAML and the canvas can't be made reliable
  inside the timebox, including the #9912 workaround.
- Step 4: DPI transitions wedge skiko or reset awareness with no stable config.
- Step 5: input-queue attachment hangs the chrome in ways a watchdog can't mask.
- Any repeated need for undocumented Windows APIs on the hot path.

## Effort/risk

| Option | Effort | Risk | Notes |
| --- | --- | --- | --- |
| (a) in-proc JVM | High | High | Same window problems as (b), no crash isolation, one-JVM-ever, JNI marshaling. No payoff over (b). |
| (b) child process + SetParent | Medium | Medium-high | Steps 2+5+6 are days each; step 3 is the coin flip. Prior art exists, none for this exact stack. |
| (c) Compose Desktop fallback | Zero (exists) | Zero | Concedes native chrome on Windows. Ships today. |
| Bridge 1 (JVM owns) | Low-medium | Low | Proxy behind an unchanged NuGet surface; protocol is the existing MVI seam. |
| Bridge 2 (native owns) | Medium | Medium | Round-trips on the chatty edge; already half-built though. |
| Bridge 3 (shared log) | Medium | High | Two undo stacks, racy. Rejected. |

Recommendation: spike (b) with bridge 1. Fall back to (c) on kill criteria, and
keep bridge 2 in the back pocket since the K/N presenter already runs.

## Sources

- Skiko supported targets: https://github.com/JetBrains/skiko
- Skiko Windows JVM artifact: https://mvnrepository.com/artifact/org.jetbrains.skiko/skiko-awt-runtime-windows-x64
- No native desktop plans (JetBrains): https://youtrack.jetbrains.com/projects/CMP/issues/CMP-1923
- `SetParent` (styles, UISTATE, DPI table incl. cross-proc forced reset): https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setparent
- Raymond Chen on cross-process parent/child + input queue attachment: https://devblogs.microsoft.com/oldnewthing/20130412-00/?p=4683
- WinUI 3 `HwndHost` proposal, "input doesn't work" reports: https://github.com/microsoft/microsoft-ui-xaml/issues/10050
- Embedded-HWND input workaround discussion: https://github.com/microsoft/microsoft-ui-xaml/discussions/9912
- ContentIsland (framework content, not foreign HWNDs): https://learn.microsoft.com/en-us/windows/windows-app-sdk/api/winrt/microsoft.ui.content.contentisland
- WinUI 3 Win32 interop (AppWindow HWND): https://learn.microsoft.com/en-us/windows/apps/winui/winui3/desktop-winui3-app-with-basic-interop
- JNI invocation API (one VM per process): https://docs.oracle.com/en/java/javase/21/docs/specs/jni/invocation.html
- .NET-hosts-JVM prior art (dated): https://github.com/jni4net/jni4net
- `ComposeWindow` is a `JFrame`: https://github.com/JetBrains/compose-multiplatform-core/blob/5289f46b9c74db8bbf59968141e3bb98197ef8a9/compose/ui/ui/src/desktopMain/kotlin/androidx/compose/desktop/ComposeWindow.desktop.kt
- JNA `Native.getWindowPointer`: https://java-native-access.github.io/jna/5.13.0/javadoc/com/sun/jna/Native.html
- Skiko DirectX 12 + fallbacks: https://blog.jetbrains.com/kotlin/2021/06/compose-for-desktop-milestone-4-released/
- `ImageComposeScene` limitations: https://github.com/JetBrains/compose-multiplatform/issues/4788
- Mixed-mode DPI, child follows parent, hosting behavior: https://learn.microsoft.com/en-us/windows/win32/hidpi/high-dpi-improvements-for-desktop-applications
- JVM Per-Monitor V2 manifest since JDK 11: https://bugs.openjdk.org/browse/JDK-8199627
- Chromium windowed-plugin reparenting (prior art): https://www.chromium.org/developers/design-documents/plugin-architecture/
- yabridge (cross-process plugin GUI hosting): https://github.com/robbert-vdh/yabridge
- UIPI message filtering: https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-changewindowmessagefilter
