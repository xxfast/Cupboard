package io.github.xxfast.cupboard.document

/**
 * The deck's layouts, Keynote's masters: a slide points at one, takes its
 * placeholders as its own elements, and inherits everything else on it.
 *
 * A layout is a [Slide], so the whole editor edits one already. What is here is
 * the four a new deck starts with, and the three things that make the pointer
 * mean something: [Slide.applyingLayout] (what a slide takes when the layout it
 * is on changes), [Slide.instantiating] (what a fresh slide starts with) and
 * [Slide.inheritedElements] (what it draws behind its own, and never edits).
 *
 * The ids are fixed rather than fresh. Two decks built from the defaults have the
 * same four layout ids, which costs nothing (ids only ever have to be unique
 * inside one document) and buys a [Document] that compares equal to another built
 * the same way.
 */
fun defaultLayouts(): List<Slide> = defaultLayouts(ElementDefaults())

/**
 * The same four dressed in [defaults], which is how every built-in theme's
 * layouts are made: a theme decides the ink, the layouts decide the geometry, and
 * neither has to repeat the other. See `Theme.kt`.
 */
fun defaultLayouts(defaults: ElementDefaults): List<Slide> = listOf(
    Slide(
        id = "layout-title",
        title = "Title",
        elements = listOf(
            TextElement(
                id = "layout-title-title",
                frame = Frame(146f, 380f, 1627f, 200f),
                text = TitleText,
                fontSize = 94f,
                fontWeight = 700,
                color = defaults.textColor,
                align = TextAlign.Center,
                fontFamily = defaults.textFont,
                role = PlaceholderRole.Title,
            ),
            TextElement(
                id = "layout-title-subtitle",
                frame = Frame(146f, 600f, 1627f, 80f),
                text = "Subtitle",
                fontSize = 39f,
                color = defaults.bodyColor,
                align = TextAlign.Center,
                fontFamily = defaults.textFont,
                role = PlaceholderRole.Body,
            ),
        ),
    ),
    Slide(
        id = "layout-title-body",
        title = "Title & Body",
        elements = listOf(
            titlePlaceholder("layout-title-body-title", defaults),
            bodyPlaceholder("layout-title-body-body", defaults),
        ),
    ),
    Slide(
        id = "layout-code",
        title = "Code",
        elements = listOf(
            titlePlaceholder("layout-code-title", defaults),
            codePlaceholder("layout-code-code", defaults),
        ),
    ),
    Slide(id = "layout-blank", title = "Blank"),
)

/** The placeholder texts, which are what an untouched instance of one says. */
private const val TitleText: String = "Title"
private const val BodyText: String = "Body text"

/** Where a title sits on every layout that has one but the title card's own. */
private fun titlePlaceholder(
    id: String = newId(),
    defaults: ElementDefaults = ElementDefaults(),
): TextElement = TextElement(
    id = id,
    frame = Frame(146f, 130f, 1627f, 142f),
    text = TitleText,
    fontSize = 94f,
    fontWeight = 700,
    color = defaults.textColor,
    fontFamily = defaults.textFont,
    role = PlaceholderRole.Title,
)

private fun bodyPlaceholder(
    id: String = newId(),
    defaults: ElementDefaults = ElementDefaults(),
): TextElement = TextElement(
    id = id,
    frame = Frame(146f, 300f, 1627f, 650f),
    text = BodyText,
    fontSize = 39f,
    lineHeight = 1.5f,
    color = defaults.bodyColor,
    fontFamily = defaults.textFont,
    listStyle = ListStyle.Bullet,
    role = PlaceholderRole.Body,
)

private fun codePlaceholder(
    id: String = newId(),
    defaults: ElementDefaults = ElementDefaults(),
): CodeElement = CodeElement(
    id = id,
    frame = Frame(146f, 300f, 1627f, 650f),
    code = "// code",
    fontSize = 28f,
    theme = defaults.codeTheme,
    showLineNumbers = true,
    role = PlaceholderRole.Code,
)

/**
 * A fresh placeholder of [role], dressed the way [defaultLayouts] dresses that
 * role: what Add Placeholder puts on the layout being edited.
 *
 * The title is the one from "Title & Body" rather than the title card's centred
 * one, because that is the shape a title takes on every layout but that one.
 */
fun placeholderElement(
    role: PlaceholderRole,
    defaults: ElementDefaults = ElementDefaults(),
): Element = when (role) {
    PlaceholderRole.Title -> titlePlaceholder(defaults = defaults)
    PlaceholderRole.Body -> bodyPlaceholder(defaults = defaults)
    PlaceholderRole.Code -> codePlaceholder(defaults = defaults)
    PlaceholderRole.Media -> ImageElement(frame = Frame(146f, 300f, 1627f, 650f), role = role)
}

/** The layout [slide] is built on, null when it is on none or names one that is gone. */
fun Document.layoutOf(slide: Slide): Slide? = layouts.firstOrNull { it.id == slide.layoutId }

/**
 * The layout now sitting at [index], or the last one when the index has fallen
 * off the end. [slideAt]'s rule for the layout list, and null only for a document
 * with no layouts at all.
 */
fun Document.layoutAt(index: Int): Slide? = layouts.getOrNull(index) ?: layouts.lastOrNull()

/**
 * Adds [layout] after the one with [after], or at the end when that is null or
 * names no layout of this document.
 */
fun Document.addLayout(after: String?, layout: Slide): Document {
    val anchor: Int = after?.let { id -> layouts.indexOfFirst { it.id == id } } ?: -1
    val at: Int = if (anchor == -1) layouts.size else anchor + 1
    return copy(layouts = layouts.take(at) + layout + layouts.drop(at))
}

