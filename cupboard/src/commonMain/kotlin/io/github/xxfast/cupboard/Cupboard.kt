package io.github.xxfast.cupboard

/**
 * The extension namespace for Cupboard's platform factory functions.
 *
 * Owns nothing, constructs nothing, holds no state: it exists to be typed. Type
 * `Cupboard.` in a shell and autocomplete lists every entry point reachable from
 * it, each with the signature that platform actually needs (a directory here, a
 * home-relative default there). That is why these are per-platform factories
 * rather than one `expect`/`actual`: the shapes genuinely differ.
 */
object Cupboard

/**
 * The one file every shell on a machine edits. Interim: the editor is a
 * single-document app until open/save lands, so the path is a constant, not a
 * user choice.
 */
internal const val DOCUMENT_FILE_NAME: String = "document.json"

/**
 * The user's saved themes, beside the document rather than in it: a theme is a
 * look you carry between decks, so it outlives whichever one it was saved from.
 */
internal const val THEME_LIBRARY_FILE_NAME: String = "themes.json"
