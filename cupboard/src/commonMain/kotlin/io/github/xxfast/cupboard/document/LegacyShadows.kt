package io.github.xxfast.cupboard.document

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.longOrNull

/**
 * [root] with every shadow written before format version 3 rewritten as a
 * [ShadowKind.Drop] [ShapeShadow].
 *
 * A version 2 shadow was a step, `dx` right and `dy` down, and a colour whose
 * alpha was the shadow's opacity. The step becomes an [ShapeShadow.offset] along
 * an [ShapeShadow.angle] (see [shadowOffsetAngle]), the alpha becomes
 * [ShapeShadow.opacity] and the colour goes opaque, which is how the editor
 * leaves one. A field the file never wrote reads as the old default it stood
 * for, not the new one.
 *
 * Every object under a `shadow` key is one, wherever it sits: a shape's, a
 * line's (including the lines [migrateLegacyLines] has just made), a saved
 * style's, inside groups and on layouts alike. Only run on a file older than
 * version 3: a current shadow has no `dx` to read and would come out as the old
 * default.
 */
internal fun migrateLegacyShadows(root: JsonElement): JsonElement = when (root) {
    is JsonArray -> JsonArray(root.map { migrateLegacyShadows(it) })

    is JsonObject -> JsonObject(
        root.mapValues { (key, value) ->
            val walked: JsonElement = migrateLegacyShadows(value)
            if (key == "shadow" && walked is JsonObject) walked.asShadow() else walked
        },
    )

    else -> root
}

private fun JsonObject.asShadow(): JsonObject {
    fun number(key: String, default: Float): Float =
        (this[key] as? JsonPrimitive)?.floatOrNull ?: default

    val color: Long = (this["color"] as? JsonPrimitive)?.longOrNull ?: LegacyShadowColor
    val alpha: Long = (color shr 24) and 0xFF
    val (offset: Float, angle: Float) = shadowOffsetAngle(
        dx = number("dx", 0f),
        dy = number("dy", LegacyShadowDy),
    )

    val migrated: Map<String, JsonElement> = mapOf(
        "kind" to JsonPrimitive(ShadowKind.Drop.name),
        "color" to JsonPrimitive(color or 0xFF000000),
        "blur" to JsonPrimitive(number("blur", LegacyShadowBlur)),
        "offset" to JsonPrimitive(offset),
        "angle" to JsonPrimitive(angle),
        "opacity" to JsonPrimitive(alpha / 255f),
    )
    return JsonObject(this - "dx" - "dy" + migrated)
}

/** What a version 2 shadow defaulted to, for a file that left a field out. */
private const val LegacyShadowColor: Long = 0x80000000
private const val LegacyShadowBlur: Float = 12f
private const val LegacyShadowDy: Float = 6f
