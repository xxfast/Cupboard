---
name: ship
description: Deliver one ROADMAP.md item end to end in the Cupboard repo: inspect the design spec, lock the shared design in the main context, delegate the common Kotlin core (MVI screen layer in :cupboard) to a subagent, split shell UI work into parallel SwiftUI and Compose subagents, review + commit + tick the roadmap, and hand the user a visual-verification checklist. Use whenever the user says /ship, names a roadmap item to build ("let's do the navigator rows", "work on the inspector next"), or asks what to work on and then picks an item. Also use for roadmap-shaped features not yet in ROADMAP.md; add the item first.
---

# Ship a roadmap item

One item in, one delivery out: design inspected, shared core landed with tests, shells wired, commits made, roadmap ticked, and a checklist of what only human eyes can verify. The main context does the thinking and reviewing; subagents do the typing. Keep the main context free of file dumps.

## 1. Understand the item

- Take the item from the arguments, or if ambiguous, list the unticked `- [ ]` lines from ROADMAP.md and let the user pick. If the request is roadmap-shaped but not listed, add it to the right phase first (standing grant covers roadmap upkeep).
- Read the item's line *and its phase header*; phase notes carry constraints (dependency chains, "needs a visual pass" annotations).
- Read the design spec for this item: `design/HANDOFF.md` for measurements and behavior, `design/README.md` for the token/theme source, mockups under `design/mockups/` when judging visual intent. Never edit anything under `design/`.
- Re-read CLAUDE.md constraints. Constraint 1 (target-clean canvas) and constraint 6 (MVI screen anatomy) shape almost every design decision here.

## 2. Lock the design in the main context

Do not delegate ambiguity; sharpen first, then delegate the mechanical part.

- Decide the shared surface per constraint 6: which `EditorEvent`s (or a new screen's events) exist, what the reductions do, what enters the serializable state vs stays presenter-local vs stays view-local. Events carry intent-sized facts: continuous gestures keep in-flight state in the composable and commit once on release.
- Decide the split: what lands in `:cupboard` commonMain, what each shell needs. Some items are single-shell (the layered glass window is SwiftUI-only); skip phantom work rather than inventing parity.
- Genuine scope decisions the user must own (deleting things, product behavior with no convention, trade-offs that change the roadmap) go to the user via AskUserQuestion. Everything with a conventional answer: pick it, state it, proceed.
- Write down the verify battery for each stage before launching anything (see step 5).

## 3. Delegate the common core

One subagent (`subagent_type: "claude"`, `model: "opus"`), background. The brief must be self-contained; the subagent has no memory of the conversation. Include, in this order:

1. Goal in one sentence, repo root path.
2. Files to read first (absolute paths): the four-file screen anatomy under `cupboard/src/commonMain/kotlin/io/github/xxfast/cupboard/screens/editor/` is both the likely edit target and the pattern to mirror for new screens. Point at reference files, don't re-describe patterns in prose.
3. Files to edit, explicitly listed. Locked design decisions stated as decisions, not options; where two implementations are acceptable, name the preference and the fallback.
4. Watch-outs, always including: no commits; no em-dashes anywhere; do not touch `cup/`, `emoji-kt/`, `design/`, the `.xcodeproj`, or `ComposeNSView.kt` unless the item is about them; commonMain stays target-clean; `./gradlew build` is known-broken, use per-target tasks.
5. The core verify battery (step 5) and a report format: deviations, surprises, verify results, under ~250 words.

Tests are part of the core, not an afterthought: new reductions get commonTest coverage mirroring `EditorViewModelTest`/`EditorUndoRedoTest` style; persistence-touching work extends the jvmTest autosave coverage.

## 4. Split the shells

After the core lands and is reviewed, launch the shell agents **in parallel, in one message** (both `model: "opus"`), unless the item is single-shell. They must not edit the same files; the core files are frozen input by now.

- **Compose shell**: `EditorScreen`/`EditorView` in `:cupboard` plus `desktopApp/` wiring (menus, windows, keyboard). Verify: `./gradlew :desktopApp:compileKotlin` and `:cupboard:jvmTest` if shared composables changed.
- **SwiftUI shell**: `macosApp/src/macosMain/kotlin/canvas/EditorHost.kt` (adapter: NSView hosting, NSImage thumbnails, ObjC-friendly types, no state ownership) plus `macosApp/Cupboard/HostApp.swift`. Verify: the xcodebuild command in step 5 plus the 5-second smoke run.

The SwiftUI adapter surface stays Kotlin-coroutine-free and CuP-free: NSViews, closures, primitives. Anything fancier will not survive the ObjC (or later .NET) boundary.

## 5. Verify batteries

Core (after step 3):
```
./gradlew :cupboard:jvmTest
./gradlew :cupboard:wasmJsMainClasses :cupboard:jsMainClasses
./gradlew :desktopApp:compileKotlin
```
Shells (after step 4, per shell):
```
./gradlew :desktopApp:compileKotlin
xcodebuild -project macosApp/Cupboard.xcodeproj -scheme Cupboard -configuration Debug -derivedDataPath macosApp/build/DerivedData build
# then launch the built .app binary, confirm alive after 5s, kill
```
A subagent that hits a wall reports it rather than working around it; if a report smells off, spot-check the diff in the main context before accepting.

## 6. Review, commit, tick

- Read the subagent summaries, then spot-check the load-bearing diffs yourself (`git diff <file>` on the presenter/adapter, not the whole tree). Trust but verify; the summaries are not user-visible, relay what matters in your own words.
- Commit per roadmap item (standing grant in this repo): one commit for the delivery, then a small follow-up commit ticking ROADMAP.md with the sha. Body in the user's voice: short, plain, no em-dashes, session trailers per the harness rules.
- Never push. Never rewrite history.

## 7. Human verification checklist

End the flow with a checklist of what only eyes can confirm, with exact commands:

- What to run (`./gradlew :desktopApp:run`, `./macosApp/run.sh`, or the IDE `Cupboard` run config) and what to look at, item by item.
- Call out any behavior that *changed feel* (gesture timing, focus, animation) even when all tests pass; those are the regressions tests cannot see.
- If the user is away, the checklist accumulates; keep it in the final message, not scattered mid-flow.

Anything the user rejects on visual review comes back through step 2, not as an unplanned hotfix: re-lock the decision, then re-delegate the delta with SendMessage to the still-warm agent when possible.
