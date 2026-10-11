# Roadmap

What we're building and in what order. See `GOALS.md` for the why, `design/README.md` for the UI spec. Detail on finished work lives in git history; this file stays forward-looking. Parity target: Keynote 15.3 for Mac (feature surface audited 2026-08 from the app's menu tree + the user guide), minus everything listed under Not planned.

Organised as releases, not phases. Current version is 0.1.0. Each set below is one tagged release and has to be shippable on its own: shared core first, then its UI on each shell in parallel (the `/ship` flow). 1.0.0 is the first stable release; anything that doesn't gate it sits under After 1.0.

## 0.1.0 (current)

Everything built so far, summarised. Commit refs point at the detail.

- Foundations: CuP as the presentation runtime behind our own interface, forked as submodules `cup/` + `emoji-kt/` (branch `ir/macos-targets`, composite-built) for `macosArm64`; upstream drafts KodeinKoders/CuP#12, kosi-libs/Emoji.kt#19. Compose runs natively on macOS via `ComposeNSView` (a real `NSTextInputClient`). Serializable document model, native 1920x1080 slide space, NYTimes-KMP-shaped MVI screens, bounded undo/redo, gestures committing once on release. CI mirrors the local verify battery on ubuntu, macos and windows (`f051f70`).
- Shells: macOS SwiftUI chrome per design v3/v4/v5 (glass panels, navigator rows, inspector tabs, full-bleed canvas; `dc8f842`, `4b4bb12`, `f386691`, `ef200af`), Linux/fallback Compose chrome in M3 with `ChromeTheme` tokens for all three OSes, WinUI chrome over the `Cupboard.Kotlin` NuGet with a text-row navigator and Fluent inspector (`6cec865`, `30bb632`, `771656c`). The Windows canvas is not embedded yet.
- Editing: element properties and rotation, multi-select, group/ungroup, align/distribute, app-internal clipboard, deletion, canvas + navigator context menus, slide management with Keynote-style nested drag, on-canvas text editing, text formatting, shape catalog, rulers/guides/snap, Keynote-parity inspectors (`docs/keynote/INSPECTOR.md`) and navigator (multi-select, keyboard, drag).
- Developer content: code element with six themes, in-place editing, code steps with reveal/highlight and diffing, Magic Move for code, step authoring, terminal, diagram (mermaid subset), equations (LaTeX subset), export as a CuP project.
- Layouts and themes: masters with placeholders, layout mode, eleven built-in themes plus Keynote's Basic/Classic masters, File > New theme chooser (`cb9a3a9`), slide size presets + custom, object styles, saved themes.
- Animation: slide transitions incl. Magic Move, builds in/out with deliveries, action builds, build order panel, play-mode fidelity test.
- Presenting: own presenter display, multi-display with swap, rehearse, playback types + links, in-show keyboard controls, Play menu.
- Media and documents: images with masks/instant alpha/adjustments, galleries, video/audio core only (`90f06d1`), `.cupboard` folder bundle, full document lifecycle with recents, export to PDF/PNG/GIF/HTML/PPTX/Print.

Almost none of it has had a visual pass. That's 0.6.0.

## 0.2.0: Finish what's half in

Shipped cores whose surfaces never landed, mostly parked on the bundle format, which exists now.

