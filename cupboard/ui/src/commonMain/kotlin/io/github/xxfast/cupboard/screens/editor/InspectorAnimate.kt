package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.xxfast.cupboard.canvas.BuildEffectNames
import io.github.xxfast.cupboard.canvas.title
import io.github.xxfast.cupboard.document.ActionKind
import io.github.xxfast.cupboard.document.AudioElement
import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.BuildAction
import io.github.xxfast.cupboard.document.BuildDelivery
import io.github.xxfast.cupboard.document.BuildKind
import io.github.xxfast.cupboard.document.BuildTrigger
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.DiagramElement
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.EquationElement
import io.github.xxfast.cupboard.document.GalleryElement
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideTransition
import io.github.xxfast.cupboard.document.TerminalElement
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TransitionDirection
import io.github.xxfast.cupboard.document.TransitionKind
import io.github.xxfast.cupboard.document.TransitionTrigger
import io.github.xxfast.cupboard.document.VideoElement
import io.github.xxfast.cupboard.document.action
import io.github.xxfast.cupboard.document.elementById
import io.github.xxfast.cupboard.theme.ChromeTheme
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LocalChromeTheme
import io.github.xxfast.cupboard.theme.LocalChromeTokens
import kotlin.math.roundToInt

/**
 * The Animate tab: Build In / Action / Build Out over the selected element's
 * builds, or the slide's transition with nothing selected.
 *
 * Which segment is showing is the core's ([EditorState.animateSegment]), so it
 * is remembered across selections the way the Format segments are. Every control
 * commits one settled edit, the duration and opacity drags excepted: their
 * samples preview the slide and their release commits one, so a drag is one
 * history entry rather than one per sample.
 *
 * Build Order is pinned to the foot on both states and swaps the body for the
 * order list. That it is open is view-local by design: which panel a user is
 * looking at is no fact about the document, and nothing on the canvas draws
 * from it. Keynote floats that list in a window; we keep it in the panel,
 * because `design/` draws the rows in the inspector.
 *
 * Layouts have neither builds nor a transition. A layout is never presented;
 * the slide wearing it is, and it carries its own.
 */
