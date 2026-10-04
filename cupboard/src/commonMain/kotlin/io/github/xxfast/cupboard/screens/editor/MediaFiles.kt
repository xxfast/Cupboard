package io.github.xxfast.cupboard.screens.editor

/**
 * The image files an insert accepts, lowercase and without the dot: what a file
 * dialog filters on and what a drop is sifted for.
 */
val ImageExtensions: List<String> = listOf("png", "jpg", "jpeg", "gif", "webp")

/**
 * The movie files an insert accepts, [ImageExtensions]' twin. Which of them a
 * host can actually play is its player's business: the deck keeps the bytes
 * either way, and a movie this machine cannot decode still plays on one that can.
 */
val MovieExtensions: List<String> = listOf("mp4", "mov", "m4v", "webm", "mkv", "avi")

/** The sound files an insert accepts, for [MovieExtensions]' reason. */
val SoundExtensions: List<String> = listOf("mp3", "m4a", "aac", "wav", "aiff", "aif", "flac", "ogg")

/** What a picked, dropped or pasted file inserts as. See [mediaKind]. */
enum class MediaKind { Image, Movie, Sound }

/**
 * What a file with [extension] inserts as, by its name alone, or null for a file
 * no insert takes.
 *
 * Here rather than in a shell because every one of them sifts the same drop: a
 * Finder selection of a screenshot, a screen recording and a voice memo lands as
 * an image, a movie and a sound whichever window it was dropped on. Case and a
 * leading dot are forgiven, since the platforms disagree on both.
 */
fun mediaKind(extension: String): MediaKind? = when (extension.removePrefix(".").lowercase()) {
    in ImageExtensions -> MediaKind.Image
    in MovieExtensions -> MediaKind.Movie
    in SoundExtensions -> MediaKind.Sound
    else -> null
}
