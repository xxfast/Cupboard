package io.github.xxfast.cupboard.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import io.github.xxfast.cupboard.editor.LocalResizeCursors
import io.github.xxfast.cupboard.editor.ResizeDirection
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.canvas.LocalMediaPlayer
import io.github.xxfast.cupboard.canvas.MediaPicturesOf
import io.github.xxfast.cupboard.canvas.MediaPlayerHost
import io.github.xxfast.cupboard.canvas.NoMediaPlayer
import io.github.xxfast.cupboard.canvas.ProvideDeckFonts
import io.github.xxfast.cupboard.canvas.SlideView
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideFields
import io.github.xxfast.cupboard.document.layoutOf
import io.github.xxfast.cupboard.document.slideFields
import io.github.xxfast.cupboard.document.stepCount

/**
 * How the presenter display arranges itself: Keynote's customisable presenter
 * display reduced to the four arrangements that earn their keep.
 *
 * Every one of them keeps the top bar, so the timer and the counter are never
 * the thing traded away.
 */
enum class PresenterLayout { CurrentAndNext, CurrentAndNotes, NextAndNotes, NotesOnly }

/**
 * What the presenter sees while the audience sees the show: where the deck is,
 * how long it has been running, what is coming, and the notes for the slide on
 * screen, editable mid-show.
 *
 * Pure render off [position]: it neither owns the show's position nor drives it,
 * so the same display serves a CuP-backed show (through `PresenterView`) and any
 * other host that can say where it is. [onNext] and [onPrevious] go back to
 * whatever does drive it.
 *
 * [elapsedMs] and [clock] are the host's to keep, because a wall clock needs a
 * platform date formatter and this module has none.
 *
 * [onNotesChange] streams every keystroke, keyed by slide id rather than by
 * index: the host folds it into the document, and an edit that lands while the
 * deck is being reordered still finds its slide.
 *
 * [layout], [split] and [notesSize] are held by the caller rather than here,
 * for the same reason [position] is: this draws what it is told to. [split] is
 * the left pane's share of a two-pane arrangement, dragged by the divider
 * between them. [notesSize] is the notes' type size in sp, stepped along
 * [PresenterNotesSizes] by the buttons over them. [onLayoutChange],
 * [onSplitChange], [onNotesSizeChange] and [onResetTimer] default to nothing so
 * a host that gives the presenter no say over them still gets a display.
 *
 * [LocalMediaPlayer] around the display is the show's own player. The current
 * slide draws the pictures of the movies it is playing, and nothing here starts
 * or stops one: the show is what plays, and the next slide's sound must not
 * start a slide early.
 */