- [x] Video/audio surfaces: Insert menu + toolbar, Video/Audio inspector sections on both shells, the macOS AVPlayer overlay in play, a JVM playback answer (no codec on the classpath: poster + open-in-OS-player is the fallback). (`b344dbb`, needs a visual pass; playback has never been run. Not done: the overlay ignores rotation, opacity and transitions, rehearse plays nothing, big movies are read whole into memory.)
- [x] Image fills on shapes and image slide backgrounds. (`2a38e52`, needs a visual pass. PPTX and the CuP export fall back to the colour underneath.)
- [x] Real fonts: family picker over the fonts the OS has, embedded in the bundle so a deck renders the same on another machine; generic Sans/Serif/Monospace stay as the fallbacks. (`a909015`, needs a visual pass. Embedding is by Add Font File: installed fonts are not copied into the bundle on their own, for licensing and because the JVM can't find their files. Android always falls back.)
- [x] Diagram step authoring: the Steps section the Code inspector got (`8e6ddd7`), for `DiagramStep`. (`34e726a`, needs a visual pass)
- [x] Inline links: link ranges inside a text box, not just whole-box. (`6f26902`, needs a visual pass. Applying a link ends the edit session, and Cmd+K opens the inspector without focusing the URL field. PPTX and the CuP export drop range links. Found on the way: a box linked to a slide rather than a URL isn't underlined.)

## 0.3.0: Editing debts

Every "not yet" the parity items left behind.

- [ ] Inspector gaps: mixed-value display on multi-selections, vertical text align, paragraph spacing, shadow offset/angle/opacity, Keynote-style colour wells.
- [ ] Per-slide Title/Body appearance checkboxes do something.
- [x] Add a Slide picker: the toolbar "+" opens Keynote's grid of the deck's layouts (thumbnail + name, three across); picking one adds a slide on that layout after the selected one. In layout mode it adds a layout. `AddSlide` takes an optional layout id.
- [ ] Compose navigator catches up with the core's multi-select (ranges, toggles, batch verbs). WinUI's follows once its canvas lands.
- [x] Object List: Keynote's per-slide object panel (`View > Show Object List`). Front-to-back rows with group disclosure, select and multi-select, inline rename, lock, hide (ours, Keynote has no hide), search filter, the canvas context menu on rows, drag-to-reorder, and a group's child selectable on its own (`572eb86`, `5db5e83`, `20c5a16`).
- [x] Canvas zoom and pan, Keynote's: the 10 to 400% ladder behind `View > Zoom` and the pill (Zoom In/Out, Actual Size, Fit Slide, Zoom to Selection, Zoom to Fit Content, Keynote's shortcuts), scroll pans clamped to the slide with any fitting axis centred, menu zoom anchored on the view centre, pinch anchored on the pointer (macOS), the pill reading the computed percent at Fit. Rides the loop as `EditorState.viewport`.
- [ ] Code field honours the wrap setting while editing and places the caret at the click instead of selecting the whole snippet.
- [ ] Group resize with rotated children (open bug from `13ffcc6`).
- [x] Speaker notes editable in the strip, Keynote's way: click for a caret, Return for a new line, Escape to leave, long notes scroll, and the top edge drags to resize (99pt up to 45% of the window) or, let go at the bottom, to hide. One visit of the caret is one undo entry (`BeginNotesEdit`/`EditNotes`/`EndNotesEdit`), and the Edit verbs grey while it is there. Ours keeps the "SPEAKER NOTES" label and the strip's type; Keynote's notes are rich text, ours stay plain. (`6b1a07a`)
- [x] Document title popover, the title bar's: a click on the deck's name in the macOS toolbar opens Name, Tags and Where, and a right-click lists the folders it is in. Rename and Move To move the bundle itself (`Cupboard.move`), on both shells' File menus too. (`6b1a07a`, needs a visual pass. The move is a new editor, so undo history does not survive it. Where's Favourites are the stock folders, the Finder sidebar is not readable. Compose has the menu items only, its title is the OS's.)
- [x] Keynote's alignment guides on the canvas, surveyed in `docs/keynote/ALIGNMENT.md`: solid yellow guides across the slide while a drag sits on a centre or edge line, relative spacing (equal gaps snap and wear double-headed arrows) and relative sizing (a resize snaps to another object's width or height and marks every match), the position / size readout beside the pointer, a six screen-point snap distance at any zoom, and multi-selections snapping as one box. All of it derived in the loop (`EditorState.alignment` from each `PreviewElements` carrying a `DragSample`), the canvas only draws. Also wires rulers, guides and snapping into the macOS host, which had never passed them to the canvas. Needs a visual pass.
- [ ] Lock a deck from the title popover: the padlock beside Where. Needs a read-only editor first, with Keynote's Unlock / Duplicate prompt on the first edit.
- [ ] Edit menu items stay live mid-edit, Keynote-style, instead of greying while the field owns the chords.
- [ ] Single-file `.cupboard` (zip) for Linux and Windows, where a folder-as-document doesn't fly. macOS keeps the package.
- [x] Launch like Keynote on every shell: a `.cupboard` opened from the OS (Finder, `open`, argv) opens that deck alone, and a bare launch shows the theme chooser and nothing else. No more `~/.cupboard/Untitled.cupboard` opening on its own. (`18d7c52`, needs a visual pass. WinUI still opens its default bundle, it has no chooser yet.)
- [x] Watch the open bundle and reload when it changes on disk (#17): `document.json` edited by another tool, a `git checkout` or an agent regenerating the deck shows up without a reopen. Platform watcher behind `watchBundle` (`WatchService` on JVM, `DispatchSource` on Apple, `ReadDirectoryChangesW` on Windows), changes ride the loop as `EditorEvent.Reload` / `RefreshAssets`, own autosaves are ignored, external writes win over unsaved edits, undo history resets like a fresh open. (`0623e1c`, needs a visual pass. Windows watcher is compile-verified only. After an assets change the macOS navigator rows show placeholders until the canvas has decoded the new bytes, same as rows always did for undecoded images. Fonts swapped on disk are not re-read.)

## 0.4.0: Windows

The one platform without a canvas. Timeboxed.

- [ ] Embed the Compose canvas per `winuiApp/CANVAS.md` (child JVM process reparented via `SetParent`, JVM owns the one `EditorViewModel`, native lib becomes an IPC proxy). Keyboard focus is the known risk.
- [ ] If the embed hits its kill criteria: Windows ships 1.0 on the tokenized Compose shell with Fluent tokens and the WinUI chrome moves to After 1.0. Either way the Windows build gets a visual pass on a real Windows machine.
- [ ] Whichever shell ships: canvas + navigator context menus and navigator multi-select on it.

## 0.5.0: Release plumbing

First downloadable build. Everything after this is a public beta.

- [ ] One version source feeding `packageVersion`, `MARKETING_VERSION` and the NuGet (today `1.0.0` and `0.1.0` disagree).
- [ ] macOS: Developer ID signing + notarization in `package.sh`, Dmg.
- [ ] Linux Deb (AppImage or Flatpak if cheap), Windows Msi. `packageDistributionForCurrentOS` already does the packaging.
- [ ] Tag-triggered CI release job attaching the three artifacts to a GitHub release. `CHANGELOG.md` kept per release.
- [ ] Upstream hygiene: promote the fork PRs (KodeinKoders/CuP#12, kosi-libs/Emoji.kt#19) on Kotlin Slack `#cup-presentations`; file the minimal Compose Multiplatform repro for the drag-freeze (local snapshot write from a `pointerInput` handler on an idle window, see `9bc97ce`).
- [ ] README with a screenshot, install steps per OS, and the Keynote-vs-Cupboard pitch.

## 0.6.0: Hardening

The visual pass the 0.1.0 items never got, area by area, on each shell (macOS SwiftUI, Compose, Windows). Each one is: run it, compare against the design and against Keynote, fix what's wrong, tick it.

- [ ] Chrome: panels, toolbar, zoom pill, status bar, inspector tabs.
- [ ] Navigator: rows, nesting, drag, multi-select, context menu.
- [ ] Canvas editing: selection, handles, rotation, guides, snap, rulers, context menu, text/code in-place editing.
- [ ] Inspectors: every Format/Animate/Document section against `docs/keynote/INSPECTOR.md`.
- [ ] Developer elements: code steps, diffing, Magic Move, terminal, diagram, equation.
- [ ] Layouts and themes: layout mode, theme switch, theme chooser, slide size.
- [ ] Animation: transitions, builds, action builds, build order panel, canvas badges.
- [ ] Presenting: play, presenter display, multi-display on real dual-display hardware, rehearse, in-show controls, laser cursor hiding per shell (not reachable from shared code).
- [ ] Documents: lifecycle, recents, autosave, every export format opened in its target app.
- [ ] Sample deck and Feature Showcase deck look right end to end.
- [ ] Beta feedback triage: whatever 0.5.0 users report.

## 1.0.0

Ships when 0.2 to 0.6 are ticked and:

- [ ] A full talk can be authored, saved, reopened and presented on each OS without a workaround.
- [ ] No known data-loss or crash bugs; undo and autosave hold up under a feel-test session.
- [ ] Decks saved on one OS open on the other two.

## After 1.0

- Tables: rows/columns/headers/footers, merged cells, cell styling, sort, cell formats (number/currency/date/percent), conditional highlighting. Formula engine only if demand proves out (it's Numbers-in-Keynote; developers mostly paste results).
- Charts: 2D set (column/bar/line/area/pie/donut/scatter) with a data editor; interactive/animated data sets later; 3D never.
- Import: PPTX and Keynote best-effort (shapes/text/images land editable; unsupported effects degrade gracefully).
- Password-protected documents.
- JPEG and movie export (need an encoder / a codec).
- Action build motion paths (move is a straight line today).
- WinUI chrome, if 0.4.0 took the fallback.
- iPad app and Android tablet app (SwiftUI/Compose shells over the same canvas and view models).
- Web app (wasmJs shell; possibly the CuP web export doubling as a share/view surface first).
- Collaboration/sync (`● synced` in the design status bar): a backend and CRDT-shaped document work; comments/highlights ride the same infrastructure.

## Not planned

- All generative/AI features (image/shape generation, slide generation, auto notes, writing tools, super resolution, auto crop): explicitly cut.
- 3D objects (USDZ), live camera-on-slide video, multi-presenter shows, FaceTime/Messages integration, iPhone/Watch remotes, Apple Pencil/touch input: not our audience's stage setup; revisit only on demand.
- Keynote '09 compat, Box/iCloud storage integrations, Touch Bar.
