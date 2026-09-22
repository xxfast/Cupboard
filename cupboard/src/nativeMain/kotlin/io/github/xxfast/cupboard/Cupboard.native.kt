package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.document.CupboardBundle
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import platform.posix.getenv

/**
 * Cupboard's own folder on this machine: the recents list and the theme library,
 * and the default deck until the user saves one somewhere of their own.
 *
 * `HOME` on the unixes, `USERPROFILE` on Windows, both read the same way because
 * this source set is macOS and mingw at once. The sandboxed macOS shell gets its
 * container from `HOME` exactly as `NSHomeDirectory` would.
 */
@OptIn(ExperimentalForeignApi::class)
fun cupboardDirectory(): Path {
    val home: String = getenv("HOME")?.toKString()
        ?: getenv("USERPROFILE")?.toKString()
        ?: "."
    return Path(home, ".cupboard")
}

/**
 * An editor over the `.cupboard` bundle at [bundle], creating it if missing.
 *
 * A pre-bundle `document.json` in the folder above moves in on the way past,
 * see [CupboardBundle.migrate]. Opening remembers the bundle, so it is in the
 * list [recentDocuments] hands back next time.
 *
 * [dispatcher] is the host's serialized main dispatcher; never Unconfined, see
 * [EditorViewModel]'s scope note. Kotlin/Native hosts that have no main loop of
 * their own (the .NET one) pass a single-parallelism worker instead.
 *
 * [directory] is where the recents list and the theme library go: the folder the
 * bundle sits in by default, which is this app's own folder for every shell that
 * has not been pointed somewhere else. Deck files never travel with them.
 *
 * Blocking is right here: there is no editor to show until the document loads.
 */
fun Cupboard.editor(
    bundle: Path,
    dispatcher: CoroutineDispatcher,
    directory: Path = bundle.parent ?: cupboardDirectory(),
): EditorViewModel = runBlocking { openEditor(bundle, directory, dispatcher) }

/**
 * An editor over the deck at [path], or why it could not be opened. What File >
 * Open comes down to, and what a double-clicked `.cupboard` does.
 *
 * Paths cross as strings on this side: the ObjC and .NET hosts have their own
 * path types and no business seeing kotlinx-io's. See [OpenResult] for what
 * comes back.
 */
fun Cupboard.openDocument(path: String, dispatcher: CoroutineDispatcher): OpenResult =
    runBlocking { openBundle(Path(path), cupboardDirectory(), dispatcher) }

/**
 * [viewModel]'s deck copied into a new bundle at [path], and an editor on it.
 *
 * The returned view model is a new one: the host shows it and closes the one it
 * passed in. `.cupboard` is appended if the save panel handed back a path
 * without it.
 */
fun Cupboard.saveAs(
    viewModel: EditorViewModel,
    path: String,
    dispatcher: CoroutineDispatcher,
): EditorViewModel =
    runBlocking { saveBundleAs(viewModel, Path(path), cupboardDirectory(), dispatcher) }

/**
 * A brand new bundle, named after the first [name] nothing else answers to, and
 * where it went. [directory] is this app's own folder when null. Open it with
 * [openDocument] like any other deck.
 */
fun Cupboard.newDocument(directory: String? = null, name: String = "Untitled"): String =
    createBundle(directory?.let { Path(it) } ?: cupboardDirectory(), name).toString()

/**
 * The decks this machine opened last, newest first, at most ten, and only the
 * ones still on disk. What a File > Open Recent menu renders, and what
 * [openDocument] takes straight back.
 */
/**
 * A fresh bundle holding the feature showcase deck, and where it went: Help >
 * Open Feature Showcase. Opened with [openDocument] like any other deck.
 */
fun Cupboard.showcase(directory: String? = null): String =
    createShowcaseBundle(directory?.let { Path(it) } ?: cupboardDirectory()).toString()

fun Cupboard.recentDocuments(): List<String> =
    runBlocking { recentsIn(cupboardDirectory()).list() }

/** Takes [path] out of the recents list, for the menu's Clear. */
fun Cupboard.forgetRecent(path: String) {
    runBlocking { recentsIn(cupboardDirectory()).forget(Path(path)) }
}