@Composable
fun PresenterDisplay(
    document: Document,
    position: PlayPosition,
    layout: PresenterLayout,
    elapsedMs: Long,
    clock: String,
    onNotesChange: (slideId: String, notes: String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    split: Float = PresenterDefaultSplit,
    notesSize: Float = PresenterDefaultNotesSize,
    onLayoutChange: (PresenterLayout) -> Unit = {},
    onSplitChange: (Float) -> Unit = {},
    onNotesSizeChange: (Float) -> Unit = {},
    onResetTimer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // The deck's own faces, out of the store the host provided around the lectern.
    ProvideDeckFonts(document.fonts) {
        Display(
            document, position, layout, elapsedMs, clock, onNotesChange,
            onNext, onPrevious, split, notesSize, onLayoutChange, onSplitChange, onNotesSizeChange,
            onResetTimer, modifier,
        )
    }
}

@Composable
private fun Display(
    document: Document,
    position: PlayPosition,
    layout: PresenterLayout,
    elapsedMs: Long,
    clock: String,
    onNotesChange: (slideId: String, notes: String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    split: Float,
    notesSize: Float,
    onLayoutChange: (PresenterLayout) -> Unit,
    onSplitChange: (Float) -> Unit,
    onNotesSizeChange: (Float) -> Unit,
    onResetTimer: () -> Unit,
    modifier: Modifier,
) {
    val order: List<Slide> = remember(document) { document.playOrder() }
    val fields: Map<String, SlideFields> = remember(document) {
        document.slides.indices.associate { document.slides[it].id to document.slideFields(it) }
    }

    val current: Slide? = order.getOrNull(position.slideIndex)
    val next: PlayPosition? = remember(document, position) { document.nextPosition(position) }
    val nextSlide: Slide? = next?.let { order.getOrNull(it.slideIndex) }

    val focus: FocusManager = LocalFocusManager.current

    // A click anywhere the notes aren't puts their caret down, and with it the
    // keys go back to driving the show.
    Column(
        modifier
            .fillMaxSize()
            .background(PresenterBackground)
            .pointerInput(focus) { detectTapGestures { focus.clearFocus() } },
    ) {
        PresenterTopBar(
            position = position,
            slideCount = order.size,
            stepCount = current?.stepCount() ?: 1,
            layout = layout,
            elapsedMs = elapsedMs,
            clock = clock,
            onLayoutChange = onLayoutChange,
            onResetTimer = onResetTimer,
            onNext = onNext,
            onPrevious = onPrevious,
        )

        val panes: Modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)

        when (layout) {
            PresenterLayout.CurrentAndNext -> SplitPanes(split, onSplitChange, panes) { left, right ->
                SlidePane(
                    label = "CURRENT",
                    live = true,
                    document = document,
                    slide = current,
                    step = position.step,
                    fields = fields,
                    modifier = left,
                )
                SlidePane(
                    label = "NEXT",
                    document = document,
                    slide = nextSlide,
                    step = next?.step ?: 0,
                    fields = fields,
                    modifier = right,
                )
            }

            PresenterLayout.CurrentAndNotes -> SplitPanes(split, onSplitChange, panes) { left, right ->
                SlidePane(
                    label = "CURRENT",
                    live = true,
                    document = document,
                    slide = current,
                    step = position.step,
                    fields = fields,
                    modifier = left,
                )
                NotesPane(current, notesSize, onNotesChange, onNotesSizeChange, modifier = right)
            }

            PresenterLayout.NextAndNotes -> SplitPanes(split, onSplitChange, panes) { left, right ->
                SlidePane(
                    label = "NEXT",
                    document = document,
                    slide = nextSlide,
                    step = next?.step ?: 0,
                    fields = fields,
                    modifier = left,
                )
                NotesPane(current, notesSize, onNotesChange, onNotesSizeChange, modifier = right)
            }

            PresenterLayout.NotesOnly -> NotesPane(
                current, notesSize, onNotesChange, onNotesSizeChange, modifier = panes,
            )
        }
    }
}

/**
 * The sizes the notes step through, in sp: small enough to fit a long script,
 * big enough to read from a step back off the lectern.
 */
val PresenterNotesSizes: List<Float> = listOf(11f, 13.5f, 16f, 19f, 23f, 28f, 34f, 40f)

/** The notes' size before anyone changes it. */
const val PresenterDefaultNotesSize: Float = 13.5f

/** The next size up the ladder from [size], or [size] at the top. */
internal fun biggerNotes(size: Float): Float = PresenterNotesSizes.firstOrNull { it > size } ?: size

/** The next size down the ladder from [size], or [size] at the bottom. */
internal fun smallerNotes(size: Float): Float = PresenterNotesSizes.lastOrNull { it < size } ?: size

/** The left pane's share of the room, before anyone drags the divider. */
const val PresenterDefaultSplit: Float = 1.7f / 2.7f

/** How far the divider goes either way: neither pane is ever dragged out of sight. */
private const val SplitMin: Float = 0.2f
private const val SplitMax: Float = 0.8f

private val SplitGrip = 16.dp

/**
 * Two panes either side of a divider the presenter drags to trade one for the
 * other. [split] is the left pane's share of the width either side of the grip.
 *
 * What the drag shows comes back as [split]: the gesture only does the sums,
 * from where the divider stood when it began.
 */
@Composable
private fun SplitPanes(
    split: Float,
    onSplitChange: (Float) -> Unit,
    modifier: Modifier,
    content: @Composable RowScope.(left: Modifier, right: Modifier) -> Unit,
) {
    val current: State<Float> = rememberUpdatedState(split)
    val grip = remember { MutableInteractionSource() }
    val hovered: Boolean by grip.collectIsHoveredAsState()

    BoxWithConstraints(modifier) {
        val room: Float = constraints.maxWidth - with(LocalDensity.current) { SplitGrip.toPx() }

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(SplitGrip)) {
            content(
                Modifier.weight(split).fillMaxHeight(),
                Modifier.weight(1f - split).fillMaxHeight(),
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset((room * split).roundToInt(), 0) }
                .width(SplitGrip)
                .fillMaxHeight()
                // Asked for null off the grip rather than left out, so a shell
                // that sets the cursor on composition puts the arrow back.
                .then(LocalResizeCursors.current?.cursor(ResizeDirection.Horizontal.takeIf { hovered }) ?: Modifier)
                .hoverable(grip)
                .pointerInput(room) {
                    var start = 0f
                    var dragged = 0f

                    detectHorizontalDragGestures(
                        onDragStart = {
                            start = current.value
                            dragged = 0f
                        },
                        onDragCancel = { onSplitChange(start) },
                    ) { _, delta ->
                        dragged += delta
                        onSplitChange((start + dragged / room).coerceIn(SplitMin, SplitMax))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (hovered) PresenterDim else PresenterBorder),
            )
        }
    }
}

