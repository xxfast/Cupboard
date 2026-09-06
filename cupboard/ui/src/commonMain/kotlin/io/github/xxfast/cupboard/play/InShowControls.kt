package io.github.xxfast.cupboard.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.canvas.SlideThumbnail
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.layoutOf
import kotlinx.coroutines.flow.StateFlow

/**
 * What the show has open on top of itself: the digits typed so far, the slide
 * switcher, the shortcut sheet, and whether the laser is on.
 *
 * One holder rather than four states so the player's key handler can close
 * whatever is open in one call, which is all Escape ever wants to do.
 */
@Stable
class ShowOverlays {
    /** The digits typed since the last Enter, Escape, or two second lull. */
    var jump: String by mutableStateOf("")

    /** The play index the switcher is highlighting, null when it is closed. */
    var switcher: Int? by mutableStateOf(null)

    var shortcuts: Boolean by mutableStateOf(false)

    var laser: Boolean by mutableStateOf(false)

    /** Whether anything is open that Escape should close instead of leaving the show. */
    val isOpen: Boolean get() = switcher != null || shortcuts || jump.isNotEmpty()

    /** Everything shut, the laser left alone: it is a tool, not an overlay. */
    fun closeAll() {
        jump = ""
        switcher = null
        shortcuts = false
    }
}

/**
 * The play index [buffer] names, or null if it names nothing in a deck of
 * [slideCount] slides.
 *
 * The number typed is the one the audience counts, so 1 is the first slide and
 * the index handed back is one less. Anything that is not a slide of this show,
 * 0 and 12 in a deck of nine among them, is nothing to jump to rather than a
 * jump to the nearest end: a mistyped number should leave the show where it is.
 */
internal fun jumpTarget(buffer: String, slideCount: Int): Int? {
    val number: Int = buffer.toIntOrNull() ?: return null
    if (number < 1 || number > slideCount) return null

    return number - 1
}

/**
 * The switcher's highlight moved [by] places, kept inside a deck of [slideCount]
 * slides.
 *
 * Clamped rather than wrapped: the strip is a row the eye is following, and an
 * arrow at the last slide that lands back on the first loses it.
 */
internal fun movedHighlight(highlight: Int, by: Int, slideCount: Int): Int {
    if (slideCount <= 0) return 0

    return (highlight + by).coerceIn(0, slideCount - 1)
}

/**
 * The digits typed so far, bottom right, with a hint of the Enter that would go
 * there once they name a slide.
 */
@Composable
fun JumpBadge(buffer: String, slideCount: Int, modifier: Modifier = Modifier) {
    val ready: Boolean = jumpTarget(buffer, slideCount) != null

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PresenterPanel.copy(alpha = 0.94f))
            .border(1.dp, if (ready) PresenterAccent else PresenterBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = if (ready) "$buffer ↵" else buffer,
            color = if (ready) PresenterText else PresenterDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
        )
    }
}

/**
 * Every slide of the show as a strip along the bottom, for finding the one to
 * jump to without walking the deck to it.
 *
 * [current] is where the show is and [highlight] is where the arrows have got
 * to; they start as the same slide and part company as soon as a key is pressed,
 * because nothing moves until [onPick] is called.
 */
@Composable
fun SlideSwitcher(
    document: Document,
    slides: List<Slide>,
    current: Int,
    highlight: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll: LazyListState = rememberLazyListState()

    // Follow the arrows: a highlight that has walked off the end of the strip is
    // a highlight the presenter cannot see.
    LaunchedEffect(highlight) {
        if (highlight in slides.indices) scroll.animateScrollToItem(highlight)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PresenterBackground.copy(alpha = 0.95f))
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LazyRow(
            state = scroll,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(slides) { index, slide ->
                SwitcherSlide(
                    document = document,
                    slide = slide,
                    number = index + 1,
                    current = index == current,
                    highlighted = index == highlight,
                    onClick = { onPick(index) },
                )
            }
        }

        Text(
            text = "← → to move · Enter to jump · S or Esc to close",
            color = PresenterFaint,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

/** One slide of the strip, ringed if it is where the show is or where the arrows are. */
@Composable
private fun SwitcherSlide(
    document: Document,
    slide: Slide,
    number: Int,
    current: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val ring: Color = when {
        highlighted -> PresenterAccent
        current -> PresenterAccent.copy(alpha = 0.4f)
        else -> Color.Transparent
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(ring)
                .clickable(onClick = onClick)
                .padding(3.dp),
        ) {
            SlideThumbnail(
                slide = slide,
                width = 180.dp,
                layout = document.layoutOf(slide),
                background = document.background,
                slideWidth = document.slideWidth,
                slideHeight = document.slideHeight,
            )
        }

        Text(
            text = "$number",
            color = if (highlighted) PresenterText else PresenterDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

/** Every key the show answers to, under what it does. */
private val Shortcuts: List<Pair<String, String>> = listOf(
    "→  ↓  Space  Enter" to "Next step",
    "←  ↑  Backspace" to "Previous step",
    "Shift + →  ←" to "Next / previous slide",
    "Home  End" to "First / last slide",
    "1…9 then Enter" to "Jump to slide",
    "S  Tab" to "Slide switcher",
    "P" to "Laser pointer",
    "X" to "Swap displays",
    "?" to "This list",
    "Esc" to "Close, then exit the show",
)

/**
 * The keys, centred over the slide, for the presenter who has forgotten one.
 *
 * Listed in the order a talk uses them rather than alphabetically: stepping,
 * then jumping, then the tools, then the way out.
 */
@Composable
fun ShortcutSheet(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .widthIn(max = 440.dp)
            .clip(shape)
            .background(PresenterPanel.copy(alpha = 0.97f))
            .border(1.dp, PresenterBorder, shape)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "KEYBOARD",
            color = PresenterFaint,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )

        for ((keys, action) in Shortcuts) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = keys,
                    color = PresenterText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(text = action, color = PresenterDim, fontSize = 12.5.sp)
            }
        }

        Text(
            text = "X is the host window's, not the show's.",
            color = PresenterFaint,
            fontSize = 11.sp,
        )
    }
}

/**
 * A dot on the slide under the pointer, for pointing at things with.
 *
 * This is the laser, not a hidden cursor: shared code has no way to take the
 * system pointer away. Compose's common [androidx.compose.ui.input.pointer.PointerIcon]
 * offers no empty icon, and the one platform that could build one (`java.awt.Cursor`
 * on the JVM) is a dependency `:cupboard:ui` cannot take, because the same code
 * compiles for `macosArm64`. So `P` lights something up rather than turning
 * something off.
 *
 * The position arrives as a [StateFlow] rather than snapshot state written in
 * the pointer handler: those writes can silently skip a repaint on desktop, and
 * collecting the flow puts the redraw back on composition's own path.
 */
@Composable
fun LaserDot(pointer: StateFlow<Offset?>, modifier: Modifier = Modifier) {
    val at: State<Offset?> = pointer.collectAsState()

    Canvas(modifier.fillMaxSize()) {
        val centre: Offset = at.value ?: return@Canvas

        drawCircle(PresenterAccent.copy(alpha = 0.18f), radius = 16.dp.toPx(), center = centre)
        drawCircle(PresenterAccent.copy(alpha = 0.65f), radius = 7.dp.toPx(), center = centre)
    }
}
