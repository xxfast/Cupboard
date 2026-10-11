package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers

/**
 * [openDocument] on the main dispatcher, which is the only one a SwiftUI host
 * has. The dispatcher-taking version is there for the hosts with no main loop of
 * their own; Swift cannot name a `CoroutineDispatcher` anyway.
 *
 * The immediate flavour: a call already on the main thread runs the loop
 * through to `states` without queueing, which on macOS otherwise costs a frame
 * per hop (the renderer's blocks sit between).
 *
 * There is no default deck: a shell launched bare shows the new-deck chooser,
 * and everything it opens after that comes through here.
 */
fun Cupboard.openDocument(path: String): OpenResult = openDocument(path, Dispatchers.Main.immediate)

/** [saveAs] on the main dispatcher. [openDocument]'s reasoning. */
fun Cupboard.saveAs(viewModel: EditorViewModel, path: String): EditorViewModel =
    saveAs(viewModel, path, Dispatchers.Main.immediate)

/** [move] on the main dispatcher. [openDocument]'s reasoning. */
fun Cupboard.move(viewModel: EditorViewModel, path: String): MoveResult =
    move(viewModel, path, Dispatchers.Main.immediate)
