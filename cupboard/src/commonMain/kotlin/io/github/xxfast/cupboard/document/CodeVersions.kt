package io.github.xxfast.cupboard.document

/**
 * Every version of a block's source, oldest first: [CodeElement.code] is version
 * 0, and [CodeElement.versions] carries the rest.
 *
 * Never empty, so version 0 always exists: a block with nothing typed in it is
 * one empty version rather than none, which is what lets every caller index
 * without a special case.
 */
val CodeElement.sources: List<String> get() = listOf(code) + versions

/**
 * The source at [version], clamped into range rather than throwing.
 *
 * Out of range is not an error for the same reason an out-of-range [LineRange]
 * is not: a [CodeStep] outlives edits to the versions it points at, and a deck
 * that loses one should keep playing on the nearest one left.
 */
fun CodeElement.sourceAt(version: Int): String {
    val all: List<String> = sources
    return all[version.coerceIn(all.indices)]
}

/** This block with [text] as its [version], clamped like [sourceAt]. */
fun CodeElement.withSource(version: Int, text: String): CodeElement {
    val all: List<String> = sources
    val index: Int = version.coerceIn(all.indices)
    return withSources(all.toMutableList().also { it[index] = text })
}

/**
 * This block with a copy of version [after] inserted straight behind it, which
 * is how a new version is made: a morph is an edit of the version before it, so
 * a fresh version starts as that version's text rather than as an empty box.
 *
 * Steps pointing past the insertion move up with the text they pointed at.
 */
fun CodeElement.withVersionAdded(after: Int): CodeElement {
    val all: List<String> = sources
    val index: Int = after.coerceIn(all.indices)
    val grown: List<String> = all.toMutableList().also { it.add(index + 1, all[index]) }
    return withSources(grown).remapping { version -> if (version > index) version + 1 else version }
}

/**
 * This block without version [index], or itself when that is the only version:
 * a code block with no source at all is not a state this offers.
 *
 * Removing version 0 promotes the next one into [CodeElement.code], since that
 * field is the first version rather than a version of its own.
 */
fun CodeElement.withVersionRemoved(index: Int): CodeElement {
    val all: List<String> = sources
    if (all.size <= 1) return this

    val at: Int = index.coerceIn(all.indices)
    val map: List<Int> = versionsAfterRemoving(all.size, at)
    val left: List<String> = all.toMutableList().also { it.removeAt(at) }
    return withSources(left).remapping { map[it.coerceIn(map.indices)] }
}

/** This block with version [from] moved to sit at [to], steps following their text. */
fun CodeElement.withVersionMoved(from: Int, to: Int): CodeElement {
    val all: List<String> = sources
    val start: Int = from.coerceIn(all.indices)
    val end: Int = to.coerceIn(all.indices)
    if (start == end) return this

    val map: List<Int> = versionsAfterMoving(all.size, start, end)
    val moved: List<String> = all.toMutableList().also { it.add(end, it.removeAt(start)) }
    return withSources(moved).remapping { map[it.coerceIn(map.indices)] }
}

/**
 * Where each of [count] versions lands once [index] is taken out: indexed by the
 * old version, valued by the new one.
 *
 * The removed version itself has nowhere to land, so it falls back to the
 * nearest version that survives, which is the one that took its place, or the
 * last one when it was the last one.
 */
fun versionsAfterRemoving(count: Int, index: Int): List<Int> {
    val left: Int = (count - 1).coerceAtLeast(1)
    return (0 until count).map { version ->
        when {
            version < index -> version
            version > index -> version - 1
            else -> index
        }.coerceIn(0, left - 1)
    }
}

/** [versionsAfterRemoving]'s twin for a move: where each of [count] versions ends up. */
fun versionsAfterMoving(count: Int, from: Int, to: Int): List<Int> {
    val order: List<Int> = (0 until count).toMutableList().also { it.add(to, it.removeAt(from)) }
    return (0 until count).map { version -> order.indexOf(version) }
}

/** This block rebuilt from [all], the first of which is always [CodeElement.code]. */
private fun CodeElement.withSources(all: List<String>): CodeElement =
    copy(code = all.first(), versions = all.drop(1))

/** This block with every step's version put through [map]. */
private fun CodeElement.remapping(map: (Int) -> Int): CodeElement {
    if (steps.isEmpty()) return this
    return copy(steps = steps.map { step -> step.copy(version = map(step.version)) })
}
