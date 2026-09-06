@file:OptIn(ExperimentalUuidApi::class)

package io.github.xxfast.cupboard.document

import kotlinx.serialization.SerialName
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
 * Collapse never renumbers; skipping does, per [presentationNumbers].
 */
@Serializable
data class Document(
    val id: String = newId(),
    val name: String = "Untitled",
    val slideWidth: Float = SLIDE_WIDTH,
    val slideHeight: Float = SLIDE_HEIGHT,
    val slides: List<Slide> = emptyList(),
    /**
     * The user's own guides, shared by every slide the way Keynote's are: a
     * guide is a property of the deck, not of the slide it was pulled out on.
     */
    val guides: List<Guide> = emptyList(),
) {
    companion object {
        const val SLIDE_WIDTH: Float = 1920f
        const val SLIDE_HEIGHT: Float = 1080f
    }
}

/**
 * Which way a line runs, and so which coordinate goes with it: [Vertical] is a
 * vertical line at an x, [Horizontal] a horizontal line at a y.
 *
 * Not `editor.Axis`, which answers a different question: that one is the axis a
 * distribute spreads along, where Horizontal means "across x".
 */
@Serializable
enum class GuideAxis { Vertical, Horizontal }

/** One user guide, in document units: an x for a vertical one, a y for a horizontal one. */
@Serializable
data class Guide(
    val id: String = newId(),
    val axis: GuideAxis,
    val position: Float,
)

/** Adds [guide], or moves the one already under its id. */
fun Document.putGuide(guide: Guide): Document =
    if (guides.none { it.id == guide.id }) copy(guides = guides + guide)
    else copy(guides = guides.map { if (it.id == guide.id) guide else it })

/**
 * Drops the guide with [id]. An id this document doesn't hold returns this same
 * instance, so a caller can skip the history entry the way [reorderElements]
 * lets it.
 */
fun Document.removeGuide(id: String): Document {
    if (guides.none { it.id == id }) return this
    return copy(guides = guides.filterNot { it.id == id })
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
    /**
     * Kept in the deck but left out of the presentation: play walks past it and
     * the numbering closes up over it, per [Document.presentationNumbers].
     */
    val skipped: Boolean = false,
    /** Whether the slide draws its own presentation number, Keynote's per-slide switch. */
    val showsSlideNumber: Boolean = false,
    /** What the slide paints behind its elements; null is the app's dark gradient. */
    val background: SlideBackground? = null,
)

/**
 * A slide's own background, when it wants one other than the deck's default.
 *
 * Colors are packed ARGB like everywhere else. There is no image variant yet:
 * an image background is bytes on the side, and bytes on the side arrive with
 * the bundle format, the same wait [ImageElement] is in.
 */
@Serializable
sealed interface SlideBackground {
    @Serializable
    @SerialName("color")
    data class Color(val color: Long) : SlideBackground

    /**
     * Two stops along a line at [angle], in CSS degrees: 0 points up and the
     * angle turns clockwise, so the deck's own gradient is 140.
     */
    @Serializable
    @SerialName("gradient")
    data class Gradient(
        val start: Long,
        val end: Long,
        val angle: Float = 140f,
    ) : SlideBackground
}

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
 * Every id this element answers for: its own, plus its children's all the way
 * down when it is a group. Builds key off element ids at any depth, so removing
 * a group has to know the whole subtree that goes with it.
 */
private fun Element.subtreeIds(): List<String> =
    listOf(id) + ((this as? GroupElement)?.children?.flatMap { it.subtreeIds() } ?: emptyList())

/**
 * The same element under a fresh id, its children renamed too all the way down.
 *
 * What copy, paste and duplicate are made of: everything else about the element
 * is kept verbatim, so a pasted copy draws exactly like the one it came from
 * until something moves it. [renamed] collects old id to new id for a caller
 * that has to follow the renaming, [Slide.duplicated] being the one that does.
 */
fun Element.withNewIds(renamed: MutableMap<String, String> = mutableMapOf()): Element {
    val fresh: String = newId()
    renamed[id] = fresh
    return when (this) {
        is GroupElement -> copy(id = fresh, children = children.map { it.withNewIds(renamed) })
        is TextElement -> copy(id = fresh)
        is ShapeElement -> copy(id = fresh)
        is ImageElement -> copy(id = fresh)
        is CodeElement -> copy(id = fresh)
        is TerminalElement -> copy(id = fresh)
        is DiagramElement -> copy(id = fresh)
        is EquationElement -> copy(id = fresh)
    }
}

