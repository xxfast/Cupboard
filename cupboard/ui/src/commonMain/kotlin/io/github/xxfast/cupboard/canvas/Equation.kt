package io.github.xxfast.cupboard.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.xxfast.cupboard.document.EquationElement
import io.github.xxfast.cupboard.document.MathNode
import io.github.xxfast.cupboard.document.parseMath
import kotlin.math.max
import kotlin.math.min

/**
 * A laid-out piece of an equation: how much room it takes either side of its
 * baseline, and how to draw it once something has decided where that baseline is.
 *
 * The drawing is a closure rather than a tree walk because the measuring is the
 * expensive half: every glyph in here is already measured, so putting the box
 * somewhere else costs a translation rather than a re-layout. That is what lets
 * the fit-to-frame scale be decided after the whole equation is laid out.
 *
 * Everything is in pixels. Ems don't survive nesting: a script inside a fraction
 * inside a script has three sizes behind it, and the only one every box agrees on
 * is the one the canvas draws in.
 */
internal class MathBox(
    val width: Float,
    val ascent: Float,
    val descent: Float,
    val draw: DrawScope.(x: Float, baselineY: Float) -> Unit,
)

/** The full height of the box, which is all a fit cares about. */
internal val MathBox.height: Float get() = ascent + descent

/**
 * How big everything at this level of the equation is set, in the TeX sense.
 *
 * [Display] and [Text] are the same size and differ in one thing only: where a
 * big operator puts its limits. [Script] and [ScriptScript] are the two shrinks,
 * and nothing shrinks past the second, which is what stops a tower of
 * superscripts from disappearing.
 */
internal enum class MathStyle { Display, Text, Script, ScriptScript }

/** What each style is set at, against the equation's own size. */
private const val ScriptScale: Float = 0.7f
private const val ScriptScriptScale: Float = 0.5f

/** A fraction's halves in display style: set down a little, but not to script size. */
private const val FractionScale: Float = 0.85f

/** How far a script sits off the baseline, in ems of the base's size. */
private const val SuperscriptShift: Float = 0.45f
private const val SubscriptShift: Float = 0.2f

/** A drawn rule: a fraction's bar, an accent's line, a radical's stroke. */
private const val RuleThickness: Float = 0.05f

/** Air above and below a fraction's bar, and either side of the stack. */
private const val FractionGap: Float = 0.15f
private const val FractionPad: Float = 0.1f

/** The math axis, where a fraction's bar and a big operator centre themselves. */
private const val AxisFraction: Float = 0.5f

/** The radical: air over the body, the hook's width, and the overline's overhang. */
private const val RootClearance: Float = 0.05f
private const val RootHook: Float = 0.55f
private const val RootTail: Float = 0.12f

/** A sum or an integral is set larger than the terms it gathers. */
private const val BigOperatorScale: Float = 1.4f

/** Between a big operator and a limit stacked over or under it. */
private const val LimitGap: Float = 0.15f

/** The air an operator gets either side of itself, and the same in a script. */
private const val OperatorSpace: Float = 0.22f
private const val ScriptOperatorSpace: Float = 0.12f

/** After a function name, so `\sin x` isn't one word. */
private const val ThinSpace: Float = 0.17f

/** An accent's clearance over its base, and how tall the mark itself is drawn. */
private const val AccentGap: Float = 0.06f
private const val AccentHeight: Float = 0.14f

/** The smallest width an accent draws over, so a mark over `x` isn't a speck. */
private const val AccentMinWidth: Float = 0.4f

/** A delimiter's overhang past the body, and the most it will ever be stretched. */
private const val DelimiterPad: Float = 0.05f
private const val DelimiterStretchLimit: Float = 4f

/**
 * [node] laid out at [size] pixels, in [color], ready to be drawn at a baseline.
 *
 * [density] rather than a [TextUnit][androidx.compose.ui.unit.TextUnit] size
 * because everything here is geometry: a superscript's shift and a fraction's
 * gap are fractions of the size, and they have to be in the same units as the
 * glyphs they are measured against. Scaled type would move the glyphs and leave
 * the geometry where it was.
 */
internal fun layoutMath(
    node: MathNode,
    size: Float,
    color: Color,
    measurer: TextMeasurer,
    density: Density,
    style: MathStyle = MathStyle.Display,
): MathBox = MathLayout(measurer, color, density).box(node, size, style)