/**
 * Drops the layout with [id], and unhooks every slide that was on it: those
 * slides keep every element they had, they are simply on no layout any more.
 * Deleting one must never take a slide's content with it.
 *
 * The last layout stays, the way the last slide does: a deck with no layout has
 * nothing for New Slide to build on. An id this document doesn't hold, and the
 * last layout, both return this same instance, so a caller can skip the history
 * entry the way [reorderElements] lets it.
 */
fun Document.removeLayout(id: String): Document {
    if (layouts.size <= 1 || layouts.none { it.id == id }) return this
    return copy(
        slides = slides.map { if (it.layoutId == id) it.copy(layoutId = null) else it },
        layouts = layouts.filterNot { it.id == id },
    )
}

/**
 * A deep copy of the layout with [id] right after it, ids and all. An id this
 * document doesn't hold returns this same instance.
 */
fun Document.duplicateLayout(id: String): Document {
    val layout: Slide = layouts.firstOrNull { it.id == id } ?: return this
    return addLayout(id, layout.duplicated())
}

/**
 * Moves the layout [id] names into the gap under [afterId], null meaning the gap
 * above the first one.
 *
 * [Document.moveSlide] without any of the depth work: layouts are a flat list, so
 * there is no run to carry and no nesting to land in. An id this document doesn't
 * hold, an anchor it doesn't hold, and a move that changes nothing all return
 * this same instance.
 */
fun Document.moveLayout(id: String, afterId: String?): Document {
    val moved: Slide = layouts.firstOrNull { it.id == id } ?: return this
    val rest: List<Slide> = layouts.filterNot { it.id == id }
    val at: Int = if (afterId == null) 0 else {
        val anchor: Int = rest.indexOfFirst { it.id == afterId }
        if (anchor == -1) return this
        anchor + 1
    }

    val reordered: List<Slide> = rest.take(at) + moved + rest.drop(at)
    if (reordered == layouts) return this
    return copy(layouts = reordered)
}

/**
 * This layout's placeholders by role, first of each winning: what a slide on it
 * fills in. Everything else on the layout is a static object, which slides
 * inherit rather than own (see [inheritedElements]).
 */
fun Slide.placeholders(): Map<PlaceholderRole, Element> = buildMap {
    for (element in elements) {
        val role: PlaceholderRole = element.placeholderRole ?: continue
        if (role !in keys) put(role, element)
    }
}

/**
 * The layout's own objects, the ones no slide fills in: everything on it without
 * a role. A slide draws them behind its own elements and can't touch them, which
 * is the whole of what "inherited" means here.
 */
fun Slide.inheritedElements(layout: Slide?): List<Element> =
    layout?.elements?.filter { it.placeholderRole == null } ?: emptyList()

/**
 * The slide's own background, then its layout's, then [deck]'s: the whole of
 * where what sits behind a slide comes from, nearest first. Null all the way down
 * is the app's own dark gradient, which is what a renderer paints for null.
 *
 * [deck] is `Document.background`, passed in rather than looked up: the renderers
 * take a slide and its layout, not a document, and a theme's background has to
 * reach them without dragging one along.
 */
fun Slide.effectiveBackground(layout: Slide?, deck: SlideBackground? = null): SlideBackground? =
    background ?: layout?.background ?: deck

/**
 * This slide moved onto [layout], or off every layout when that is null.
 *
 * Content survives: an element already carrying a role takes that placeholder's
 * frame and look and keeps its own id, its own text and its own builds, so
 * switching layouts re-dresses a slide rather than rewriting it. A placeholder
 * the slide has nothing for is copied in fresh, and an element whose role the new
 * layout has no placeholder for is left exactly where it is: a title that stays
 * put is better than one that vanishes because the new layout has no title.
 *
 * Also what Reapply Layout is: applying the layout a slide is already on puts
 * every instance back where the layout says it goes.
 */
fun Slide.applyingLayout(layout: Slide?): Slide {
    if (layout == null) return copy(layoutId = null)

    val placeholders: Map<PlaceholderRole, Element> = layout.placeholders()
    val redressed: List<Element> = elements.map { element ->
        val placeholder: Element = element.placeholderRole
            ?.let { role -> placeholders[role] }
            ?: return@map element
        return@map element.applyingPlaceholder(placeholder)
    }

    val filled: Set<PlaceholderRole> = elements.mapNotNullTo(mutableSetOf()) { it.placeholderRole }
    val added: List<Element> = placeholders
        .filterKeys { role -> role !in filled }
        .values
        .map { it.withNewIds() }

    return copy(layoutId = layout.id, elements = redressed + added)
}

/**
 * A fresh slide's start on [layout]: its placeholders copied in under new ids, so
 * the slide owns them from the first keystroke. Null is a slide on no layout,
 * which starts empty.
 *
 * [applyingLayout]'s sibling, and deliberately not the same function: applying is
 * for a slide that already has content to keep, this is for one that has none.
 */
fun Slide.instantiating(layout: Slide?): Slide {
    if (layout == null) return copy(layoutId = null)
    return copy(
        layoutId = layout.id,
        elements = elements + layout.placeholders().values.map { it.withNewIds() },
    )
}

/**
 * This element wearing [placeholder]'s frame and look, keeping its own id and
 * everything it says.
 *
 * [applyingStyle] is what "look" means, so the exclusions are already the right
 * ones: a text box keeps its text and its link, a code block its code, its
 * language and its steps. The frame comes on top of that, because where a
 * placeholder sits is the larger half of what a layout decides.
 */
private fun Element.applyingPlaceholder(placeholder: Element): Element =
    applyingStyle(placeholder).update(frame = placeholder.frame)
