package io.github.xxfast.cupboard.document

/**
 * What changed under an open bundle, from outside the editor: another tool
 * rewriting the deck, a `git checkout`, a script regenerating the whole thing.
 *
 * Facts rather than paths, so the editor never learns the bundle's layout: a
 * watcher that knows it decodes the deck before it gets here, and whatever it
 * could not decode never arrives at all.
 */
sealed interface BundleChange {
    /**
     * `document.json` was rewritten and reads as [document]. Never emitted for a
     * file that fails `decodeDocument`: a half-written deck is the normal case in
     * the middle of a burst, and the write that finishes it brings its own.
     */
    data class Deck(val document: Document) : BundleChange

    /**
     * Something under `assets/` changed: a swapped image, a regenerated bundle's
     * bytes. Which one is not said, because the document only holds ids, and an
     * id whose bytes changed under it looks exactly like one whose didn't.
     */
    data object Assets : BundleChange
}
