package io.github.xxfast.cupboard.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class BuildEffect {
    /** No animation at all: the element is simply there on its step, and gone on its exit. */
    Appear,
    FadeUp,
    Pop,
    Dissolve,

    /** In from the leading edge, out the same way. */
    MoveIn,

    /** Grows out of nothing, and shrinks back into it. */
    Scale,

    /** Uncovered top to bottom, its box growing with it, and re-covered on the way out. */
    Wipe,

    /**
     * Types the element in rather than bringing it in whole. Only
     * [TerminalElement] honours it: its command lines type out character by
     * character over the build's duration, and each block of output lands the
     * moment the command above it has finished. Every other kind treats it as
     * [Dissolve], so an effect set on the wrong element still reveals it.
     */
    Typewriter,
}

/**
 * Whether a build brings its element on, takes it away, or moves it about while
 * it is there.
 *
 * An element with no [In] build at all is on the slide from the start, so an
 * [Out] build on its own is how something that opens with the slide leaves it.
 * An [Action] changes nothing about visibility: it carries a [BuildAction] and
 * animates an element that is already on the slide.
 */
enum class BuildKind { In, Out, Action }

/**
 * What one action does to the element it names.
 *
 * [Move] and [Rotate] are relative, so two of them in a row compose into one
 * longer journey; [Scale] multiplies about the element's centre; [Opacity] is
 * absolute, and the last one to land is the one that counts.
 */
enum class ActionKind { Move, Opacity, Rotate, Scale }

/**
 * The change an [BuildKind.Action] build makes, of whichever [kind] it is: only
 * that kind's fields are read, and the rest sit at the value that changes
 * nothing.
 *
 * [dx] and [dy] are document units, [rotation] is degrees clockwise, [scale] is a
 * factor about the element's centre and [opacity] is the fraction the element
 * draws at.
 */
@Serializable
data class BuildAction(
    val kind: ActionKind,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val opacity: Float = 1f,
    val rotation: Float = 0f,
    val scale: Float = 1f,
)

/**
 * How much of the element one build hands over: all of it, or one piece at a
 * time.
 *
 * The pieces are the element's own text ([ByParagraph], [ByWord], [ByCharacter])
 * or its lines ([ByLine], which is also how a code block and a terminal are
 * walked). A delivery its element has no pieces for is one piece, so a delivery
 * set on the wrong kind still reveals it whole.
 */
enum class BuildDelivery { All, ByParagraph, ByWord, ByCharacter, ByLine }

enum class BuildTrigger {
    /** Advances on its own step (a click in play mode). */
    OnClick,

    /** Plays together with the previous build's step. */
    WithPrevious,

    /**
     * Plays on the step the previous build ends on, [Build.delayMs] after that
     * build has finished. No click of its own: a chain of these runs itself out
     * once the click that started it has landed.
     */
    AfterPrevious,
}

/**
 * One entry in a slide's Animate build order.
 *
 * [elementStep] is the index into the target element's own steps, and is null for
 * every build that only brings an element in. Which steps those are is the
 * element's business: a code block's [CodeElement.steps], a diagram's
 * [DiagramElement.steps]. It rides the existing triggers rather than inventing
 * its own, so a `WithPrevious` step build lands on the same click as the build
 * before it.
 *
 * The tag stays "codeStep": decks were written before the field grew past code,
 * and a rename on disk would be a rename of every one of them. Everything added
 * since defaults to what a deck written before it meant, so a file on disk opens
 * as the build order it was saved as.
 */
@Serializable
data class Build(
    val elementId: String,
    val kind: BuildKind = BuildKind.In,
    val effect: BuildEffect = BuildEffect.FadeUp,
    val durationMs: Int = 400,
    val delivery: BuildDelivery = BuildDelivery.All,
    val trigger: BuildTrigger = BuildTrigger.OnClick,
    /**
     * How long after its trigger the build starts. Waited out on top of whatever
     * the chain before it is still doing, which is what makes a
     * [BuildTrigger.AfterPrevious] run of builds a sequence rather than a pile.
     */
    val delayMs: Int = 0,
    @SerialName("codeStep") val elementStep: Int? = null,
    /**
     * What the build does when its [kind] is [BuildKind.Action], and nothing at
     * all otherwise. An action build without one is a no-op rather than a
     * failure: a deck opens as what it was saved as, whatever a panel left behind.
     */
    val action: BuildAction? = null,
) {
    companion object
}