/** Where the deck is, how long it has been running, and the manual clicks. */
@Composable
private fun PresenterTopBar(
    position: PlayPosition,
    slideCount: Int,
    stepCount: Int,
    layout: PresenterLayout,
    elapsedMs: Long,
    clock: String,
    onLayoutChange: (PresenterLayout) -> Unit,
    onResetTimer: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "SLIDE ${position.slideIndex + 1} / $slideCount " +
                "· step ${position.step + 1} / $stepCount",
            color = PresenterFaint,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )

        LayoutPicker(layout = layout, onLayoutChange = onLayoutChange)

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = elapsed(elapsedMs),
                    color = PresenterText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 28.sp,
                )
                if (clock.isNotEmpty()) Text(
                    text = clock,
                    color = PresenterDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                // Plain text rather than a filled button: it sits against the
                // clock it resets, and it is not one of the two things the
                // presenter reaches for mid-sentence.
                Text(
                    text = "Reset Timer",
                    color = PresenterDim,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onResetTimer)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            // Hosts that own the keyboard drive the show with it; these are for
            // the ones that don't, and for a pointer at the lectern.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresenterButton("Previous", onPrevious)
                PresenterButton("Next", onNext)
            }
        }
    }
}

/** Each arrangement under the name a presenter would call it. */
private val LayoutLabels: List<Pair<PresenterLayout, String>> = listOf(
    PresenterLayout.CurrentAndNext to "Current + Next",
    PresenterLayout.CurrentAndNotes to "Current + Notes",
    PresenterLayout.NextAndNotes to "Next + Notes",
    PresenterLayout.NotesOnly to "Notes",
)

/**
 * The four arrangements as one segmented control, in the top bar because that is
 * the one strip every arrangement keeps.
 *
 * Labelled rather than drawn as pane diagrams: this is read once, in a hurry,
 * from a lectern, and words survive that better than four tiny glyphs.
 */
@Composable
private fun LayoutPicker(layout: PresenterLayout, onLayoutChange: (PresenterLayout) -> Unit) {
    val shape = RoundedCornerShape(6.dp)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(PresenterPanel)
            .border(1.dp, PresenterBorder, shape)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((option, label) in LayoutLabels) {
            val selected: Boolean = option == layout

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (selected) PresenterControl else Color.Transparent)
                    .clickable { onLayoutChange(option) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = label,
                    color = if (selected) PresenterText else PresenterDim,
                    fontSize = 11.5.sp,
                )
            }
        }
    }
}

/**
 * One slide of the show under its label, fitted to the pane whichever way round
 * the pane happens to be: the deck's aspect is the deck's, and letterboxing it
 * is the only honest way to show what the audience is getting.
 *
 * A null [slide] is the end of the deck, which only the "NEXT" pane reaches.
 *
 * A [live] pane is the slide the audience is looking at, so its movies show what
 * the show's are showing. Any other pane draws posters.
 */
