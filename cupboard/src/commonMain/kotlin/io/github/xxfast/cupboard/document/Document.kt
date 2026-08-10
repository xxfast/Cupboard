@file:OptIn(ExperimentalUuidApi::class)

package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun newId(): String = Uuid.random().toString()

/**
 * All geometry is in document units: points at 1x on the fixed 1920x1080 slide.
 * Colors are packed ARGB (0xAARRGGBB).
 *
 * Slides are a flat ordered list, Keynote-style: nesting is expressed with
 * [Slide.depth], and a slide with deeper slides beneath it can collapse them.
 * Numbering is always the absolute index + 1, collapse never renumbers.
 */
@Serializable
data class Document(
    val id: String = newId(),
    val name: String = "Untitled",
    val slideWidth: Float = SLIDE_WIDTH,
    val slideHeight: Float = SLIDE_HEIGHT,
    val slides: List<Slide> = emptyList(),
) {
    companion object {
        const val SLIDE_WIDTH: Float = 1920f
        const val SLIDE_HEIGHT: Float = 1080f
    }
}

@Serializable
data class Slide(
    val id: String = newId(),
    val title: String = "Untitled slide",
    val elements: List<Element> = emptyList(),
    val builds: List<Build> = emptyList(),
    val notes: String = "",
    val depth: Int = 0,
    val collapsed: Boolean = false,
)

/** Slides in presentation order. Kept for call-site symmetry with the old tree model. */
fun Document.allSlides(): List<Slide> = slides

fun Document.updateSlide(updated: Slide): Document =
    copy(slides = slides.map { if (it.id == updated.id) updated else it })

/** True when the next slide exists and sits deeper, i.e. this slide owns children. */
fun Document.hasChildren(index: Int): Boolean =
    slides.getOrNull(index + 1)?.let { it.depth > slides[index].depth } == true

/**
 * Indices visible under collapse rules: a collapsed slide hides the following
 * contiguous run of slides deeper than it.
 */
fun Document.visibleIndices(): List<Int> {
    val visible = mutableListOf<Int>()
    var hiddenBelow: Int? = null
    for ((index, slide) in slides.withIndex()) {
        val threshold = hiddenBelow
        if (threshold != null && slide.depth > threshold) continue
        hiddenBelow = null
        visible += index
        if (slide.collapsed) hiddenBelow = slide.depth
    }
    return visible
}

fun Document.toggleCollapsed(id: String): Document =
    copy(slides = slides.map { if (it.id == id) it.copy(collapsed = !it.collapsed) else it })
