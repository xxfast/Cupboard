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

/** Replaces the element with the same id. An id this slide doesn't hold changes nothing. */
fun Slide.updateElement(updated: Element): Slide =
    copy(elements = elements.map { if (it.id == updated.id) updated else it })

/** [updateElement] for a batch: every element in [updated] replaces its id, in one pass. */
fun Slide.updateElements(updated: List<Element>): Slide {
    if (updated.isEmpty()) return this
    val byId: Map<String, Element> = updated.associateBy { it.id }
    return copy(elements = elements.map { byId[it.id] ?: it })
}

/**
 * Wraps everything [ids] resolves to into one [GroupElement] with [groupId].
 *
 * The group lands at the z-position of its topmost member and the children keep
 * their relative z-order, so grouping never reshuffles what draws over what. Its
 * frame is the members' bounding box at group time.
 *
 * Locked members are left out: a group drags its children around with it, and
 * grouping a locked element would be a way to edit around the lock. Fewer than
 * two groupable elements is not a group, so it returns this same instance.
 */
fun Slide.groupElements(ids: List<String>, groupId: String = newId()): Slide {
    val wanted: Set<String> = ids.toSet()
    val members: List<Element> = elements.filter { it.id in wanted && !it.locked }
    if (members.size < 2) return this

    val memberIds: Set<String> = members.mapTo(mutableSetOf()) { it.id }
    val topIndex: Int = elements.indexOfLast { it.id in memberIds }
    // Where the group goes once the members are gone: everything that stays put
    // and drew below the topmost member is still below it.
    val insertAt: Int = elements.take(topIndex).count { it.id !in memberIds }
    val group = GroupElement(
        id = groupId,
        frame = boundingFrame(members.map { it.drawnBounds() }),
        children = members,
    )

    val rest: MutableList<Element> = elements.filterTo(mutableListOf()) { it.id !in memberIds }
    rest.add(insertAt, group)
    return copy(elements = rest)
}

/**
 * Splices the group's children back where the group sat, in their stored order,
 * with the transforms the group carried baked into each of them: its rotation
 * turns the child around the group's center and adds to the child's own, each of
 * its flips mirrors the child across that center and toggles the child's flag,
 * and its opacity multiplies through.
 *
 * A group flip does not negate a child's own rotation, so a rotated child in a
 * flipped group comes out mirrored about the wrong diagonal. Known
 * simplification, the same class as the group's unscaled text.
 *
 * An id that is not a group's, or not here at all, returns this same instance.
 */
fun Slide.ungroupElement(id: String): Slide {
    val index: Int = elements.indexOfFirst { it.id == id }
    if (index == -1) return this
    val group = elements[index] as? GroupElement ?: return this

    val bounds: Frame = group.frame
    val freed: List<Element> = group.children.map { child ->
        val mirroredX: Float =
            if (group.flippedHorizontally) 2 * bounds.centerX - child.frame.centerX
            else child.frame.centerX
        val mirroredY: Float =
            if (group.flippedVertically) 2 * bounds.centerY - child.frame.centerY
            else child.frame.centerY
        val (dx, dy) = rotateVector(
            mirroredX - bounds.centerX,
            mirroredY - bounds.centerY,
            group.rotation,
        )

        child.update(
            frame = child.frame.copy(
                x = bounds.centerX + dx - child.frame.width / 2,
                y = bounds.centerY + dy - child.frame.height / 2,
            ),
            opacity = child.opacity * group.opacity,
            rotation = child.rotation + group.rotation,
            flippedHorizontally = child.flippedHorizontally != group.flippedHorizontally,
            flippedVertically = child.flippedVertically != group.flippedVertically,
        )
    }

    return copy(elements = elements.take(index) + freed + elements.drop(index + 1))
}

/** Where a z-order move sends an element. [Slide.elements] is the z-order, last on top. */
enum class ZOrderMove { Forward, Backward, ToFront, ToBack }

/**
 * Moves the element with [id] through the z-order, clamped at both ends.
 *
 * A move that changes nothing returns this same instance, so a caller can tell
 * a real reorder from a no-op by identity and skip the history entry.
 */
fun Slide.reorderElement(id: String, move: ZOrderMove): Slide = reorderElements(listOf(id), move)

/**
 * [reorderElement] for a batch, clamped at both ends the same way.
 *
 * [ZOrderMove.ToFront] and [ZOrderMove.ToBack] move the members as a block, with
 * their relative order intact. [ZOrderMove.Forward] and [ZOrderMove.Backward]
 * step each member one slot, worked from the edge the move heads for so members
 * pile up against the end rather than leapfrogging each other.
 */
fun Slide.reorderElements(ids: List<String>, move: ZOrderMove): Slide {
    val wanted: Set<String> = ids.toSet()
    val members: List<Element> = elements.filter { it.id in wanted }
    if (members.isEmpty()) return this

    val reordered: List<Element> = when (move) {
        ZOrderMove.ToFront -> elements.filter { it.id !in wanted } + members
        ZOrderMove.ToBack -> members + elements.filter { it.id !in wanted }

        // Top down, tracking the lowest slot already spoken for: a member that
        // can't move blocks the next one instead of being jumped over.
        ZOrderMove.Forward -> elements.toMutableList().apply {
            var ceiling: Int = size
            for (index in indices.reversed()) {
                if (this[index].id !in wanted) continue
                if (index + 1 >= ceiling) {
                    ceiling = index
                    continue
                }
                add(index + 1, removeAt(index))
                ceiling = index + 1
            }
        }

        ZOrderMove.Backward -> elements.toMutableList().apply {
            var floor = -1
            for (index in indices) {
                if (this[index].id !in wanted) continue
                if (index - 1 <= floor) {
                    floor = index
                    continue
                }
                add(index - 1, removeAt(index))
                floor = index - 1
            }
        }
    }

    if (reordered == elements) return this
    return copy(elements = reordered)
}

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
