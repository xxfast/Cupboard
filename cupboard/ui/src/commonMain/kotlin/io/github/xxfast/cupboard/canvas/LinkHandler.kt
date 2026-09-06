package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.compositionLocalOf
import io.github.xxfast.cupboard.document.LinkTarget

/**
 * What a link on a slide does when it is clicked. Nothing, unless something above
 * the slide provides one: the player does, and the editor deliberately doesn't.
 *
 * A composition local rather than a parameter because the click happens several
 * layers below whatever can answer it, and every layer in between draws a slide
 * without caring whether the show it is in has anywhere to jump to.
 */
val LocalLinkHandler = compositionLocalOf<(LinkTarget) -> Unit> { {} }
