package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.io.files.Path
import platform.Foundation.NSHomeDirectory

/**
 * An editor over `~/.cupboard/document.json`.
 *
 * Deliberately the same path the Compose Desktop shell uses: the two shells are
 * front ends onto one document on this machine. Apple hosts have a real main
 * loop, so [dispatcher] defaults to it.
 */
fun Cupboard.editor(dispatcher: CoroutineDispatcher = Dispatchers.Main): EditorViewModel =
    editor(Path(NSHomeDirectory(), ".cupboard"), dispatcher)
