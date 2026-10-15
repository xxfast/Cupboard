package io.github.xxfast.cupboard.document

/**
 * What a text box's fields read on one slide: its place in the presentation,
 * how many places there are, and its title.
 *
 * [number] is null on a skipped slide, which has no place in the presentation,
 * and a `{number}` there reads as nothing at all. [total] counts the slides that
 * will actually be shown, so skipping one closes the count up the way
 * [Document.presentationNumbers] closes the numbering up.
 */
data class SlideFields(val number: Int?, val total: Int, val title: String)

/**
 * The fields a text box can hold, as typed: `{number}`, `{total}` and `{title}`.
 * Braces round anything else are just braces, and stay as typed.
 */
private val Field = Regex("""\{(number|total|title)\}""")

/** The two fields that are the slide number, which is what [Slide.showsSlideNumber] switches. */
private val SlideNumberField = Regex("""\{(number|total)\}""")

/** What the slide at [index] of [Document.slides] fills its fields in with. */
fun Document.slideFields(index: Int): SlideFields = SlideFields(
    number = presentationNumbers()[index],
    total = slides.count { !it.skipped },
    title = slides[index].title,
)

/**
 * [slideFields] for the slide with [id], null for an id [Document.slides]
 * doesn't hold: a layout, which is a template with no place of its own.
 */
fun Document.slideFields(id: String): SlideFields? {
    val index: Int = slides.indexOfFirst { it.id == id }
    return if (index == -1) null else slideFields(index)
}

/**
 * This text with every field in it filled in from [fields].
 *
 * Null is what something with no slide to read them from shows, a layout being
 * edited or drawn as a template: a `#` for either number and `Title` for the
 * title, so the box still reads as the field it is rather than as its braces.
 */
fun String.resolveFields(fields: SlideFields?): String {
    if ('{' !in this) return this
    return Field.replace(this) { match ->
        when (match.groupValues[1]) {
            "number" -> if (fields == null) "#" else fields.number?.toString().orEmpty()
            "total" -> fields?.total?.toString() ?: "#"
            else -> fields?.title ?: "Title"
        }
    }
}

/** Whether this box's text holds any field, which is whether it reads differently per slide. */
val TextElement.hasFields: Boolean
    get() = Field.containsMatchIn(text)

/** Whether this box's text holds the slide number, `{number}` or `{total}`. */
val TextElement.hasSlideNumber: Boolean
    get() = SlideNumberField.containsMatchIn(text)

/** [TextElement.hasFields] for any element, a group answering for its children. */
private val Element.carriesFields: Boolean
    get() = when (this) {
        is TextElement -> hasFields
        is GroupElement -> children.any { it.carriesFields }
        else -> false
    }

/**
 * [TextElement.hasSlideNumber] for any element, a group answering for its
 * children: a number grouped with something goes wherever its group does.
 */
private val Element.carriesSlideNumber: Boolean
    get() = when (this) {
        is TextElement -> hasSlideNumber
        is GroupElement -> children.any { it.carriesSlideNumber }
        else -> false
    }

/**
 * This box the way a slide reading [fields] draws it: every field in its text
 * filled in, and this same instance when it holds none.
 *
 * What the document holds is never this. Fields are worked out at draw time,
 * every time, which is what lets one box on a layout read right on every slide
 * built on it, and an inserted slide renumber everything after it untouched.
 */
fun TextElement.withFields(fields: SlideFields?): TextElement =
    // One field at a time, last first so the ones before it stay where they were
    // found: each is then one edit, which is what carries a range link across it.
    Field.findAll(text).toList().asReversed().fold(this) { box, match ->
        box.withText(box.text.replaceRange(match.range, match.value.resolveFields(fields)))
    }

/** [TextElement.withFields] for any element, through every group it sits in. */
fun Element.withFields(fields: SlideFields?): Element = when (this) {
    is TextElement -> withFields(fields)
    is GroupElement ->
        if (!carriesFields) this
        else copy(children = children.map { it.withFields(fields) })

    else -> this
}

/**
 * Whether this slide leaves [element] undrawn for being its slide number: a box
 * holding `{number}` or `{total}`, on a slide that has switched its number off.
 *
 * Its own boxes and its layout's alike, so a title slide turns off the number
 * every other slide on the layout shows. `{title}` answers to nothing: the
 * switch is about the number.
 */
fun Slide.hidesNumberIn(element: Element): Boolean =
    !showsSlideNumber && element.carriesSlideNumber

/**
 * Whether anything this slide draws reads [SlideFields]: a box with a field in
 * it, its own or [layout]'s. One that doesn't looks the same wherever it sits,
 * which is what a cache of its picture can skip keying on.
 */
fun Slide.readsFields(layout: Slide?): Boolean =
    (elements + inheritedElements(layout)).any { it.carriesFields }