/**
 * An [BuildKind.Action] build, for the panels that add one.
 *
 * Here rather than at the call sites because every shell writes the same four
 * fields, and a build that is an action everywhere but in its kind is a bug that
 * only shows up in play mode.
 */
fun Build.Companion.action(
    elementId: String,
    action: BuildAction,
    trigger: BuildTrigger = BuildTrigger.OnClick,
    durationMs: Int = 600,
): Build = Build(
    elementId = elementId,
    kind = BuildKind.Action,
    durationMs = durationMs,
    trigger = trigger,
    action = action,
)

/**
 * Where an element sits, and how it is drawn, once every action that has landed
 * has had its say: [dx] and [dy] in document units off its frame, [rotation] in
 * degrees on top of its own, [scale] about its centre, and [opacity] the one an
 * action set, null when none did.
 */
data class ActionState(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val opacity: Float? = null,
    val rotation: Float = 0f,
    val scale: Float = 1f,
)

/**
 * One build's place in the slide's step model: the steps it spans, and how long
 * after its step opens it starts.
 *
 * [firstStep] and [lastStep] are the same step for everything but a
 * [BuildTrigger.OnClick] build with pieces, which takes one step per piece: each
 * piece is its own click, the way Keynote hands out a bulleted list.
 */
data class BuildAt(
    val index: Int,
    val build: Build,
    val firstStep: Int,
    val lastStep: Int,
    /** The build's own delay plus everything the chain ahead of it is still waiting on. */
    val delayMs: Int,
)

/**
 * How much of an element is out at a step, for a build that hands it over in
 * pieces: the [build] doing the delivering, the count of pieces showing, and how
 * many there are in all.
 */
data class PieceReveal(val build: Build, val shown: Int, val total: Int)

/**
 * How many pieces this build hands [slide]'s element over in: 1 for
 * [BuildDelivery.All], and otherwise what the element has to give.
 *
 * A delivery the element has no pieces for (words of a code block, paragraphs of
 * a terminal) is worth one piece rather than none: a build always reveals
 * something, whatever it was pointed at.
 */
fun Build.pieceCount(slide: Slide): Int {
    // An action moves the whole element, so there is nothing to hand over in
    // pieces however the build was dressed.
    if (kind == BuildKind.Action) return 1
    if (delivery == BuildDelivery.All) return 1

    val element: Element = slide.elementById(elementId) ?: return 1
    val pieces: Int = when (element) {
        is TextElement -> element.pieces(delivery).size
        is CodeElement -> if (delivery == BuildDelivery.ByLine) element.code.lineRanges().size else 1
        is TerminalElement -> if (delivery == BuildDelivery.ByLine) element.text.lineRanges().size else 1
        else -> 1
    }
    return maxOf(1, pieces)
}

/**
 * The character ranges [delivery] hands this element's text over in, in order.
 *
 * Ranges into [TextElement.text] rather than the substrings themselves, so a
 * renderer can style the part that hasn't arrived yet instead of setting a
 * different string every step. Whitespace between pieces belongs to neither: it
 * comes in with the piece ahead of it.
 */
fun TextElement.pieces(delivery: BuildDelivery): List<IntRange> = when (delivery) {
    BuildDelivery.All -> if (text.isEmpty()) emptyList() else listOf(0..text.lastIndex)
    BuildDelivery.ByLine -> text.lineRanges()
    BuildDelivery.ByParagraph -> text.lineRanges().filter { range ->
        (range.first..minOf(range.last, text.lastIndex)).any { !text[it].isWhitespace() }
    }

    BuildDelivery.ByWord -> text.wordRanges()
    BuildDelivery.ByCharacter -> text.indices.filter { !text[it].isWhitespace() }.map { it..it }
}

/**
 * Every line's range, blank ones included and the newlines themselves in none of
 * them. A blank line is an empty range, which is a piece that reveals nothing
 * and still takes its turn.
 */
private fun String.lineRanges(): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var start = 0
    for (index in indices) {
        if (this[index] != '\n') continue
        ranges += start..index - 1
        start = index + 1
    }
    ranges += start..lastIndex
    return ranges
}

/** Every run of non-whitespace, in order. */
private fun String.wordRanges(): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var start = -1
    for (index in indices) {
        val blank: Boolean = this[index].isWhitespace()
        if (!blank && start < 0) start = index
        if (blank && start >= 0) {
            ranges += start..index - 1
            start = -1
        }
    }
    if (start >= 0) ranges += start..lastIndex
    return ranges
}

