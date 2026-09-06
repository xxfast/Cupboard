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
