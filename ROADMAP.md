# Roadmap

What we're building and in what order. See `GOALS.md` for the why, `design/README.md` for the UI spec. Detail on finished work lives in git history; this file stays forward-looking. Parity target: Keynote 15.3 for Mac (feature surface audited 2026-08 from the app's menu tree + the user guide), minus everything listed under Not planned.

## Phase 0: Scaffolding

- [x] CuP adopted as the presentation runtime behind our own interface (play mode, steps/builds; swappable). Forked as submodules `cup/` + `emoji-kt/` (branch `ir/macos-targets` in each, composite-built) to add `macosArm64`. Upstream drafts: KodeinKoders/CuP#12, kosi-libs/Emoji.kt#19.
- [x] Compose runs natively on macOS without a JVM; `ComposeNSView` embeds the shared canvas in SwiftUI via `NSViewRepresentable`. Known edge: no IME on native text input (picked up in Phase 2).
- [x] Serializable document model: flat Keynote-style nesting (depth + collapse, absolute numbering), native 1920x1080 slide space, rendered by pure common composables. Editor canvas with selection, 8 resize handles, guides + snap, zoom (Fit / 25-200%), screen-space overlays; pure-render thumbnails; syntax-highlighted code elements (`dev.snipme:highlights`, every target, no JS bridge).
- [x] Screen architecture per CLAUDE.md constraint 6: NYTimes-KMP-shaped MVI in `screens/<name>/`, thin shell adapters, navigation per platform. Gestures commit once on release. Undo/redo: bounded document history in the presenter, Cmd+Z in both shells' menus.
- [x] Interim persistence: KStore autosaves `~/.cupboard/document.json` (debounced, both shells share the file) until the `.cupboard` bundle format exists.
- [x] Template app modules removed; `:cupboard` keeps its android/ios/web targets because iPad, Android tablet, and web apps are planned later.
- [x] macOS shell scaffolding: `macosApp/Cupboard.xcodeproj` (Gradle framework phase + compose-resources staging), `.app` packaging, IDE run config through the KMP plugin. Terminal launches show the window without a dock click: run.sh goes through LaunchServices with logs on the tty (`624e9e5`; the in-app activate alone, `6f8afba`, wasn't honored).
- [x] Play mode wired in both shells. Needs a visual pass.
- [x] Build CI via GitHub Actions: jobs mirroring the local verify battery (`:cupboard:jvmTest` + js/wasmJs compiles + `:desktopApp:compileKotlin` on ubuntu; `xcodebuild` + the 5s smoke run on macos). Recursive submodule checkout for the forks; no aggregate `./gradlew build` (known-broken by design). (`f051f70`, first run green incl. the smoke test on the CI VM)
- [ ] Follow-through: promote the upstream fork PRs (KodeinKoders/CuP#12, kosi-libs/Emoji.kt#19), coordinate on Kotlin Slack `#cup-presentations`.

## Phase 1: App shells (all desktop platforms, in parallel)

macOS, Windows, and Linux are built side by side; there is no dedicated desktop client. Every later phase lands shared core first, then its UI on each shell in parallel (the `/ship` flow).

- [x] macOS chrome per design v3's layered window: full-bleed canvas with translucent glass panels floating over it (sidebar 212px with traffic lights in its header, inspector 282px, toolbar 52px spanning the gap); no status bar on macOS. Live zoom pill (Fit + 25-200%); insert clusters and inspector body are placeholders until their phases. (`dc8f842`, needs a visual pass)
- [x] macOS navigator rows per the Keynote 26 spec (design v4): floating card, capsule selection hugging number + thumbnail (no ring), disclosure strip under the parent row, 16px/level indent. (`4b4bb12`, needs a visual pass)
- [x] macOS full-bleed canvas embed via `ComposeNSView` (window content layer at origin 0,0; retires the skiko Metal-layer offset band). (`f386691`, needs a visual pass)
- [x] Navigator rows per design v5's leading-gutter chevron: fixed gutter on every row (chevron vertically centred against the thumbnail, number tucked in the same gutter), 12px/level indent + 12px thumbnail step-down (clamped at three levels), stroked 9x9 chevron rotating 90deg over 140ms. macOS SwiftUI + Compose fallback; WinUI stays text rows until the canvas port. Collapse animates the rows too, via `fullOutline()`'s visible flag. (`ef200af`, `7975c58`, needs a visual pass)
- [ ] Inspector chrome on every shell: macOS landed per design v4 (Format/Animate/Document tabs live in the toolbar over the inspector glass, active tab closes the panel, Text/Build/Slide placeholder bodies incl. the static Document panel; `4b4bb12`, needs a visual pass). Still owed: M3 tabs on Compose and Fluent on WinUI (three tabs there too: Format/Animate/Slide); panels fill in as their features land in later phases.
- [x] Linux chrome in Material 3 per the design: navigator, toolbar, inspector, speaker notes strip, status bar; OS styling tokenized (`ChromeTheme` ports all of `design/platform-theme.js`, 3 OS x dark/light) so this shell doubles as the universal fallback. Zoom pill live like the mac shell's; insert buttons and inspector bodies are placeholders until their phases. (needs a visual pass)
- [ ] Windows: embed the Compose canvas per `winuiApp/CANVAS.md` (child JVM process reparented via `SetParent`, JVM owns the one `EditorViewModel`, native lib becomes an IPC proxy; keyboard-focus risk timeboxed, Compose fallback on kill criteria). Chrome parity already landed (`6cec865`, `30bb632`): view models shared via the `Cupboard.Kotlin` NuGet, MenuBar/navigator/status bar in Fluent, CI's windows job blocking and green. Needs a visual pass on a Windows machine.
- [ ] Fallback rule: if a native route stalls, that platform ships the tokenized Compose shell instead (mac or Fluent tokens) without blocking the others.

## Phase 2: Editing foundations (shared)

The parity core every later phase builds on. All of it is `screens/editor/` events + reductions first, shell surfaces second.

- [x] Element property events: position/size (numeric entry), opacity, z-order (forward/back/front/back), lock/unlock, flip/rotate. Rotated elements hit-test and resize where they're drawn (anchor-corner-fixed math in `Geometry.kt`); live Format panels in both shells, Arrange menu on desktop. (`2943c50`, needs a visual pass)
- [x] Multi-select: marquee + shift-click (toggles at press time), group/ungroup (`GroupElement`, absolute-coordinate children, transforms baked on ungroup), align/distribute, batch property edits in both shells. (`13ffcc6`, needs a visual pass; known open bug: group resize misbehaves with rotated children, seen on visual review)
- [x] Clipboard: cut/copy/paste/duplicate for elements and slides, paste style / copy style. App-internal (presenter-local like undo, not the OS pasteboard); cut+paste keeps position, repeat pastes cascade; collapsed slides travel with their hidden run. (`23ed292`) Edit-menu verbs follow keyboard focus per Keynote: `focusedPane` rides the loop, generic Cut/Copy/Duplicate/Delete re-dispatch to slide or elements, so Cmd+X/C/V works in the navigator too (`b02edd3`).
- [x] Deletion: elements and slides, Clear All; undo/redo re-anchor the slide selection by index when the restored document loses it (the selection-dangle revisit). (`a3a482c`)
- [x] Canvas context menu: cut/copy/paste/delete, then z-order, group/ungroup, lock, align/distribute. `ContextClick` through the loop (outside the selection selects, inside keeps it, empty space clears); canvas forwards point + hit only. `NSMenu` on macOS with enablement computed one step ahead of the roundtrip; Compose renders the shared menu specs natively via AWT on macOS/Windows (`ea62459`) and keeps an m3 dropdown at the pointer on Linux, where M3 is the designed chrome. Arrange items factored once per shell, shared by menu bar and context menu. WinUI waits for its canvas embed. (`def9f0c`; `9b28924` fixed the menu's eaten rightMouseUp sticking the scene's secondary button, caught and confirmed fixed on visual review)
- [x] Navigator context menu: new/duplicate on the clicked row, then cut/copy/paste, then delete; every action carries the row's id (race-free by construction; on macOS the row is not pre-selected, `.contextMenu` has no click hook and the actions settle selection in the core). Pulled AddSlide forward from slide management; "after this slide" insertion (add/duplicate/paste) lands past the row's deeper run so a parent is never split from its children. Native per shell (SwiftUI `.contextMenu` on macOS rows, AWT popup on desktop macOS/Windows, m3 on Linux); slide verbs factored once per shell, shared with the menu-bar Slide menu, which gained New Slide. (`0a956eb`)
- [x] Slide management: add/duplicate/delete/reorder (navigator drag), skip slide, slide numbers, per-slide background (color/gradient). Add landed early with the navigator context menu (`0a956eb`); the rest in `1d857d1`: `moveSlide` moves the slide with its whole nested run, collapsed or not, Keynote-style (the first cut moved an expanded parent alone and outdented its children, which tore the hierarchy apart); dropping onto a row's body nests under it, its edge quarters name the gaps; the drag is Keynote's: the row travels with the pointer and rows animate into place on drop (Compose navigator is a keyed `LazyColumn` + `animateItem`, SwiftUI offsets the row and wraps the drop in `withAnimation`), drag rides the loop on Compose (transient `slideDrag`, the marquee pattern) and native DragGesture on SwiftUI, skip renumbers presentation-style and play walks past skipped slides, inspector Slide panel gets the Slide Number checkbox + Default/Color/Gradient swatches. Image backgrounds wait for Phase 8's bundle format alongside ImageElement content. Needs a visual pass.
- [x] On-canvas text editing: double-click puts the caret in a text element (legacy `BasicTextField` over the canvas overlay, one undo entry per edit session, Edit verbs grey out while editing), and `ComposeNSView` is a real `NSTextInputClient` so dead keys and CJK sources reach the field on native macOS. (`a2bcc59`, needs a visual pass)
- [x] Text formatting: font family (generic Sans/Serif/Monospace until fonts ship with the bundle format), size/weight, bold/italic/underline/strikethrough, colour, alignment, line spacing, lists (bullet/numbered, nested by leading tabs, Tab/Shift+Tab in the field), links (whole-box for now, inline ranges later). Live Text section in both Format inspectors, Format menu with Cmd+B/I/U on both shells. (`9417d91`, needs a visual pass)
- [x] Shape catalog: the basic dozen (rect, rounded, oval, triangle, arrow, diamond, star, hexagon, quote bubble, callout), lines with arrowheads, fills (solid/gradient; image fills wait for Phase 8's bundle format), borders, shadow, corner radius. Brought the first `InsertElement` event with it: toolbar Text/Shape buttons and an Insert menu are live on both shells, Shape section in both Format inspectors. (`2b4bba0`, needs a visual pass)
- [ ] Rulers + user guides (draggable), layout guides, snap settings.

## Phase 3: Developer content (the differentiator)

Where we beat Keynote for our audience; worth shipping before broad parity.

- [ ] Code element parity+: language picker, theme choice (tokenized to app theme), line numbers, font size/wrap controls.
- [ ] Code steps: per-build line/range highlighting and progressive reveal (CuP's `cup-source-code` model, but document-owned and native-safe).
- [ ] Code diffing between steps (Magic Move for code: matched lines animate, added/removed lines fade/slide).
- [ ] Terminal/output element: monospace block styled as a terminal with prompt/output styling, typewriter build.
- [ ] Diagram element: text-defined diagrams (mermaid-class syntax) rendered to the canvas; steps reveal nodes/edges.
- [ ] Equations: LaTeX subset rendered natively (KaTeX-class layout in Kotlin, or precomputed at edit time; no webview).
- [ ] Export a deck as a CuP Kotlin project (the document model was designed for this).

## Phase 4: Layouts and themes

- [ ] Slide layouts (masters): layout editing view, text/media placeholders, apply/reapply layout, layout inheritance (layout edits propagate; layout objects background-locked on slides).
- [ ] Themes: save-as-theme, change theme, theme-defined defaults for new elements; ships with a small set of developer-taste themes (dark-first).
- [ ] Document setup: slide size presets (16:9, 4:3) + custom dimensions; the 1920x1080 native space becomes per-document.
- [ ] Object styles: save/apply fill+border+shadow combos; default text box appearance.

## Phase 5: Animation

- [ ] Transition catalog per slide: dissolve/push/move-in/wipe + a Magic Move analog (matched elements animate between slides); duration, on-click vs auto with delay.
- [ ] Builds in/out per element: appear/dissolve/move/scale/wipe; text delivery by paragraph/word/character; code delivery by line (meets Phase 3's code steps).
- [ ] Action builds: move along path, opacity, rotate, scale; chainable.
- [ ] Build order panel: cross-element reordering, timing modes (on click / with / after previous, per-build delay); this is the Animate inspector's content.
- [ ] Play-mode fidelity: the CuP adapter (or its successor) honors all of the above; transition/build settings become part of the document model.

## Phase 6: Presenting

- [ ] Presenter display: current + next slide, notes, elapsed timer, clock; customizable layout; notes editable mid-show. (CuP's JVM speaker window can seed this on Compose shells.)
- [ ] Multi-display: slideshow on one display, presenter display on another, swap live.
- [ ] Rehearse mode (presenter display without an external display).
- [ ] Playback types: normal, self-playing (auto-advance, loop, restart after idle), links-only (kiosk); slide/element links (go to slide, next/previous, URLs).
- [ ] In-show controls: keyboard navigation, number+enter jump, slide switcher overlay, shortcut overlay ("?"), pointer show/hide.

## Phase 7: Media and data

- [ ] Images: insert (file/drag/paste), non-destructive masking (rect + shape), instant-alpha background removal, adjust panel (exposure/saturation/contrast), captions.
- [ ] Image galleries (carousel object, per-image captions, cycled during a show).
- [ ] Video/audio: embedded playback, trim, poster frame, loop, volume; web video embeds.
- [ ] Tables: rows/columns/headers/footers, merged cells, cell styling, sort; cell formats (number/currency/date/percent); conditional highlighting. Formula engine only if demand proves out (it's Numbers-in-Keynote; developers mostly paste results).
- [ ] Charts: 2D set (column/bar/line/area/pie/donut/scatter) with a data editor; interactive/animated data sets later; 3D never.

## Phase 8: Documents and interop

- [ ] `.cupboard` bundle format: folder/zip with document JSON + assets; replaces the interim single-JSON KStore file; document versioning for forward compat.
- [ ] Real document lifecycle: open/save/save-as, recents, multiple windows/documents, dirty state, file association.
- [ ] Export: PDF (with per-build pages option), PNG/JPEG per slide, movie of a played deck, animated GIF, HTML player; PPTX export (best-effort mapping); print with grid/handout layouts.
- [ ] Import: PPTX and Keynote best-effort (shapes/text/images land editable; unsupported effects degrade gracefully).
- [ ] Password-protected documents.

## Phase 9: More form factors

- [ ] iPad app (SwiftUI shell over the same canvas and view models) and Android tablet app.
- [ ] Web app (wasmJs shell; possibly the CuP web export doubling as a share/view surface first).

## Open questions

- ~~The drag-freeze bug~~ Resolved: bisected to `9bc97ce`, and the vsync suspicion was wrong. Snapshot writes to canvas-local state from pointer handlers intermittently never reach the recomposer (input, frame clock and draw all traced healthy while frozen); state that roundtrips through the view model always renders. Fixed by streaming transient `PreviewSlide` events through the loop, one `UpdateSlide` on release (undo/autosave boundary), canvas trusts the roundtrip again and `6ea6a03`'s write-behind is gone. Still owed: a minimal repro for an upstream Compose Multiplatform issue (local snapshot write from a `pointerInput` handler on an idle window).

- Collaboration/sync (`● synced` in the design status bar): real-time co-editing implies a backend and CRDT-shaped document work; comments/highlights ride the same infrastructure. Out of scope until the editor core is done.
- Speaker-notes authoring UX: read-only strips shipped in both shells per the design (`4b4bb12` mac, `86d6a74` desktop); still open whether notes are *edited* in the strip or a panel, and whether editing lands with Phase 6's presenter display or earlier.

## Not planned

- All generative/AI features (image/shape generation, slide generation, auto notes, writing tools, super resolution, auto crop): explicitly cut.
- 3D objects (USDZ), live camera-on-slide video, multi-presenter shows, FaceTime/Messages integration, iPhone/Watch remotes, Apple Pencil/touch input: not our audience's stage setup; revisit only on demand.
- Keynote '09 compat, Box/iCloud storage integrations, Touch Bar.