@Composable
internal fun ColumnScope.AnimatePanel(
    slide: Slide,
    isEditingLayouts: Boolean,
    selectedElements: List<Element>,
    animateSegment: AnimateSegment,
    onSelectAnimateSegment: (AnimateSegment) -> Unit,
    onSelectElement: (String?) -> Unit,
    onSetTransition: (slideId: String, transition: SlideTransition?) -> Unit,
    onPreview: (Slide) -> Unit,
    onUpdate: (Slide) -> Unit,
    onAddBuild: (Build) -> Unit,
    onUpdateBuild: (index: Int, build: Build) -> Unit,
    onRemoveBuild: (index: Int) -> Unit,
    onMoveBuild: (from: Int, to: Int) -> Unit,
    onPlayPreview: (() -> Unit)?,
) {
    if (isEditingLayouts) {
        PanelBody {
            Text(
                text = "Layouts have no builds or transitions.",
                color = LocalChromeTokens.current.subtle,
                fontSize = 12.sp,
            )
        }
        return
    }

    // Dropped whenever the slide changes: an order is that slide's, and coming
    // back to a different one should come back to the segments.
    var showingOrder: Boolean by remember(slide.id) { mutableStateOf(false) }

    if (showingOrder) {
        PanelTitle("Build Order")
        PanelDivider()

        PanelBody {
            BuildOrderBody(
                slide = slide,
                selectedElements = selectedElements,
                onSelectElement = onSelectElement,
                onMove = onMoveBuild,
                onPlayPreview = onPlayPreview,
            )
        }

        PanelDivider()

        PinnedBlock {
            TonalButton(
                label = "Done",
                enabled = true,
                onClick = { showingOrder = false },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    val primary: Element? = selectedElements.firstOrNull()

    if (primary == null) {
        PanelTitle("Transitions")
        PanelDivider()

        PanelBody {
            TransitionSection(
                slide = slide,
                onSetTransition = onSetTransition,
                onPreview = onPreview,
                onUpdate = onUpdate,
            )
        }
    } else {
        PinnedBlock {
            SegmentPicker(
                options = ANIMATE_SEGMENTS,
                selected = animateSegment,
                onPick = onSelectAnimateSegment,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PanelBody {
            BuildsBody(
                slide = slide,
                primary = primary,
                segment = animateSegment,
                onPreview = onPreview,
                onAdd = onAddBuild,
                onUpdate = onUpdateBuild,
                onRemove = onRemoveBuild,
                onPlayPreview = onPlayPreview,
            )
        }
    }

    PanelDivider()

    PinnedBlock {
        TonalButton(
            label = "Build Order",
            enabled = true,
            onClick = { showingOrder = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The three segments, titled the way Keynote titles them. */
private val ANIMATE_SEGMENTS: List<Pair<AnimateSegment, String>> = listOf(
    AnimateSegment.BuildIn to "Build In",
    AnimateSegment.Action to "Action",
    AnimateSegment.BuildOut to "Build Out",
)

/** Which builds a segment is about. One each, and all three kinds are covered. */
private fun AnimateSegment.buildKind(): BuildKind = when (this) {
    AnimateSegment.BuildIn -> BuildKind.In
    AnimateSegment.Action -> BuildKind.Action
    AnimateSegment.BuildOut -> BuildKind.Out
}

/** What the empty state calls the effect the segment hasn't got. */
private fun AnimateSegment.effectName(): String = when (this) {
    AnimateSegment.BuildIn -> "Build In"
    AnimateSegment.Action -> "Action"
    AnimateSegment.BuildOut -> "Build Out"
}

/**
 * The primary element's builds of the showing kind: one block each, effect name
 * over Change and Preview, then that build's own controls.
 *
 * Nothing here is greyed for a locked element. A build belongs to the slide's
 * order rather than to the element, so animating a locked shape is no more
 * editing it than locking it was.
 *
 * Only Action holds more than one in practice, and only Action keeps the add
 * button once it has one: a second Build In on the same element is a build order
 * nobody meant to write.
 */
@Composable
private fun BuildsBody(
    slide: Slide,
    primary: Element,
    segment: AnimateSegment,
    onPreview: (Slide) -> Unit,
    onAdd: (Build) -> Unit,
    onUpdate: (index: Int, build: Build) -> Unit,
    onRemove: (index: Int) -> Unit,
    onPlayPreview: (() -> Unit)?,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val kind: BuildKind = segment.buildKind()
    val mine: List<IndexedValue<Build>> = slide.builds
        .withIndex()
        .filter { (_, build) -> build.elementId == primary.id && build.kind == kind }

    if (mine.isEmpty()) {
        Text(
            text = "No ${segment.effectName()} Effect",
            color = tokens.faint,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        AddEffectButton(kind = kind, elementId = primary.id, onAdd = onAdd)
        return
    }

    for ((index, build) in mine) {
        Text(
            text = build.effectTitle(),
            color = tokens.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChangeEffectButton(
                build = build,
                onPick = { changed -> onUpdate(index, changed) },
                modifier = Modifier.weight(1f),
            )
            TonalButton(
                label = "Preview ▶",
                enabled = onPlayPreview != null,
                onClick = { onPlayPreview?.invoke() },
                modifier = Modifier.weight(1f),
            )
        }

        BuildControls(
            slide = slide,
            index = index,
            build = build,
            onPreview = onPreview,
            onUpdate = onUpdate,
            onRemove = onRemove,
        )

        PanelDivider()
    }

    // An element can hold several actions and exactly one build in or out, so
    // this is the only segment that keeps offering to add another.
    if (kind == BuildKind.Action) {
        AddEffectButton(kind = kind, elementId = primary.id, onAdd = onAdd)
    }
}

/** The empty segment's one verb, and Action's way to a second one. */
@Composable
private fun AddEffectButton(kind: BuildKind, elementId: String, onAdd: (Build) -> Unit) {
    MenuButton(
        label = "Add an Effect",
        enabled = true,
        accent = true,
        choices = effectChoices(kind) { build -> onAdd(build(elementId)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Change: the same catalog, landing on the build that is already there. */
@Composable
private fun ChangeEffectButton(
    build: Build,
    onPick: (Build) -> Unit,
    modifier: Modifier = Modifier,
) {
    MenuButton(
        label = "Change",
        enabled = true,
        choices = when (build.kind) {
            BuildKind.Action -> ActionKind.entries.map { kind ->
                MenuChoice(kind.name) {
                    onPick(build.copy(action = (build.action ?: BuildAction(kind)).copy(kind = kind)))
                }
            }

            else -> BuildEffectNames.map { (effect, title) ->
                MenuChoice(title) { onPick(build.copy(effect = effect)) }
            }
        },
        modifier = modifier,
    )
}

/**
 * The catalog one kind of build is picked out of, as the build each entry would
 * add. An action starts on a gentle scale-up rather than on nothing, so adding
 * one is something rather than a no-op waiting for a second click.
 */
private fun effectChoices(
    kind: BuildKind,
    onPick: ((elementId: String) -> Build) -> Unit,
): List<MenuChoice> = when (kind) {
    BuildKind.Action -> ActionKind.entries.map { action ->
        MenuChoice(action.name) {
            onPick { id -> Build.action(id, BuildAction(action, scale = 1.2f)) }
        }
    }

    else -> BuildEffectNames.map { (effect, title) ->
        MenuChoice(title) { onPick { id -> Build(elementId = id, kind = kind, effect = effect) } }
    }
}

/** What a configured build is headed with: its effect, or what its action does. */
private fun Build.effectTitle(): String =
    if (kind == BuildKind.Action) action?.kind?.name ?: "None" else effect.title()

/**
 * The controls for one build: how much of its element it hands over at a time,
 * how long it takes, what starts it, and what it does where it is an action.
 *
 * Every one of them commits a whole [Build] through [onUpdate], one settled edit
 * each, the duration and opacity drags excepted: their samples preview the slide
 * and their release commits, the way the transition's duration does.
 *
 * The effect itself isn't here. Change owns it, which is what keeps one build
 * from carrying two controls that set the same field.
 */
@Composable
private fun BuildControls(
    slide: Slide,
    index: Int,
    build: Build,
    onPreview: (Slide) -> Unit,
    onUpdate: (index: Int, build: Build) -> Unit,
    onRemove: (index: Int) -> Unit,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val element: Element? = slide.elementById(build.elementId)
    val action: BuildAction? = build.action

    fun edit(change: (Build) -> Build) = onUpdate(index, change(build))

    if (build.kind != BuildKind.Action) {
        val deliveries: List<Pair<BuildDelivery, String>> = element.deliveries()
        if (deliveries.isNotEmpty()) DropdownField(
            label = "Delivery",
            value = build.delivery,
            options = deliveries.withDelivery(build.delivery),
            enabled = true,
            onPick = { delivery -> edit { it.copy(delivery = delivery) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (action != null && build.kind == BuildKind.Action) when (action.kind) {
        ActionKind.Move -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(
                label = "dx",
                value = action.dx,
                enabled = true,
                onCommit = { dx -> edit { it.copy(action = action.copy(dx = dx)) } },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "dy",
                value = action.dy,
                enabled = true,
                onCommit = { dy -> edit { it.copy(action = action.copy(dy = dy)) } },
                modifier = Modifier.weight(1f),
            )
        }

        ActionKind.Opacity -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Opacity", color = tokens.subtle, fontSize = 12.sp, modifier = Modifier.width(56.dp))
            SliderRow(
                fraction = action.opacity,
                valueLabel = "${(action.opacity * 100).roundToInt()}%",
                modifier = Modifier.weight(1f),
                onDrag = { value ->
                    onPreview(slide.withBuild(index, build.copy(action = action.copy(opacity = value))))
                },
                onRelease = { value -> edit { it.copy(action = action.copy(opacity = value)) } },
            )
        }

        ActionKind.Rotate -> NumberField(
            label = "Rotation",
            value = action.rotation,
            enabled = true,
            onCommit = { rotation -> edit { it.copy(action = action.copy(rotation = rotation)) } },
            modifier = Modifier.width(150.dp),
            unit = Units.Degrees,
        )

        ActionKind.Scale -> NumberField(
            label = "Scale",
            value = action.scale,
            enabled = true,
            onCommit = { scale -> edit { it.copy(action = action.copy(scale = scale)) } },
            modifier = Modifier.width(150.dp),
            minimum = 0.01f,
            fractional = true,
            unit = Units.Times,
            step = 0.1f,
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Duration", color = tokens.subtle, fontSize = 12.sp, modifier = Modifier.width(56.dp))
        SliderRow(
            fraction = build.durationMs.asDurationFraction(),
            valueLabel = build.durationMs.asSeconds(),
            modifier = Modifier.weight(1f),
            onDrag = { value ->
                onPreview(slide.withBuild(index, build.copy(durationMs = value.asDurationMs())))
            },
            onRelease = { value -> edit { it.copy(durationMs = value.asDurationMs()) } },
        )
    }

    SegmentedRow(Modifier.fillMaxWidth()) {
        BUILD_TRIGGERS.forEachIndexed { at, (trigger, title) ->
            val selected: Boolean = build.trigger == trigger
            Segment(
                selected = selected,
                first = at == 0,
                onClick = { edit { it.copy(trigger = trigger) } },
            ) {
                SegmentLabel(title, selected = selected, enabled = true)
            }
        }
    }

    // Only the build that waits has a wait: everything else starts on its own
    // trigger, and there is nothing to hold it back from.
    if (build.trigger == BuildTrigger.AfterPrevious) NumberField(
        label = "Delay",
        value = build.delayMs / 1000f,
        enabled = true,
        onCommit = { seconds -> edit { it.copy(delayMs = (seconds * 1000).roundToInt()) } },
        modifier = Modifier.fillMaxWidth(),
        minimum = 0f,
        fractional = true,
        unit = Units.Seconds,
        step = 0.1f,
    )

    if (element.ownSteps() > 0) NumberField(
        label = "Step",
        value = (build.elementStep ?: 0).toFloat(),
        enabled = true,
        // 0 is no step build at all rather than the first state: a stepped
        // element shows its first state from the moment it is on the slide.
        onCommit = { step -> edit { it.copy(elementStep = step.roundToInt().takeIf { at -> at > 0 }) } },
        modifier = Modifier.width(150.dp),
        minimum = 0f,
        unit = Units.None,
    )

    TonalButton(
        label = "Remove",
        enabled = true,
        onClick = { onRemove(index) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The Build Order body: Preview over the rows, in the order they play.
 *
 * Picking a row still selects the element it animates, which is what makes the
 * list a way around the slide rather than a list of strings.
 */
@Composable
private fun BuildOrderBody(
    slide: Slide,
    selectedElements: List<Element>,
    onSelectElement: (String?) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onPlayPreview: (() -> Unit)?,
) {
    val selectedIds: Set<String> = selectedElements.mapTo(mutableSetOf()) { it.id }

    // Above the order rather than below it: the builds are read top down, and
    // watching them run is the question the list raises, not an afterthought to
    // it. Live on a slide with no builds too, where it plays the slide's own
    // transition in, which is still what the tab is about.
    TonalButton(
        label = "Preview",
        enabled = onPlayPreview != null,
        onClick = { onPlayPreview?.invoke() },
        modifier = Modifier.fillMaxWidth(),
    )

    if (slide.builds.isEmpty()) {
        Text(
            text = "Nothing builds on this slide yet.",
            color = LocalChromeTokens.current.subtle,
            fontSize = 12.sp,
        )
        return
    }

    BuildOrderList(
        slide = slide,
        activeIds = selectedIds,
        onPick = { index -> onSelectElement(slide.builds[index].elementId) },
        onMove = onMove,
    )
}

/**
 * The rows, in the order they play, drag-reorderable.
 *
 * The drag keeps its own state here, unlike the navigator's: this list is the
 * inspector's own, nothing on the canvas draws from it, and the drop commits one
 * [onMove] whatever the travel drew. One gesture is still one history entry.
 */
@Composable
private fun BuildOrderList(
    slide: Slide,
    activeIds: Set<String>,
    onPick: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    // Layout data, not gesture state, the way the navigator keeps its rows:
    // nothing draws from this, it only tells a drag what it is over.
    val bounds: MutableMap<Int, ClosedFloatingPointRange<Float>> = remember { mutableMapOf() }
    // The row on the move, and how far it has come.
    var dragging: Int? by remember { mutableStateOf(null) }
    var travel: Float by remember { mutableStateOf(0f) }

    /**
     * Where a row carried to [windowY] lands: the count of the rows staying put
     * whose middle it has passed, which is exactly the index `MoveBuild` inserts
     * at once [from] is lifted out.
     */
    fun targetAt(windowY: Float, from: Int): Int {
        var to = 0
        for (index in slide.builds.indices) {
            if (index == from) continue
            val range: ClosedFloatingPointRange<Float> = bounds[index] ?: continue
            if (windowY > (range.start + range.endInclusive) / 2f) to++
        }
        return to
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((index, build) in slide.builds.withIndex()) {
            val moving: Boolean = dragging == index
            BuildOrderRow(
                order = index + 1,
                title = slide.elementById(build.elementId)?.buildTitle() ?: "Missing element",
                meta = build.meta(index),
                active = build.elementId in activeIds,
                onClick = { onPick(index) },
                modifier = Modifier
                    .zIndex(if (moving) 1f else 0f)
                    .graphicsLayer { translationY = if (moving) travel else 0f }
                    // Only while nothing is dragging: a row carried by the
                    // pointer reports where it is being held, which is no place
                    // to measure a drop against.
                    .onGloballyPositioned {
                        if (dragging != null) return@onGloballyPositioned
                        val top: Float = it.positionInWindow().y
                        bounds[index] = top..top + it.size.height
                    }
                    .pointerInput(index, slide.builds.size) {
                        // Where the press landed in window pixels, and the row
                        // the travel has carried it to.
                        var startY = 0f
                        var to: Int = index
                        detectDragGestures(
                            onDragStart = { press ->
                                startY = (bounds[index]?.start ?: 0f) + press.y
                                dragging = index
                                travel = 0f
                                to = index
                            },
                            onDragEnd = {
                                dragging = null
                                travel = 0f
                                if (to != index) onMove(index, to)
                            },
                            onDragCancel = {
                                dragging = null
                                travel = 0f
                            },
                        ) { change, amount ->
                            change.consume()
                            travel += amount.y
                            to = targetAt(startY + travel, index)
                        }
                    },
            )
        }
    }
}

/**
 * One build-order row: its number, what it animates and how, and the grab glyph.
 *
 * [active] is the design's live row, whose element is in the canvas selection.
 * Windows dresses an active row with an accent rail down its leading edge
 * instead of the tonal fill, which is [ChromeTheme.rowRail].
 */
@Composable
private fun BuildOrderRow(
    order: Int,
    title: String,
    meta: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val theme: ChromeTheme = LocalChromeTheme.current
    val corner: RoundedCornerShape = RoundedCornerShape(theme.rowR)
    val rail: Boolean = active && theme.rowRail
    val filled: Boolean = active && !theme.rowRail

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(corner)
            .background(if (filled) tokens.tonal else tokens.rowBg)
            .let { if (active) it.border(1.dp, tokens.accent, corner) else it }
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rail) Box(Modifier.width(3.dp).fillMaxHeight().background(tokens.accent))

        Row(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (active) tokens.accent else tokens.badgeOff),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$order",
                    color = if (active) tokens.accentText else tokens.badgeOffText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (filled) tokens.tonalText else tokens.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    color = tokens.subtle,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("⠿", color = tokens.subtle, fontSize = 13.sp)
        }
    }
}

/**
 * The slide's own transition, the whole of it stateless against [slide].
 *
 * Null is the deck's transition rather than none at all, so a slide on the deck's
 * own reads as the empty state: there is no effect of this slide's to configure
 * until one is added.
 */
@Composable
private fun TransitionSection(
    slide: Slide,
    onSetTransition: (String, SlideTransition?) -> Unit,
    onPreview: (Slide) -> Unit,
    onUpdate: (Slide) -> Unit,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val transition: SlideTransition? = slide.transition

    if (transition == null || transition.kind == TransitionKind.None) {
        Text(
            text = "No Transition Effect",
            color = tokens.faint,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        MenuButton(
            label = "Add an Effect",
            enabled = true,
            accent = true,
            choices = TRANSITION_EFFECTS.map { (kind, title) ->
                MenuChoice(title) {
                    onSetTransition(slide.id, (transition ?: SlideTransition()).copy(kind = kind))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    Text(
        text = TRANSITION_EFFECTS.firstOrNull { (kind, _) -> kind == transition.kind }?.second
            ?: transition.kind.name,
        color = tokens.text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MenuButton(
            label = "Change",
            enabled = true,
            choices = TRANSITION_EFFECTS.map { (kind, title) ->
                MenuChoice(title) { onSetTransition(slide.id, transition.copy(kind = kind)) }
            },
            modifier = Modifier.weight(1f),
        )
        // Back onto the deck's own, which is what "no transition of this slide's"
        // means: null, not TransitionKind.None.
        TonalButton(
            label = "Remove",
            enabled = true,
            onClick = { onSetTransition(slide.id, null) },
            modifier = Modifier.weight(1f),
        )
    }

    if (transition.kind in DIRECTIONAL_KINDS) SegmentedRow(Modifier.fillMaxWidth()) {
        TransitionDirection.entries.forEachIndexed { index, direction ->
            val selected: Boolean = transition.direction == direction
            Segment(
                selected = selected,
                first = index == 0,
                onClick = { onSetTransition(slide.id, transition.copy(direction = direction)) },
            ) {
                SegmentLabel(direction.name, selected = selected, enabled = true)
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Duration", color = tokens.subtle, fontSize = 12.sp, modifier = Modifier.width(56.dp))
        SliderRow(
            fraction = transition.durationMs.asDurationFraction(),
            valueLabel = transition.durationMs.asSeconds(),
            modifier = Modifier.weight(1f),
            onDrag = { value -> onPreview(slide.withDuration(value)) },
            onRelease = { value -> onUpdate(slide.withDuration(value)) },
        )
    }

    SectionLabel("START TRANSITION")
    SegmentedRow(Modifier.fillMaxWidth()) {
        TRANSITION_TRIGGERS.forEachIndexed { index, (trigger, title) ->
            val selected: Boolean = transition.trigger == trigger
            Segment(
                selected = selected,
                first = index == 0,
                onClick = { onSetTransition(slide.id, transition.copy(trigger = trigger)) },
            ) {
                SegmentLabel(title, selected = selected, enabled = true)
            }
        }
    }

    // Only the automatic slide has one: a slide waiting for a click waits as
    // long as it is left waiting.
    if (transition.trigger == TransitionTrigger.Automatic) NumberField(
        label = "Delay",
        value = transition.delayMs / 1000f,
        enabled = true,
        onCommit = { seconds ->
            onSetTransition(slide.id, transition.copy(delayMs = (seconds * 1000).roundToInt()))
        },
        modifier = Modifier.fillMaxWidth(),
        minimum = 0f,
        fractional = true,
        unit = Units.Seconds,
        step = 0.1f,
    )
}

/** The effects a slide can transition on: every kind that is one. */
private val TRANSITION_EFFECTS: List<Pair<TransitionKind, String>> = listOf(
    TransitionKind.Dissolve to "Dissolve",
    TransitionKind.Push to "Push",
    TransitionKind.MoveIn to "Move In",
    TransitionKind.Wipe to "Wipe",
    TransitionKind.MagicMove to "Magic Move",
)

/** The kinds that travel, and so the only ones a direction means anything to. */
private val DIRECTIONAL_KINDS: Set<TransitionKind> =
    setOf(TransitionKind.Push, TransitionKind.MoveIn, TransitionKind.Wipe)

private val TRANSITION_TRIGGERS: List<Pair<TransitionTrigger, String>> = listOf(
    TransitionTrigger.OnClick to "On Click",
    TransitionTrigger.Automatic to "Automatically",
)

/** Abbreviated: three of these share the panel's width. */
private val BUILD_TRIGGERS: List<Pair<BuildTrigger, String>> = listOf(
    BuildTrigger.OnClick to "On Click",
    BuildTrigger.WithPrevious to "With Prev",
    BuildTrigger.AfterPrevious to "After Prev",
)

/** The duration slider's ends, and the tenth of a second its samples land on. */
private const val MIN_DURATION_MS: Int = 100
private const val MAX_DURATION_MS: Int = 3000
private const val DURATION_STEP_MS: Int = 100

/** Where a duration sits along the slider, as 0..1. */
private fun Int.asDurationFraction(): Float =
    (this - MIN_DURATION_MS).toFloat() / (MAX_DURATION_MS - MIN_DURATION_MS)

/** A slider sample as a duration, snapped so the label reads in whole tenths. */
private fun Float.asDurationMs(): Int {
    val raw: Float = MIN_DURATION_MS + this * (MAX_DURATION_MS - MIN_DURATION_MS)
    return (raw / DURATION_STEP_MS).roundToInt() * DURATION_STEP_MS
}

/** Tenths of a second, as fine as the slider goes: 600 reads "0.6s". */
private fun Int.asSeconds(): String = "${this / 1000}.${this % 1000 / 100}s"

/**
 * The slide with a duration off a slider sample. Only ever called on a slide
 * that has a transition, since only that slide draws the slider.
 */
private fun Slide.withDuration(sample: Float): Slide {
    val transition: SlideTransition = this.transition ?: SlideTransition()
    return copy(transition = transition.copy(durationMs = sample.asDurationMs()))
}

/** [slide] with one build replaced: what a drag samples before it commits one. */
private fun Slide.withBuild(index: Int, build: Build): Slide =
    copy(builds = builds.mapIndexed { at, existing -> if (at == index) build else existing })

/**
 * What a row calls the element a build animates: its kind, and the first thing
 * it holds, so two text boxes on one slide read apart.
 */
private fun Element.buildTitle(): String = when (this) {
    is TextElement -> titled("Text", text)
    is ShapeElement -> titled("Shape", label)
    is TerminalElement -> titled("Terminal", title)
    is EquationElement -> titled("Equation", latex)
    is CodeElement -> "Code"
    is DiagramElement -> "Diagram"
    is ImageElement -> "Image"
    is GalleryElement -> titled("Gallery", images.firstOrNull()?.caption.orEmpty())
    is VideoElement -> titled("Video", title.ifBlank { webUrl.orEmpty() })
    is AudioElement -> titled("Audio", title)
    is GroupElement -> "Group"
}

/** "[kind]: first line of [content]", or [kind] alone where there is none. */
private fun titled(kind: String, content: String): String {
    val hint: String = content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    return if (hint.isEmpty()) kind else "$kind: ${hint.take(24)}"
}

/**
 * The row's second line: what the build does, how long it takes, and what starts
 * it. [index] is the build's place in the order, which is what "after 2" counts.
 */
private fun Build.meta(index: Int): String {
    val start: String = when (trigger) {
        BuildTrigger.OnClick -> "on click"
        // The first build has nothing ahead of it, so both of these land with
        // the slide rather than after anything.
        BuildTrigger.WithPrevious -> if (index == 0) "with slide" else "with $index"
        BuildTrigger.AfterPrevious -> if (index == 0) "with slide" else "after $index"
    }
    val what: String = when (kind) {
        BuildKind.In -> effect.title()
        BuildKind.Out -> "Out · ${effect.title()}"
        BuildKind.Action -> "Action · ${action.title()}"
    }
    return "$what · ${durationMs.asSeconds()} · $start"
}

/** What an action does, in the units it does it in. */
private fun BuildAction?.title(): String {
    if (this == null) return "None"

    return when (kind) {
        ActionKind.Move -> "Move ${dx.asWholeNumber()},${dy.asWholeNumber()}"
        ActionKind.Opacity -> "Opacity ${(opacity * 100).roundToInt()}%"
        ActionKind.Rotate -> "Rotate ${rotation.asWholeNumber()}°"
        ActionKind.Scale -> "Scale ${scale.asMultiplier()}"
    }
}

/**
 * The pieces this element can be handed over in, empty for one that has none:
 * a shape or an image arrives whole however a build was dressed.
 */
private fun Element?.deliveries(): List<Pair<BuildDelivery, String>> = when (this) {
    is TextElement -> listOf(
        BuildDelivery.All to "All",
        BuildDelivery.ByParagraph to "By Paragraph",
        BuildDelivery.ByWord to "By Word",
        BuildDelivery.ByCharacter to "By Character",
    )

    is CodeElement, is TerminalElement -> listOf(
        BuildDelivery.All to "All",
        BuildDelivery.ByLine to "By Line",
    )

    else -> emptyList()
}

/** [delivery] appended under its own name where the list doesn't carry it, so a
 * build dressed for another kind of element still shows what it holds. */
private fun List<Pair<BuildDelivery, String>>.withDelivery(
    delivery: BuildDelivery,
): List<Pair<BuildDelivery, String>> =
    if (any { (option, _) -> option == delivery }) this else this + (delivery to delivery.name)

/** How many states of its own this element has, 0 for one that isn't stepped. */
private fun Element?.ownSteps(): Int = when (this) {
    is CodeElement -> steps.size
    is DiagramElement -> steps.size
    else -> 0
}