/**
 * Where every build lands, resolved in order: the one function the rest of the
 * step model is written on.
 *
 * An [BuildTrigger.OnClick] build opens the next step and holds one step per
 * piece. A [BuildTrigger.WithPrevious] build joins the step the one before it
 * opened, and an [BuildTrigger.AfterPrevious] build the step the one before it
 * ended, waiting out that build's delay and duration before it starts. Both ride
 * their anchor's step whatever their delivery says: pieces cost clicks, and
 * those two triggers are exactly the ones that don't take one.
 */
fun Slide.buildTimeline(): List<BuildAt> {
    val timeline = mutableListOf<BuildAt>()
    for ((index, build) in builds.withIndex()) {
        val previous: BuildAt? = timeline.lastOrNull()
        timeline += when (build.trigger) {
            BuildTrigger.OnClick -> {
                val first: Int = (previous?.lastStep ?: 0) + 1
                val last: Int = first + build.pieceCount(this) - 1
                BuildAt(index, build, first, last, build.delayMs)
            }

            BuildTrigger.WithPrevious -> {
                val step: Int = previous?.firstStep ?: 0
                BuildAt(index, build, step, step, build.delayMs)
            }

            BuildTrigger.AfterPrevious -> {
                val step: Int = previous?.lastStep ?: 0
                val waited: Int =
                    if (previous == null) 0 else previous.delayMs + previous.build.durationMs
                BuildAt(index, build, step, step, waited + build.delayMs)
            }
        }
    }
    return timeline
}

/**
 * Step model (maps 1:1 onto CuP's stepCount):
 * step 0 shows everything without a build, and every step past it is one click.
 * A build delivered in pieces holds a step per piece, so a body handed over
 * paragraph by paragraph costs as many clicks as it has paragraphs.
 */
fun Slide.stepCount(): Int = 1 + (buildTimeline().maxOfOrNull { it.lastStep } ?: 0)

/**
 * The step at which each element with a build becomes visible.
 *
 * An element's *first* [BuildKind.In] build is the one that reveals it, and later
 * builds for the same element only change it. That matters now that more than one
 * build per element is ordinary: a code block brought in at step 1 and advanced at
 * steps 2 and 3 has to stay on screen throughout, not wait for its last build.
 *
 * An element with only [BuildKind.Out] builds is not in here at all: it opens with
 * the slide, and a caller reading this as "when does it appear" gets the same
 * answer it always did.
 */
fun Slide.buildSteps(): Map<String, Int> {
    val steps = mutableMapOf<String, Int>()
    for (at in buildTimeline()) {
        if (at.build.kind != BuildKind.In) continue
        if (at.build.elementId !in steps) steps[at.build.elementId] = at.firstStep
    }
    return steps
}

/**
 * The build that reveals [elementId] and where it lands, null when nothing brings
 * it in. The first [BuildKind.In] build for the element, per [buildSteps].
 */
fun Slide.entryBuildAt(elementId: String): BuildAt? = buildTimeline()
    .firstOrNull { it.build.elementId == elementId && it.build.kind == BuildKind.In }

/** [entryBuildAt]'s build alone: the effect a renderer plays the element in on. */
fun Slide.entryBuild(elementId: String): Build? = entryBuildAt(elementId)?.build

/** [entryBuildAt]'s mirror: the first build that takes [elementId] away, null when none does. */
fun Slide.exitBuildAt(elementId: String): BuildAt? = buildTimeline()
    .firstOrNull { it.build.elementId == elementId && it.build.kind == BuildKind.Out }

/** [exitBuildAt]'s build alone: the effect a renderer plays the element out on. */
fun Slide.exitBuild(elementId: String): Build? = exitBuildAt(elementId)?.build

/**
 * Whether [elementId] is on the slide at [step]: the last of its builds to have
 * landed says so, an [BuildKind.In] showing it and an [BuildKind.Out] taking it
 * away.
 *
 * An element none of whose builds has landed yet is off the slide when something
 * later brings it in, and on it when nothing does: an element with only exits was
 * there from the start, and one with no builds at all is always there.
 *
 * [BuildKind.Action] builds are not read here at all: an action animates an
 * element that is already on the slide, and says nothing about whether it is.
 */
fun Slide.isVisibleAt(elementId: String, step: Int): Boolean {
    val mine: List<BuildAt> = buildTimeline()
        .filter { it.build.elementId == elementId && it.build.kind != BuildKind.Action }
    val landed: BuildAt = mine.lastOrNull { it.firstStep <= step }
        ?: return mine.none { it.build.kind == BuildKind.In }
    return landed.build.kind == BuildKind.In
}

