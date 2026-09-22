package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.document.CupboardBundle
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path

/**
 * Cupboard's own folder on this machine: the recents list and the theme library,
 * and the default deck until the user saves one somewhere of their own.
 *
 * Never a deck's folder. Both of those files are the person's rather than the
 * document's, so they stay out of any bundle that might get mailed on.
 */
fun cupboardDirectory(): Path = Path(System.getProperty("user.home"), ".cupboard")

/**
 * An editor over a `.cupboard` bundle, creating it if missing, defaulting to
 * `~/.cupboard/Untitled.cupboard` so this shell and the macOS one open on the
 * same deck.
 *
 * A pre-bundle `~/.cupboard/document.json` moves into it on the way past, see
 * [CupboardBundle.migrate]. Opening remembers the bundle, so it is in the
 * recents list the shell shows next time.
 *
 * [dispatcher] is the host's serialized main dispatcher; never Unconfined, see
 * [EditorViewModel]'s scope note.
 *
 * [directory] is where the recents list and theme library go, the folder the
 * bundle sits in by default. A shell opening decks the user picked passes
 * [cupboardDirectory] instead, which is what [openDocument] defaults to: files
 * belonging to the app do not follow the deck around the filesystem.
 *
 * Blocking is right here: there is no editor to show until the document loads.
 */
fun Cupboard.editor(
    bundle: Path = CupboardBundle.default(cupboardDirectory()),
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    directory: Path = bundle.parent ?: cupboardDirectory(),
): EditorViewModel = runBlocking { openEditor(bundle, directory, dispatcher) }

/**
 * An editor over the deck at [bundle], or why it could not be opened. What File
 * > Open comes down to, and what a double-clicked `.cupboard` does.
 *
 * See [OpenResult]: a deck this build cannot read comes back as a sentence to
 * show rather than as a half-loaded document.
 */
fun Cupboard.openDocument(
    bundle: Path,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    directory: Path = cupboardDirectory(),
): OpenResult = runBlocking { openBundle(bundle, directory, dispatcher) }

/**
 * A brand new bundle in [directory], named after the first [name] nothing else
 * answers to, and where it went. Open it with [openDocument] like any other.
 */
fun Cupboard.newDocument(directory: Path = cupboardDirectory(), name: String = "Untitled"): Path =
    createBundle(directory, name)

/**
 * A fresh bundle holding the feature showcase deck, and where it went: Help >
 * Open Feature Showcase. Opened with [openDocument] like any other deck, which
 * is why this hands back a path rather than an editor.
 */
fun Cupboard.showcase(directory: Path = cupboardDirectory()): Path =
    createShowcaseBundle(directory)

/**
 * [viewModel]'s deck copied into a new bundle at [target], and an editor on it.
 *
 * The returned view model is a new one: the caller shows it and closes the one
 * it passed in. `.cupboard` is appended to [target] if the save dialog handed
 * one back without it.
 */
fun Cupboard.saveAs(
    viewModel: EditorViewModel,
    target: Path,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    directory: Path = cupboardDirectory(),
): EditorViewModel = runBlocking { saveBundleAs(viewModel, target, directory, dispatcher) }

/**
 * The decks this machine opened last, newest first, at most ten, and only the
 * ones still on disk. Paths as strings, which is what a File > Open Recent menu
 * renders and what [openDocument] takes back through `Path(...)`.
 */
fun Cupboard.recentDocuments(directory: Path = cupboardDirectory()): List<String> =
    runBlocking { recentsIn(directory).list() }

/** Takes [path] out of the recents list, for the menu's Clear. */
fun Cupboard.forgetRecent(path: Path, directory: Path = cupboardDirectory()) {
    runBlocking { recentsIn(directory).forget(path) }
}