/**
 * The same slide under fresh ids: its own, and every element's all the way down.
 *
 * Builds follow the renaming rather than being dropped, so a duplicated slide
 * animates like the one it came from. A build whose element is no longer there
 * stays dropped, the same way [removeElements] leaves it.
 */
fun Slide.duplicated(): Slide {
    val renamed: MutableMap<String, String> = mutableMapOf()
    val copies: List<Element> = elements.map { it.withNewIds(renamed) }
    return copy(
        id = newId(),
        elements = copies,
        builds = builds.mapNotNull { build ->
            renamed[build.elementId]?.let { build.copy(elementId = it) }
        },
    )
}

/** Appends [added] on top of the z-order, which is where new elements land. */
fun Slide.addElements(added: List<Element>): Slide = copy(elements = elements + added)

/**
 * Drops the top-level elements [ids] resolves to, and with them every build that
 * pointed at one or at anything nested inside one: a build left behind would
 * hold a step open for an element that is no longer there to reveal.
 *
 * Ids this slide doesn't hold change nothing and return this same instance, so a
 * caller can tell a real deletion from a no-op by identity and skip the history
 * entry, the same way [reorderElements] does.
 */
fun Slide.removeElements(ids: Set<String>): Slide {
    val removed: List<Element> = elements.filter { it.id in ids }
    if (removed.isEmpty()) return this

    val gone: Set<String> = removed.flatMapTo(mutableSetOf()) { it.subtreeIds() }
    return copy(
        elements = elements.filter { it.id !in gone },
        builds = builds.filter { it.elementId !in gone },
    )
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

/**
 * The slide now sitting at [index], or the last one when the index has fallen
 * off the end. Null only for a document with no slides at all.
 *
 * What a selection anchored to an index resolves to once slides have moved
 * under it: deleting a slide, and undoing or redoing a deletion, both leave a
 * selection pointing at a gap rather than at a slide.
 */
fun Document.slideAt(index: Int): Slide? = slides.getOrNull(index) ?: slides.lastOrNull()

/**
 * One past the run of slides deeper than the one at [index]: what a collapsed
 * slide hides, and what an expanded one lets out when it goes.
 *
 * On the bare list rather than the document, because a move has to ask it of the
 * list the moved slides have already been lifted out of.
 */
private fun List<Slide>.runEndAfter(index: Int): Int {
    val depth: Int = this[index].depth
    return (index + 1..lastIndex).firstOrNull { this[it].depth <= depth } ?: size
}

/**
 * Where a slide inserted "after [id]" lands: past the deeper run that follows
 * [id], not straight after it. A same-depth slide dropped between a parent and
 * its children would take those children for itself, so adding, duplicating and
 * pasting all insert here. -1 for an id this document doesn't hold.
 */
fun Document.insertionIndexAfter(id: String): Int {
    val index: Int = slides.indexOfFirst { it.id == id }
    return if (index == -1) -1 else slides.runEndAfter(index)
}

/**
 * The slide with [id] and whatever travels with it: a collapsed slide comes with
 * the run it hides, anything else comes on its own. Empty for an id this document
 * doesn't hold.
 *
 * The unit the navigator treats as one row, so it is also the unit copy, cut and
 * duplicate work in: a group that goes as a whole has to come back as a whole.
 */
fun Document.slideGroup(id: String): List<Slide> {
    val index: Int = slides.indexOfFirst { it.id == id }
    if (index == -1) return emptyList()

    val slide: Slide = slides[index]
    return if (slide.collapsed) slides.subList(index, slides.runEndAfter(index)).toList()
    else listOf(slide)
}

/**
 * Removes the slide with [id], and deals with whatever sat beneath it.
 *
 * A collapsed slide goes with the run of deeper slides it was hiding: they are
 * inside it as far as the navigator is concerned, and deleting a row you can see
 * must never quietly leave rows you couldn't behind. An expanded one gives that
 * run up instead, each slide in it moving one level out but never shallower than
 * the deleted slide was, so the run stays where the eye left it.
 *
 * The document is never emptied: deleting the last slide leaves one fresh blank
 * one, because a deck with nothing in it has nowhere to draw and no row to
 * select. An id this document doesn't hold returns this same instance.
 */
fun Document.removeSlide(id: String): Document {
    val index: Int = slides.indexOfFirst { it.id == id }
    if (index == -1) return this
    return copy(slides = slides.withoutUnitAt(index).ifEmpty { listOf(Slide()) })
}

/**
 * The navigator row at [index] lifted out of the list: a collapsed slide comes
 * away with the run it was hiding, an expanded one leaves that run behind one
 * level out but never shallower than the slide itself was.
 *
 */
private fun List<Slide>.withoutUnitAt(index: Int): List<Slide> {
    val lifted: Slide = this[index]
    val runEnd: Int = runEndAfter(index)
    return if (lifted.collapsed) take(index) + drop(runEnd)
    else take(index) +
        subList(index + 1, runEnd).map { it.copy(depth = maxOf(lifted.depth, it.depth - 1)) } +
        drop(runEnd)
}

/**
 * Moves the row [id] names into the gap under [afterId], null meaning the gap
 * above the first row.
 *
 * The moved unit is the slide and every deeper slide beneath it, collapsed or
 * not: a parent is a group in the navigator, and dragging a group anywhere must
 * land the group, the way Keynote does. Gaps sit between visible rows, so the
 * landing index is the end of [afterId]'s own row unit rather than the slot
 * right after it.
 *
 * Depth comes from where it lands: the anchor's, except in the gap between a
 * parent and its first child, where the child's depth wins and the drop joins the
 * run rather than splitting it. The rest of the unit keeps its distance from its
 * first slide, so a group lands as the group it was.
 *
 * [nest] is a drop onto [afterId]'s row rather than the gap under it: the unit
 * becomes that slide's first child, and a collapsed anchor opens so the drop is
 * not swallowed out of sight. Meaningless without an anchor, so ignored for null.
 *
 * A drop onto the unit's own body, an id this document doesn't hold, and a move
 * that puts every slide back where it was all return this same instance, so a
 * caller can skip the history entry the way [reorderElements] lets it.
 */
fun Document.moveSlide(id: String, afterId: String?, nest: Boolean = false): Document {
    val index: Int = slides.indexOfFirst { it.id == id }
    if (index == -1) return this

    val runEnd: Int = slides.runEndAfter(index)
    val unit: List<Slide> = slides.subList(index, runEnd).toList()
    val remaining: List<Slide> = slides.take(index) + slides.drop(runEnd)

    val at: Int
    val depth: Int
    var landing: List<Slide> = remaining
    if (afterId == null) {
        at = 0
        depth = 0
    } else {
        val anchorIndex: Int = remaining.indexOfFirst { it.id == afterId }
        // The anchor went with the unit, i.e. the row was dropped on itself.
        if (anchorIndex == -1) return this
        val anchor: Slide = remaining[anchorIndex]
        if (nest) {
            at = anchorIndex + 1
            depth = anchor.depth + 1
            if (anchor.collapsed) {
                landing = remaining.toMutableList().apply { this[anchorIndex] = anchor.copy(collapsed = false) }
            }
        } else {
            at = if (anchor.collapsed) remaining.runEndAfter(anchorIndex) else anchorIndex + 1
            val below: Slide? = remaining.getOrNull(at)
            depth = if (below != null && below.depth > anchor.depth) below.depth else anchor.depth
        }
    }

    val base: Int = unit.first().depth
    val rebased: List<Slide> = unit.map { it.copy(depth = depth + it.depth - base) }
    val moved: List<Slide> = landing.take(at) + rebased + landing.drop(at)

    if (moved == slides) return this
    return copy(slides = moved)
}

/**
 * Each slide's position in the presentation, parallel to [slides]: 1-based over
 * the slides that will actually be shown, null for a skipped one.
 *
 * Skipping renumbers, the way Keynote's does: the numbers a viewer sees have to
 * count what a viewer gets. Collapsing never renumbers, because it hides rows
 * from the author and nothing from the audience.
 */
fun Document.presentationNumbers(): List<Int?> {
    var shown = 0
    return slides.map { slide -> if (slide.skipped) null else ++shown }
}

/**
 * Takes the slide with [id] in or out of the presentation.
 *
 * Per slide, never per row: a collapsed parent's hidden run does not follow it,
 * because skip is about what the audience sees and collapse is about what the
 * author does. An unknown id, or a slide already like this, returns this same
 * instance so the caller can skip the history entry.
 */
fun Document.setSlideSkipped(id: String, skipped: Boolean): Document {
    val index: Int = slides.indexOfFirst { it.id == id }
    if (index == -1 || slides[index].skipped == skipped) return this
    return copy(
        slides = slides.mapIndexed { at, slide ->
            if (at == index) slide.copy(skipped = skipped) else slide
        },
    )
}
