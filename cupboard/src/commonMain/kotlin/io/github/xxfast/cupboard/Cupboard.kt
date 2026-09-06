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
 * The deck inside a `.cupboard` bundle. See `CupboardBundle` for the layout
 * around it. Which bundle a shell opens is still a constant rather than a user
 * choice until open/save-as lands.
 */
internal const val DOCUMENT_FILE_NAME: String = "document.json"

/**
 * The user's saved themes, beside the bundle rather than inside it: a theme is a
 * look you carry between decks, so it outlives whichever one it was saved from,
 * and it must not travel when a bundle is mailed to someone else.
 */
internal const val THEME_LIBRARY_FILE_NAME: String = "themes.json"