@Composable
private fun SlidePane(
    label: String,
    document: Document,
    slide: Slide?,
    step: Int,
    fields: Map<String, SlideFields>,
    modifier: Modifier = Modifier,
    live: Boolean = false,
) {
    val show: MediaPlayerHost = LocalMediaPlayer.current
    val media: MediaPlayerHost = remember(show, live) {
        if (live) MediaPicturesOf(show) else NoMediaPlayer
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            color = PresenterFaint,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (slide == null) {
                Text(text = "End of show", color = PresenterDim, fontSize = 15.sp)
                return@BoxWithConstraints
            }

            val ratio: Float = document.slideWidth / document.slideHeight
            val shape = RoundedCornerShape(6.dp)

            CompositionLocalProvider(LocalMediaPlayer provides media) {
                SlideView(
                    slide = slide,
                    layout = document.layoutOf(slide),
                    step = step,
                    fields = fields[slide.id],
                    background = document.background,
                    slideWidth = document.slideWidth,
                    slideHeight = document.slideHeight,
                    modifier = Modifier
                        .then(
                            // Fit to whichever edge runs out first, so the slide
                            // never spills out of the pane it was given.
                            if (maxWidth / maxHeight <= ratio) Modifier.fillMaxWidth()
                            else Modifier.fillMaxHeight(),
                        )
                        .aspectRatio(ratio)
                        .clip(shape)
                        .border(1.dp, PresenterBorder, shape),
                )
            }
        }
    }
}

/**
 * The notes for the slide on screen, editable while the show runs, in the notes
 * strip's own typography.
 *
 * The field keeps its own text and streams it out rather than reading the
 * document back: the host writes an edit into a new [Document] on every
 * keystroke, and rendering that back into the field would fight the cursor. The
 * local copy is re-seeded when the slide changes, which is the only time the
 * document is the better authority.
 *
 * Editing takes the keyboard off the show, so Escape, or a click outside the
 * field, hands it back.
 */
@Composable
private fun NotesPane(
    slide: Slide?,
    size: Float,
    onNotesChange: (slideId: String, notes: String) -> Unit,
    onSizeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(PresenterPanel)
            .border(1.dp, PresenterBorder, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "SPEAKER NOTES",
                color = PresenterFaint,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.weight(1f),
            )
            // Keynote's pair: a small A and a big one, each greyed at its end
            // of the ladder.
            NotesSizeButton(glyph = 10.sp, enabled = smallerNotes(size) != size) {
                onSizeChange(smallerNotes(size))
            }
            NotesSizeButton(glyph = 15.sp, enabled = biggerNotes(size) != size) {
                onSizeChange(biggerNotes(size))
            }
        }

        if (slide == null) return@Column

        var text: String by remember(slide.id) { mutableStateOf(slide.notes) }
        val focus: FocusManager = LocalFocusManager.current

        BasicTextField(
            value = text,
            onValueChange = { edited ->
                text = edited
                onNotesChange(slide.id, edited)
            },
            // Full-contrast ink rather than the strip's dim: this one is typed
            // into, in a dark room, at arm's length.
            textStyle = TextStyle(
                color = PresenterText,
                fontSize = size.sp,
                lineHeight = (size * 1.5f).sp,
            ),
            cursorBrush = SolidColor(PresenterAccent),
            // While the caret is in here the arrows and space are the field's,
            // so Escape is the way out of it. Taken before the show's own
            // handler, which would end the show instead.
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || event.key != Key.Escape) return@onPreviewKeyEvent false
                    focus.clearFocus()
                    true
                },
        )
    }
}

/** One of the notes' size buttons: an A at [glyph], which says which way it goes. */
@Composable
private fun NotesSizeButton(glyph: TextUnit, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "A",
            color = if (enabled) PresenterText else PresenterFaint,
            fontSize = glyph,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PresenterButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PresenterControl)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text = label, color = PresenterText, fontSize = 12.5.sp)
    }
}

/** `h:mm:ss` off the wall of time the show has been running. */
internal fun elapsed(elapsedMs: Long): String {
    val seconds: Long = (elapsedMs / 1000).coerceAtLeast(0)
    val minutes: Long = (seconds / 60) % 60

    return "${seconds / 3600}:${minutes.padded()}:${(seconds % 60).padded()}"
}

private fun Long.padded(): String = toString().padStart(2, '0')
