package io.github.xxfast.cupboard.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import io.github.xxfast.cupboard.canvas.SlideView
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.layoutOf
import io.github.xxfast.cupboard.document.presentationNumbers
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
 * [layout] is held by the caller rather than here, for the same reason
 * [position] is: this draws what it is told to. [onLayoutChange] and
 * [onResetTimer] default to nothing so a host that gives the presenter no say
 * over either still gets a display.
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
    onLayoutChange: (PresenterLayout) -> Unit = {},
    onResetTimer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val order: List<Slide> = remember(document) { document.playOrder() }
    val numbers: Map<String, Int?> = remember(document) {
        document.slides.zip(document.presentationNumbers())
            .associate { (slide, number) -> slide.id to number }
    }

    val current: Slide? = order.getOrNull(position.slideIndex)
    val next: PlayPosition? = remember(document, position) { document.nextPosition(position) }
    val nextSlide: Slide? = next?.let { order.getOrNull(it.slideIndex) }

    Column(modifier.fillMaxSize().background(PresenterBackground)) {
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

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (layout) {
                PresenterLayout.CurrentAndNext -> {
                    SlidePane(
                        label = "CURRENT",
                        document = document,
                        slide = current,
                        step = position.step,
                        numbers = numbers,
                        modifier = Modifier.weight(1.7f).fillMaxHeight(),
                    )
                    SlidePane(
                        label = "NEXT",
                        document = document,
                        slide = nextSlide,
                        step = next?.step ?: 0,
                        numbers = numbers,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }

                PresenterLayout.CurrentAndNotes -> {
                    SlidePane(
                        label = "CURRENT",
                        document = document,
                        slide = current,
                        step = position.step,
                        numbers = numbers,
                        modifier = Modifier.weight(1.7f).fillMaxHeight(),
                    )
                    NotesPane(
                        slide = current,
                        onNotesChange = onNotesChange,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }

                PresenterLayout.NextAndNotes -> {
                    SlidePane(
                        label = "NEXT",
                        document = document,
                        slide = nextSlide,
                        step = next?.step ?: 0,
                        numbers = numbers,
                        modifier = Modifier.weight(1.7f).fillMaxHeight(),
                    )
                    NotesPane(
                        slide = current,
                        onNotesChange = onNotesChange,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }

                PresenterLayout.NotesOnly -> NotesPane(
                    slide = current,
                    onNotesChange = onNotesChange,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
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
 */
@Composable
private fun SlidePane(
    label: String,
    document: Document,
    slide: Slide?,
    step: Int,
    numbers: Map<String, Int?>,
    modifier: Modifier = Modifier,
) {
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

            SlideView(
                slide = slide,
                layout = document.layoutOf(slide),
                step = step,
                number = numbers[slide.id],
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

/**
 * The notes for the slide on screen, editable while the show runs, in the notes
 * strip's own typography.
 *
 * The field keeps its own text and streams it out rather than reading the
 * document back: the host writes an edit into a new [Document] on every
 * keystroke, and rendering that back into the field would fight the cursor. The
 * local copy is re-seeded when the slide changes, which is the only time the
 * document is the better authority.
 */
@Composable
private fun NotesPane(
    slide: Slide?,
    onNotesChange: (slideId: String, notes: String) -> Unit,
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
        Text(
            text = "SPEAKER NOTES",
            color = PresenterFaint,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )

        if (slide == null) return@Column

        var text: String by remember(slide.id) { mutableStateOf(slide.notes) }

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
                fontSize = 13.5.sp,
                lineHeight = 20.25.sp,
            ),
            cursorBrush = SolidColor(PresenterAccent),
            modifier = Modifier.fillMaxSize(),
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