private fun MathStyle.factor(): Float = when (this) {
    MathStyle.Display, MathStyle.Text -> 1f
    MathStyle.Script -> ScriptScale
    MathStyle.ScriptScript -> ScriptScriptScale
}

/** The style a script of this one is set in. Two shrinks and no more. */
private fun MathStyle.scripted(): MathStyle = when (this) {
    MathStyle.Display, MathStyle.Text -> MathStyle.Script
    MathStyle.Script, MathStyle.ScriptScript -> MathStyle.ScriptScript
}

/** How much smaller a script of this style is drawn, against this style's own size. */
private fun MathStyle.scriptScale(): Float = scripted().factor() / factor()

/** A fraction's halves shrink less than a script does, and only in display. */
private fun MathStyle.fractionPart(): MathStyle = when (this) {
    MathStyle.Display -> MathStyle.Text
    MathStyle.Text -> MathStyle.Script
    MathStyle.Script, MathStyle.ScriptScript -> MathStyle.ScriptScript
}

private fun MathStyle.fractionScale(): Float =
    if (this == MathStyle.Display) FractionScale else scriptScale()

/**
 * One equation's layout pass.
 *
 * A class rather than a pile of parameters for one reason: the math axis is
 * measured, and a fraction inside a fraction inside a root would measure it
 * again for every level. It is cached here, keyed by size, and so measured once
 * per size the equation actually uses.
 */
