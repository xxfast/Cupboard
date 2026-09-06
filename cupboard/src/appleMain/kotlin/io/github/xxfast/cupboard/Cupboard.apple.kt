package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.document.CupboardBundle
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.io.files.Path
import platform.Foundation.NSHomeDirectory

/**
 * An editor over `~/.cupboard/Untitled.cupboard`.
 *
 * Deliberately the same bundle the Compose Desktop shell opens: the two shells
 * are front ends onto one deck on this machine. Apple hosts have a real main
 * loop, so [dispatcher] defaults to it.
 */
fun Cupboard.editor(dispatcher: CoroutineDispatcher = Dispatchers.Main): EditorViewModel =
    editor(CupboardBundle.default(Path(NSHomeDirectory(), ".cupboard")), dispatcher)

/**
 * [openDocument] on the main dispatcher, which is the only one a SwiftUI host
 * has. The dispatcher-taking version is there for the hosts with no main loop of
 * their own; Swift cannot name a `CoroutineDispatcher` anyway.
 */
fun Cupboard.openDocument(path: String): OpenResult = openDocument(path, Dispatchers.Main)

/** [saveAs] on the main dispatcher. [openDocument]'s reasoning. */
fun Cupboard.saveAs(viewModel: EditorViewModel, path: String): EditorViewModel =
    saveAs(viewModel, path, Dispatchers.Main)
