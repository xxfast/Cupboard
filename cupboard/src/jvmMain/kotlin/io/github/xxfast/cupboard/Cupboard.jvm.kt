package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.document.BuiltInThemes
import io.github.xxfast.cupboard.document.CupboardBundle
import io.github.xxfast.cupboard.document.SlideSizePreset
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.screens.chooser.ThemeChooserState
import io.github.xxfast.cupboard.screens.chooser.ThemeChooserViewModel
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
 * An editor over the `.cupboard` bundle at [bundle], creating it if missing.
 *
 * Nothing opens by default any more: a shell launched bare shows the new-deck
 * chooser, and one launched on a file opens that file through [openDocument].
 * This is for a bundle the shell already owns (the WinUI route, tests), not a
 * way to pick a deck for the person.
 *
 * A pre-bundle `document.json` in the folder above moves in on the way past,
 * see [CupboardBundle.migrate]. Opening remembers the bundle, so it is in the
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
    bundle: Path,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    directory: Path = bundle.parent ?: cupboardDirectory(),
): EditorViewModel = runBlocking { openEditor(bundle, directory, dispatcher) }

/**
 * The new-deck chooser, File > New's theme sheet. Its last state's
 * [ThemeChooserState.selectedTheme] and [ThemeChooserState.size] are what
 * [newDocument] takes on Create.
 *
 * [dispatcher] is the host's serialized main dispatcher, as for [editor].
 */
fun Cupboard.themeChooser(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    initialState: ThemeChooserState = ThemeChooserState(),
): ThemeChooserViewModel = ThemeChooserViewModel(initialState, dispatcher)

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
 *
 * [theme] and [size] are what the new-deck chooser picked, see [themeChooser].
 */
fun Cupboard.newDocument(
    directory: Path = cupboardDirectory(),
    name: String = "Untitled",
    theme: Theme = BuiltInThemes.Cupboard,
    size: SlideSizePreset = SlideSizePreset.Widescreen,
): Path = createBundle(directory, name, theme, size)

/**
 * The feature showcase deck's bundle, made on the first ask and the same one
 * after, edits and all: Help > Feature Showcase > Open. Opened with
 * [openDocument] like any other deck, which is why this hands back a path rather
 * than an editor.
 */
fun Cupboard.showcase(directory: Path = cupboardDirectory()): Path =
    showcaseBundle(directory)

/**
 * [showcase] back to how it shipped, and where it is: Help > Feature Showcase >
 * Reset. Close any editor on it first, or its autosave writes the old deck back.
 */
fun Cupboard.resetShowcase(directory: Path = cupboardDirectory()): Path =
    resetShowcaseBundle(directory)

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
 * [viewModel]'s bundle moved to [target], which is Rename when only the name
 * differs and Move To when only the folder does. `.cupboard` is appended if
 * [target] came without it.
 *
 * See [MoveResult]: the editor that comes back replaces the one passed in,
 * which is already closed, and a move that could not happen is a sentence to
 * show with nothing changed.
 */
fun Cupboard.move(
    viewModel: EditorViewModel,
    target: Path,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    directory: Path = cupboardDirectory(),
): MoveResult = runBlocking { moveBundle(viewModel, target, directory, dispatcher) }

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
