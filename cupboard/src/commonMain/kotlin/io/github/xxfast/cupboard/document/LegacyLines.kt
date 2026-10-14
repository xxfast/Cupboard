package io.github.xxfast.cupboard.document

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull

/**
 * [root] with every line written before format version 2 rewritten as a
 * [LineElement].
 *
 * A version 1 line was a shape, `{"type":"shape","kind":"Line"}`, whose stroke ran
 * corner to corner across its frame: top-left to bottom-right, mirrored on x by a
 * horizontal flip and on y by a vertical one, then turned by the frame's rotation
 * about its centre. Those two corners become the line's two points. Its stroke,
 * shadow, opacity, lock, name, visibility and link come along; its arrows become
 * [LineEnd.Arrow] ends; its fill and label, which a line never drew, are dropped.
 *
 * An [ImageMask] could name the same kind and read as the rectangle it was boxed
 * by, so it becomes [ShapeKind.Rectangle], which is what it drew.
 *
 * The walk is over every object and array in the tree, so groups, layouts and
 * masters are covered without this knowing where they keep their elements.
 */
internal fun migrateLegacyLines(root: JsonElement): JsonElement = when (root) {
    is JsonArray -> JsonArray(root.map { migrateLegacyLines(it) })

    is JsonObject -> {
        val walked = JsonObject(root.mapValues { (_, value) -> migrateLegacyLines(value) })
        when {
            walked.isLegacyLine() -> walked.asLine()
            walked.isLineMask() -> JsonObject(walked + ("kind" to JsonPrimitive("Rectangle")))
            else -> walked
        }
    }

    else -> root
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.isLegacyLine(): Boolean = string("type") == "shape" && string("kind") == "Line"

// A mask is the one other object carrying a kind: a `frame` beside it and no
// `type`, which every element has.
private fun JsonObject.isLineMask(): Boolean =
    "type" !in this && "frame" in this && string("kind") == "Line"

private fun JsonObject.asLine(): JsonObject {
    fun number(of: JsonObject, key: String): Float = (of[key] as? JsonPrimitive)?.floatOrNull ?: 0f
    fun flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

    val frame: JsonObject = this["frame"] as? JsonObject ?: JsonObject(emptyMap())
    val left: Float = number(frame, "x")
    val top: Float = number(frame, "y")
    val right: Float = left + number(frame, "width")
    val bottom: Float = top + number(frame, "height")
    val centerX: Float = (left + right) / 2
    val centerY: Float = (top + bottom) / 2
    val rotation: Float = (this["rotation"] as? JsonPrimitive)?.floatOrNull ?: 0f

    fun corner(x: Float, y: Float): JsonObject {
        val flippedX: Float = if (flag("flippedHorizontally")) left + right - x else x
        val flippedY: Float = if (flag("flippedVertically")) top + bottom - y else y
        val (dx: Float, dy: Float) = rotateVector(flippedX - centerX, flippedY - centerY, rotation)
        return JsonObject(
            mapOf(
                "x" to JsonPrimitive(centerX + dx),
                "y" to JsonPrimitive(centerY + dy),
            ),
        )
    }

    fun cap(key: String): JsonPrimitive =
        JsonPrimitive(if (flag(key)) LineEnd.Arrow.name else LineEnd.None.name)

    val kept: Map<String, JsonElement> = listOf(
        "id",
        "strokeColor",
        "strokeWidth",
        "shadow",
        "opacity",
        "locked",
        "name",
        "hidden",
        "link",
    ).mapNotNull { key -> this[key]?.let { key to it } }.toMap()

    // A shape that never wrote its stroke drew with a shape's defaults, which are
    // not a line's: those are what it keeps.
    val shape = ShapeElement(frame = Frame(0f, 0f, 0f, 0f))
    return JsonObject(
        mapOf(
            "type" to JsonPrimitive("line"),
            "start" to corner(left, top),
            "end" to corner(right, bottom),
            "strokeColor" to JsonPrimitive(shape.strokeColor),
            "strokeWidth" to JsonPrimitive(shape.strokeWidth),
            "startEnd" to cap("startArrow"),
            "endEnd" to cap("endArrow"),
        ) + kept,
    )
}
