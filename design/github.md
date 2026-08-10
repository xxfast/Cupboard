repo: xxfast/slides-kt
branch: main

## Last sync
date: 2026-08-10T14:47:16Z

### Updated in this project
- Re-checked main (tree e686502): still the bare Compose Multiplatform template — App.kt / Greeting.kt / Platform.kt + per-platform actuals, no editor UI or slide model
- Split the editor design into components (toolbar / navigator / canvas / inspector / build row) + shared platform-theme.js
- Collapsed the two editor versions into one file; handoff spec moved to HANDOFF.md

## Sync history
- 2026-08-09T15:43:40Z — re-checked repo (fresh KMP template); created design_handoff_slides_editor/ package (README spec + prototypes)
- 2026-08-09T05:44:18Z — explored repo (fresh template); designed main editor window as UI spec

## Screen map
| Screen | Repo files |
|---|---|
| Slides Editor.dc.html | (new design — no source UI; target: shared/src/commonMain/kotlin/io/github/xxfast/slides/) |
| EditorToolbar / Inspector .dc.html | (per-OS native chrome; target: platform source sets) |
| SlideNavigator / SlideCanvas / BuildOrderRow .dc.html | (target: shared/src/commonMain/…/slides/) |