/**
 * Which of [elementId]'s own steps is showing at [step]: the [Build.elementStep]
 * of the last build for that element that carries one and lands at or before
 * [step].
 *
 * Null when no such build has played yet, which is both "the element has no step
 * builds at all" and "its first one is still ahead". A caller with a stepped
 * element reads that as step 0, since a stepped element shows its first state
 * from the moment it is visible; an element with no steps isn't stepped at all.
 */
fun Slide.elementStepAt(elementId: String, step: Int): Int? = buildTimeline()
    .lastOrNull { it.firstStep <= step && it.build.elementId == elementId && it.build.elementStep != null }
    ?.build
    ?.elementStep

/**
 * How much of [elementId] is out at [step], null when it shows whole.
 *
 * The element's latest landed [BuildKind.In] build that delivers in pieces, and
 * how many of them that build has handed over by [step]. A build that takes a
 * step per piece counts them off one click at a time; one riding another build's
 * step brings all of its pieces at once, having no clicks of its own to spend.
 */
fun Slide.pieceRevealAt(elementId: String, step: Int): PieceReveal? {
    val at: BuildAt = buildTimeline().lastOrNull {
        it.build.elementId == elementId &&
            it.build.kind == BuildKind.In &&
            it.build.delivery != BuildDelivery.All &&
            it.firstStep <= step
    } ?: return null

    val total: Int = at.build.pieceCount(this)
    val shown: Int =
        if (at.firstStep == at.lastStep) total
        else (step - at.firstStep + 1).coerceIn(1, total)
    return PieceReveal(at.build, shown, total)
}

/**
 * Every [BuildKind.Action] for [elementId] that has landed by [step], composed in
 * timeline order.
 *
 * Moves and rotations add and scales multiply, so a run of them reads as one
 * journey: a second move starts where the first ended. Opacity is absolute, so
 * the last one to land wins, and stays null while none has.
 *
 * A build whose kind is [BuildKind.Action] but that carries no [Build.action] is
 * skipped rather than guessed at.
 */
fun Slide.actionStateAt(elementId: String, step: Int): ActionState {
    var state = ActionState()
    for (at in buildTimeline()) {
        if (at.build.kind != BuildKind.Action || at.build.elementId != elementId) continue
        if (at.firstStep > step) continue
        val action: BuildAction = at.build.action ?: continue
        state = when (action.kind) {
            ActionKind.Move -> state.copy(dx = state.dx + action.dx, dy = state.dy + action.dy)
            ActionKind.Rotate -> state.copy(rotation = state.rotation + action.rotation)
            ActionKind.Scale -> state.copy(scale = state.scale * action.scale)
            ActionKind.Opacity -> state.copy(opacity = action.opacity)
        }
    }
    return state
}

/**
 * The action build [actionStateAt] last read, which is the one a renderer takes
 * its duration and delay from.
 *
 * Null only when [elementId] has no action builds at all. Walking backwards past
 * the first of them keeps that first build, so a state animating back to rest
 * takes as long as it took to leave it.
 */
fun Slide.actionBuildAt(elementId: String, step: Int): BuildAt? {
    val mine: List<BuildAt> = buildTimeline().filter {
        it.build.kind == BuildKind.Action && it.build.elementId == elementId
    }
    return mine.lastOrNull { it.firstStep <= step } ?: mine.firstOrNull()
}

/** [pieceRevealAt]'s count alone: how many pieces of [elementId] are out at [step]. */
fun Slide.piecesShownAt(elementId: String, step: Int): Int? = pieceRevealAt(elementId, step)?.shown

/**
 * The state [element] draws in at [step], or null when it has no steps to draw
 * and renders as the whole block. Out-of-range indices clamp rather than throw:
 * a build can outlive the step it pointed at.
 */
fun Slide.codeStepFor(element: CodeElement, step: Int): CodeStep? {
    if (element.steps.isEmpty()) return null
    val index: Int = (elementStepAt(element.id, step) ?: 0).coerceIn(element.steps.indices)
    return element.steps[index]
}

/**
 * The state [element] draws in at [step], or null when it has no steps and
 * renders whole. Clamps like [codeStepFor], and for the same reason: a build can
 * outlive the step it pointed at.
 */
fun Slide.diagramStepFor(element: DiagramElement, step: Int): DiagramStep? {
    if (element.steps.isEmpty()) return null
    val index: Int = (elementStepAt(element.id, step) ?: 0).coerceIn(element.steps.indices)
    return element.steps[index]
}