private class MathLayout(
    private val measurer: TextMeasurer,
    private val color: Color,
    private val density: Density,
) {
    private val axes: MutableMap<Float, Float> = mutableMapOf()

    fun box(node: MathNode, size: Float, style: MathStyle): MathBox = when (node) {
        is MathNode.Row -> row(node.children.map { box(it, size, style) })
        is MathNode.Symbol -> leaf(node.text, size, italic = node.italic)
        is MathNode.Text -> leaf(node.text, size, bold = node.bold)
        is MathNode.Operator -> padded(leaf(node.text, size), size * operatorSpace(style))
        is MathNode.Function -> widened(leaf(node.name, size), size * ThinSpace)
        is MathNode.Space -> MathBox(size * node.em, 0f, 0f) { _, _ -> }
        is MathNode.Fraction -> fraction(node, size, style)
        is MathNode.Root -> root(node, size, style)
        is MathNode.Scripts -> scripts(node, size, style)
        is MathNode.BigOperator -> bigOperator(node, size, style)
        is MathNode.Delimited -> delimited(node, size, style)
        is MathNode.Accent -> accent(node, size, style)
    }

    /**
     * Where the middle of the equation sits above the baseline: the line a
     * fraction's bar is drawn on and a big operator is centred on.
     */
    private fun axis(size: Float): Float = axes.getOrPut(size) {
        measured("x", size, italic = false, bold = false).firstBaseline * AxisFraction
    }

    private fun measured(
        text: String,
        size: Float,
        italic: Boolean,
        bold: Boolean,
    ): TextLayoutResult = measurer.measure(
        text = AnnotatedString(text),
        style = TextStyle(
            color = color,
            fontSize = with(density) { size.toSp() },
            fontFamily = FontFamily.Serif,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        ),
    )

    private fun leaf(
        text: String,
        size: Float,
        italic: Boolean = false,
        bold: Boolean = false,
    ): MathBox {
        if (text.isEmpty()) return Empty

        val result: TextLayoutResult = measured(text, size, italic, bold)
        val ascent: Float = result.firstBaseline
        val descent: Float = result.size.height - ascent
        return MathBox(result.size.width.toFloat(), ascent, descent) { x, baselineY ->
            drawText(textLayoutResult = result, topLeft = Offset(x, baselineY - ascent))
        }
    }

    private fun row(children: List<MathBox>): MathBox {
        if (children.isEmpty()) return Empty
        if (children.size == 1) return children.single()

        var width = 0f
        for (child in children) width += child.width
        val ascent: Float = children.maxOf { it.ascent }
        val descent: Float = children.maxOf { it.descent }

        return MathBox(width, ascent, descent) { x, baselineY ->
            var cursor: Float = x
            for (child in children) {
                child.draw(this, cursor, baselineY)
                cursor += child.width
            }
        }
    }

    /** [box] with [space] either side of it, which is what makes an operator one. */
    private fun padded(box: MathBox, space: Float): MathBox =
        MathBox(box.width + 2 * space, box.ascent, box.descent) { x, baselineY ->
            box.draw(this, x + space, baselineY)
        }

    /** [box] with [space] after it, which is what keeps `\sin x` two words. */
    private fun widened(box: MathBox, space: Float): MathBox =
        MathBox(box.width + space, box.ascent, box.descent) { x, baselineY ->
            box.draw(this, x, baselineY)
        }

    private fun operatorSpace(style: MathStyle): Float = when (style) {
        MathStyle.Display, MathStyle.Text -> OperatorSpace
        MathStyle.Script, MathStyle.ScriptScript -> ScriptOperatorSpace
    }

    private fun scripts(node: MathNode.Scripts, size: Float, style: MathStyle): MathBox {
        val base: MathBox = box(node.base, size, style)
        val scriptSize: Float = size * style.scriptScale()
        val scriptStyle: MathStyle = style.scripted()
        return sideScripts(
            base = base,
            superscript = node.superscript?.let { box(it, scriptSize, scriptStyle) },
            subscript = node.subscript?.let { box(it, scriptSize, scriptStyle) },
            size = size,
        )
    }

    /**
     * [base] with its scripts stacked over each other after it, the way every
     * script that isn't a big operator's limit is set.
     */
    private fun sideScripts(
        base: MathBox,
        superscript: MathBox?,
        subscript: MathBox?,
        size: Float,
    ): MathBox {
        if (superscript == null && subscript == null) return base

        val up: Float = size * SuperscriptShift
        val down: Float = size * SubscriptShift
        val width: Float = base.width + max(superscript?.width ?: 0f, subscript?.width ?: 0f)
        val ascent: Float = max(base.ascent, superscript?.let { up + it.ascent } ?: 0f)
        val descent: Float = max(base.descent, subscript?.let { down + it.descent } ?: 0f)

        return MathBox(width, ascent, descent) { x, baselineY ->
            base.draw(this, x, baselineY)
            superscript?.draw(this, x + base.width, baselineY - up)
            subscript?.draw(this, x + base.width, baselineY + down)
        }
    }

    private fun fraction(node: MathNode.Fraction, size: Float, style: MathStyle): MathBox {
        val partStyle: MathStyle = style.fractionPart()
        val partSize: Float = size * style.fractionScale()
        val numerator: MathBox = box(node.numerator, partSize, partStyle)
        val denominator: MathBox = box(node.denominator, partSize, partStyle)

        val axis: Float = axis(size)
        val thickness: Float = size * RuleThickness
        val gap: Float = size * FractionGap
        val pad: Float = size * FractionPad
        val width: Float = max(numerator.width, denominator.width) + 2 * pad

        // Both baselines relative to the fraction's own, which is the line the
        // rest of the row sits on.
        val over: Float = -(axis + thickness / 2 + gap + numerator.descent)
        val under: Float = -axis + thickness / 2 + gap + denominator.ascent

        return MathBox(
            width = width,
            ascent = numerator.ascent - over,
            descent = under + denominator.descent,
        ) { x, baselineY ->
            numerator.draw(this, x + (width - numerator.width) / 2, baselineY + over)
            denominator.draw(this, x + (width - denominator.width) / 2, baselineY + under)
            if (node.rule) drawRect(
                color = color,
                topLeft = Offset(x, baselineY - axis - thickness / 2),
                size = Size(width, thickness),
            )
        }
    }

    private fun root(node: MathNode.Root, size: Float, style: MathStyle): MathBox {
        val body: MathBox = box(node.body, size, style)
        val thickness: Float = size * RuleThickness
        val clearance: Float = size * RootClearance
        val hook: Float = size * RootHook
        val tail: Float = size * RootTail
        val index: MathBox? = node.index?.let {
            box(it, size * ScriptScriptScale, MathStyle.ScriptScript)
        }

        val lead: Float = index?.width ?: 0f
        val ascent: Float = body.ascent + clearance + 2 * thickness
        val descent: Float = body.descent + thickness
        val width: Float = lead + hook + body.width + tail

        return MathBox(width, ascent, descent) { x, baselineY ->
            val top: Float = baselineY - ascent + thickness / 2
            val bottom: Float = baselineY + descent
            val left: Float = x + lead
            val fall: Float = bottom - top

            // Tick, down-stroke, up-stroke, then the overline across the body.
            val radical: Path = Path().apply {
                moveTo(left, top + fall * 0.62f)
                lineTo(left + hook * 0.24f, top + fall * 0.54f)
                lineTo(left + hook * 0.5f, bottom)
                lineTo(left + hook, top)
                lineTo(x + width, top)
            }
            drawPath(
                path = radical,
                color = color,
                style = Stroke(
                    width = thickness,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            body.draw(this, left + hook, baselineY)
            index?.draw(this, x, top + fall * 0.5f)
        }
    }

    /**
     * A sum, product, integral or limit with its limits where the style puts
     * them: stacked over and under it in display, beside it everywhere else.
     *
     * Only a single-glyph operator is set larger. `lim` is a word, and a word at
     * 1.4x next to type at 1x reads as a mistake rather than as an operator.
     */
    private fun bigOperator(node: MathNode.BigOperator, size: Float, style: MathStyle): MathBox {
        val large: Boolean = node.symbol.length == 1
        val symbol: MathBox = leaf(node.symbol, if (large) size * BigOperatorScale else size)
        val scriptSize: Float = size * style.scriptScale()
        val scriptStyle: MathStyle = style.scripted()
        val upper: MathBox? = node.upper?.let { box(it, scriptSize, scriptStyle) }
        val lower: MathBox? = node.lower?.let { box(it, scriptSize, scriptStyle) }

        if (style != MathStyle.Display) return sideScripts(symbol, upper, lower, size)
        if (upper == null && lower == null) return symbol

        val gap: Float = size * LimitGap
        val axis: Float = axis(size)

        // The symbol rides the axis, so a sum and the terms after it share a middle.
        val middle: Float = -axis + symbol.ascent - symbol.height / 2
        val top: Float = middle - symbol.ascent
        val bottom: Float = middle + symbol.descent
        val over: Float = top - gap - (upper?.descent ?: 0f)
        val under: Float = bottom + gap + (lower?.ascent ?: 0f)

        val width: Float = maxOf(symbol.width, upper?.width ?: 0f, lower?.width ?: 0f)
        val ascent: Float = -(if (upper == null) top else over - upper.ascent)
        val descent: Float = if (lower == null) bottom else under + lower.descent

        return MathBox(width, ascent, descent) { x, baselineY ->
            symbol.draw(this, x + (width - symbol.width) / 2, baselineY + middle)
            upper?.draw(this, x + (width - upper.width) / 2, baselineY + over)
            lower?.draw(this, x + (width - lower.width) / 2, baselineY + under)
        }
    }

    private fun delimited(node: MathNode.Delimited, size: Float, style: MathStyle): MathBox {
        val body: MathBox = box(node.body, size, style)
        val axis: Float = axis(size)

        // Symmetric about the axis: a bracket that leans one way reads as broken,
        // however lopsided the thing inside it is.
        val half: Float = max(body.ascent - axis, body.descent + axis) + size * DelimiterPad
        val left: MathBox = delimiter(node.left, size, half)
        val right: MathBox = delimiter(node.right, size, half)

        return MathBox(
            width = left.width + body.width + right.width,
            ascent = maxOf(body.ascent, left.ascent, right.ascent),
            descent = maxOf(body.descent, left.descent, right.descent),
        ) { x, baselineY ->
            left.draw(this, x, baselineY)
            body.draw(this, x + left.width, baselineY)
            right.draw(this, x + left.width + body.width, baselineY)
        }
    }

    /**
     * One delimiter glyph, stretched vertically to cover [half] either side of
     * the axis. "." is the delimiter that isn't one, and draws nothing.
     *
     * Stretched about its own centre rather than re-measured at a bigger size: a
     * bracket has to reach the height it is given exactly, and a font's next size
     * up only ever gets close.
     */
    private fun delimiter(glyph: String, size: Float, half: Float): MathBox {
        if (glyph == "." || glyph.isEmpty()) return Empty

        val natural: MathBox = leaf(glyph, size)
        if (natural.height <= 0f) return natural

        val stretch: Float = (2 * half / natural.height).coerceIn(1f, DelimiterStretchLimit)
        if (stretch <= 1f) return natural

        val grow: Float = natural.height * (stretch - 1) / 2
        return MathBox(natural.width, natural.ascent + grow, natural.descent + grow) { x, y ->
            val center: Float = y - natural.ascent + natural.height / 2
            scale(1f, stretch, pivot = Offset(x + natural.width / 2, center)) {
                natural.draw(this, x, y)
            }
        }
    }

    private fun accent(node: MathNode.Accent, size: Float, style: MathStyle): MathBox {
        val base: MathBox = box(node.base, size, style)
        val gap: Float = size * AccentGap
        val thickness: Float = size * RuleThickness
        val mark: Float = size * AccentHeight
        val ascent: Float = base.ascent + gap + mark

        return MathBox(base.width, ascent, base.descent) { x, baselineY ->
            base.draw(this, x, baselineY)
            val width: Float = max(base.width, size * AccentMinWidth)
            drawAccent(
                kind = node.kind,
                left = x + (base.width - width) / 2,
                top = baselineY - ascent,
                width = width,
                height = mark,
                thickness = thickness,
                color = color,
            )
        }
    }
}

private val Empty = MathBox(0f, 0f, 0f) { _, _ -> }

/**
 * The mark over an accented term, drawn rather than set.
 *
 * A glyph would have to be positioned by its ink, and text measurement gives a
 * line box: a `^` placed by its line box sits a whole ascender too high. These
 * are five short paths, and they land where they are put.
 */
private fun DrawScope.drawAccent(
    kind: String,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    thickness: Float,
    color: Color,
) {
    val stroke = Stroke(width = thickness, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val middle: Float = left + width / 2

    when (kind) {
        "bar" -> drawLine(
            color = color,
            start = Offset(left, top + height),
            end = Offset(left + width, top + height),
            strokeWidth = thickness,
            cap = StrokeCap.Round,
        )

        "hat" -> drawPath(
            path = Path().apply {
                moveTo(left, top + height)
                lineTo(middle, top)
                lineTo(left + width, top + height)
            },
            color = color,
            style = stroke,
        )

        "vec" -> {
            val y: Float = top + height / 2
            drawLine(
                color = color,
                start = Offset(left, y),
                end = Offset(left + width, y),
                strokeWidth = thickness,
                cap = StrokeCap.Round,
            )
            drawArrowHead(
                color = color,
                tip = Offset(left + width, y),
                from = Offset(left, y),
                weight = thickness,
            )
        }

        "tilde" -> drawPath(
            path = Path().apply {
                moveTo(left, top + height * 0.75f)
                cubicTo(
                    left + width * 0.25f, top - height * 0.25f,
                    left + width * 0.75f, top + height * 1.25f,
                    left + width, top + height * 0.25f,
                )
            },
            color = color,
            style = stroke,
        )

        "dot" -> drawCircle(color, thickness, Offset(middle, top + height / 2))

        "ddot" -> {
            drawCircle(color, thickness, Offset(middle - width * 0.15f, top + height / 2))
            drawCircle(color, thickness, Offset(middle + width * 0.15f, top + height / 2))
        }
    }
}

/**
 * An equation: its LaTeX parsed, laid out, and scaled to fit the element's frame.
 *
 * Everything about the picture is a pure function of the source, so nothing here
 * is stored and the layout only reruns when the text, the size, the colour or
 * the density changes. The measuring is the expensive half and it all happens
 * once, in [layoutMath]; a redraw walks closures.
 *
 * The fit is capped at 1:1, like a diagram's: an equation smaller than its box is
 * centred in it rather than blown up, so two equations set at the same font size
 * read at the same size however big a box each was dropped into. Vertically it is
 * the box that is centred rather than the baseline, because an equation with a
 * tall fraction in it has no baseline worth aligning to.
 */
@Composable
internal fun EquationElementView(element: EquationElement) {
    val measurer: TextMeasurer = rememberTextMeasurer()
    val density: Density = LocalDensity.current
    val color: Color = element.color.toComposeColor()

    val box: MathBox = remember(element.latex, element.fontSize, element.color, density) {
        // One document unit is one dp, and the surface has already folded the
        // canvas' zoom into the density, so this is the size at 1:1 in pixels.
        val size: Float = element.fontSize * density.density
        layoutMath(parseMath(element.latex), size, color, measurer, density)
    }

    Canvas(modifier = Modifier.size(element.frame.width.dp, element.frame.height.dp)) {
        if (box.width <= 0f || box.height <= 0f) return@Canvas

        val zoom: Float = min(min(size.width / box.width, size.height / box.height), 1f)
        val left: Float = (size.width - box.width * zoom) / 2f
        val top: Float = (size.height - box.height * zoom) / 2f

        withTransform({
            translate(left, top)
            scale(zoom, zoom, pivot = Offset.Zero)
        }) {
            box.draw(this, 0f, box.ascent)
        }
    }
}
