package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Frame
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How far into the slide the canvas is zoomed, and where it is looking: Keynote's
 * zoom pill and scroll bars, as editor view state.
 *
 * It rides the loop like [EditorState.marquee] does: the canvas reports its size
 * and its scroll deltas, and draws from what comes back, never from a copy of its
 * own. Every function here takes the deck's slide size, because the viewport is
 * the same whichever deck it looks at and the deck is what it is measured against.
 */
@Serializable
data class CanvasViewport(
    /** 0 is Fit Slide, which is live; otherwise a percent of native slide size. */
    val zoomPercent: Int = 0,
    /**
     * Where the view's centre sits relative to the slide's centre, in document
     * units. Zoom-independent, so a zoom about the view centre leaves it alone,
     * which is Keynote's menu-zoom behaviour for free.
     */
    val panX: Float = 0f,
    val panY: Float = 0f,
    /**
     * The canvas's view size in dp, reported by the canvas; 0 until it has been
     * measured. Transient: a restored session has no window yet.
     */
    @Transient val viewWidth: Float = 0f,
    @Transient val viewHeight: Float = 0f,
) {
    private val isMeasured: Boolean get() = viewWidth > 0f && viewHeight > 0f

    /**
     * Screen dp per document unit. Null when Fit has no view to fit yet, which a
     * canvas reads as "fit yourself", its default before any of this existed.
     */
    fun scale(slideWidth: Float, slideHeight: Float): Float? {
        if (zoomPercent != 0) return zoomPercent / 100f
        if (!isMeasured) return null
        return min(viewWidth / slideWidth, viewHeight / slideHeight)
    }

    /** What the zoom pill shows: Fit as the percent it works out to, not the word. */
    fun effectivePercent(slideWidth: Float, slideHeight: Float): Int? =
        scale(slideWidth, slideHeight)?.let { scale -> (scale * 100).roundToInt() }

    /**
     * The pan coerced so the view never shows past the slide's edges: an axis
     * where the slide fits the view stays centred, and one where it doesn't
     * scrolls exactly as far as the slide goes, with no over-scroll margin.
     */
    fun clamped(slideWidth: Float, slideHeight: Float): CanvasViewport {
        val scale: Float = scale(slideWidth, slideHeight) ?: return copy(panX = 0f, panY = 0f)
        val maxX: Float = max(0f, (slideWidth - viewWidth / scale) / 2)
        val maxY: Float = max(0f, (slideHeight - viewHeight / scale) / 2)
        return copy(panX = panX.coerceIn(-maxX, maxX), panY = panY.coerceIn(-maxY, maxY))
    }

    /**
     * A fixed zoom about the view centre, 0 for Fit Slide. Fit recentres; any
     * other percent keeps the pan the view is showing, coerced into range.
     */
    fun zoomedTo(percent: Int, slideWidth: Float, slideHeight: Float): CanvasViewport {
        if (percent == 0) return copy(zoomPercent = 0, panX = 0f, panY = 0f)
        return clamped(slideWidth, slideHeight)
            .copy(zoomPercent = percent.coerceIn(MinPercent, MaxPercent))
    }

    /** The next [Steps] entry up from the effective percent, so Fit at 32% goes to 50. */
    fun zoomedIn(slideWidth: Float, slideHeight: Float): CanvasViewport {
        val current: Int = effectivePercent(slideWidth, slideHeight) ?: 100
        val next: Int = Steps.firstOrNull { it > current } ?: return this
        return zoomedTo(next, slideWidth, slideHeight)
    }

    /** [zoomedIn]'s mirror: the last [Steps] entry below the effective percent. */
    fun zoomedOut(slideWidth: Float, slideHeight: Float): CanvasViewport {
        val current: Int = effectivePercent(slideWidth, slideHeight) ?: 100
        val previous: Int = Steps.lastOrNull { it < current } ?: return this
        return zoomedTo(previous, slideWidth, slideHeight)
    }

    /**
     * A pinch by [factor], keeping the document point under the pointer
     * ([anchorX], [anchorY], relative to the slide centre) under it. Starts from
     * the pan on screen, not the stored one, so the anchor is the point the user
     * actually sees there.
     */
    fun zoomedBy(
        factor: Float,
        anchorX: Float,
        anchorY: Float,
        slideWidth: Float,
        slideHeight: Float,
    ): CanvasViewport {
        if (!isMeasured) return this
        val shown: CanvasViewport = clamped(slideWidth, slideHeight)
        val oldScale: Float = shown.scale(slideWidth, slideHeight) ?: return this
        val oldPercent: Int = (oldScale * 100).roundToInt()
        val percent: Int = (oldPercent * factor).roundToInt().coerceIn(MinPercent, MaxPercent)
        val ratio: Float = oldScale / (percent / 100f)
        return shown.copy(
            zoomPercent = percent,
            panX = anchorX - (anchorX - shown.panX) * ratio,
            panY = anchorY - (anchorY - shown.panY) * ratio,
        )
    }

    /**
     * Zooms [frame] (document units) to fill [FitFill] of the view, centred on it:
     * Zoom to Selection and Zoom to Fit Content. The percent is whatever that
     * works out to, not a ladder step.
     */
    fun zoomedTo(frame: Frame, slideWidth: Float, slideHeight: Float): CanvasViewport {
        if (!isMeasured || frame.width <= 0f || frame.height <= 0f) return this
        val fill: Float = min(viewWidth / frame.width, viewHeight / frame.height) * 100 * FitFill
        return copy(
            zoomPercent = floor(fill).toInt().coerceIn(MinPercent, MaxPercent),
            panX = frame.centerX - slideWidth / 2,
            panY = frame.centerY - slideHeight / 2,
        )
    }

    /**
     * A scroll by ([dx], [dy]) document units. Clamped before as well as after:
     * a pan left over-range by a zoom change would otherwise eat the first part
     * of every scroll back, a dead zone where nothing moves.
     */
    fun panned(dx: Float, dy: Float, slideWidth: Float, slideHeight: Float): CanvasViewport {
        val shown: CanvasViewport = clamped(slideWidth, slideHeight)
        return shown
            .copy(panX = shown.panX + dx, panY = shown.panY + dy)
            .clamped(slideWidth, slideHeight)
    }

    companion object {
        val Steps: List<Int> = listOf(10, 25, 50, 75, 100, 125, 150, 200, 300, 400)
        const val MinPercent: Int = 10
        const val MaxPercent: Int = 400

        /** How much of the view a Zoom to Selection / Fit Content target fills. */
        const val FitFill: Float = 0.9f
    }
}
