package io.github.xxfast.cupboard.document

/*
 * Adapted from `storyboard-warp`'s `HeckelDiff.kt` by Rahul Ravikumar
 * (https://github.com/bnorm/storyboard, Apache License 2.0, originally
 * https://github.com/tikurahul/warp), described in
 * https://rahulrav.com/blog/magic_move.html, after P. Heckel, "A technique for
 * isolating differences between files", Comm. ACM 21(4), 264-268 (1978).
 *
 * Changes from the original, as Apache License 2.0 section 4 asks:
 * - tokens are Cupboard's [CodeToken] rather than storyboard's `Token`, and
 *   matching goes through an explicit key instead of an overridden `equals`,
 * - `androidx.collection`'s ScatterMap is a plain HashMap, so `:cupboard` stays
 *   dependency-free and keeps compiling for mingwX64,
 * - the `Keys` / content-id machinery is dropped: we diff one version against
 *   the next, and never need ids stable across a whole chain,
 * - whitespace is never an anchor in phase 1 (see below).
 */

/**
 * One instruction in a morph: a token that survives from one version of a code
 * block to the next, one that arrives, or one that leaves.
 *
 * A [Match] carries both tokens rather than one, because the pair is the whole
 * point: the previous token says where the glyph starts, the current one says
 * where it lands, and the renderer tweens between the two.
 */
sealed interface TokenEdit {
    data class Match(
        val previous: CodeToken,
        val previousIndex: Int,
        val current: CodeToken,
        val currentIndex: Int,
    ) : TokenEdit

    data class Insert(val token: CodeToken, val index: Int) : TokenEdit

    data class Delete(val token: CodeToken, val index: Int) : TokenEdit
}

/**
 * What a token is matched on: its text, its kind, and how deep it is nested.
 *
 * Position is deliberately out of it, because position is what the morph
 * animates. Depth is deliberately in it: two identical braces at different
 * nesting levels are different braces, and pairing them up would make the
 * animation cross itself.
 */
private data class TokenKey(val content: String, val kind: CodeTokenKind, val depth: Int)

private val CodeToken.key: TokenKey get() = TokenKey(content, kind, depth)

/**
 * How [previous] becomes [current], token by token: Heckel's diff, which finds
 * the tokens both sides agree on and calls everything else an insert or a delete.
 *
 * Four passes. The first pairs up tokens that appear exactly once on each side,
 * which are the anchors nothing else can be confused with. The second and third
 * march forwards and backwards out of every anchor, picking up the neighbours
 * that agree, which is what carries a whole unchanged line along with the one
 * identifier in it that happened to be unique. The fourth turns whatever is left
 * into deletes (on the previous side) and inserts (on the current side).
 *
 * The returned script is in the order a renderer wants to walk it: deletes sit
 * next to the tokens they were between, and contiguous deletes come out as one
 * run, so a removed line leaves together instead of a character at a time.
 */
fun diffTokens(previous: List<CodeToken>, current: List<CodeToken>): List<TokenEdit> {
    val editsP: Array<TokenEdit?> = arrayOfNulls(previous.size)
    val editsC: Array<TokenEdit?> = arrayOfNulls(current.size)

    val (frequencyP: Map<TokenKey, Int>, contextP: Map<TokenKey, TokenContext?>) =
        frequencyAndContext(previous)
    val (frequencyC: Map<TokenKey, Int>, _) = frequencyAndContext(current)

    fun match(previousIndex: Int, currentIndex: Int) {
        val edit = TokenEdit.Match(
            previous = previous[previousIndex],
            previousIndex = previousIndex,
            current = current[currentIndex],
            currentIndex = currentIndex,
        )
        editsP[previousIndex] = edit
        editsC[currentIndex] = edit
    }

    // Phase 1: the anchors, tokens that occur exactly once on both sides.
    current.forEachIndexed { index, token ->
        // Our one deviation from the original: an indent run that happens to be
        // unique is not evidence of anything, and pinning a match on it drags
        // unrelated lines onto each other. Whitespace still matches in the
        // marches below, where it has a real anchor beside it.
        if (token.kind == CodeTokenKind.Whitespace) return@forEachIndexed

        val key: TokenKey = token.key
        val context: TokenContext = contextP[key] ?: return@forEachIndexed
        if (frequencyC[key] == 1 && frequencyP[key] == 1) match(context.index, index)
    }

    // Phase 2: forwards out of every anchor, while both sides keep agreeing.
    for (index in 0 until editsC.size - 1) {
        val edit: TokenEdit? = editsC[index]
        if (edit !is TokenEdit.Match) continue

        val nextIndex: Int = index + 1
        val nextPreviousIndex: Int = edit.previousIndex + 1
        val next: CodeToken = current[nextIndex]
        val nextPrevious: CodeToken = previous.getOrNull(nextPreviousIndex) ?: continue
        if (next.key == nextPrevious.key &&
            editsC[nextIndex] == null &&
            editsP[nextPreviousIndex] == null
        ) {
            match(nextPreviousIndex, nextIndex)
        }
    }

    // Phase 3: the same march backwards.
    for (index in editsC.size - 1 downTo 1) {
        val edit: TokenEdit? = editsC[index]
        if (edit !is TokenEdit.Match) continue

        val priorIndex: Int = index - 1
        val priorPreviousIndex: Int = edit.previousIndex - 1
        val prior: CodeToken = current[priorIndex]
        val priorPrevious: CodeToken = previous.getOrNull(priorPreviousIndex) ?: continue
        if (prior.key == priorPrevious.key &&
            editsC[priorIndex] == null &&
            editsP[priorPreviousIndex] == null
        ) {
            match(priorPreviousIndex, priorIndex)
        }
    }

    // Phase 4: everything still unmatched is a delete on one side, an insert on
    // the other, woven together into one script.
    for (index in editsP.indices) {
        if (editsP[index] == null) editsP[index] = TokenEdit.Delete(previous[index], index)
    }

    val edits = ArrayList<TokenEdit>(maxOf(editsP.size, editsC.size))
    // How far the delete side has already been emitted, so a run is written out
    // once, where it starts, rather than once per index it covers.
    var deleted: Int = 0

    for (index in 0 until maxOf(editsP.size, editsC.size)) {
        if (index < editsP.size && deleted <= index && editsP[index] is TokenEdit.Delete) {
            var end: Int = index
            while (end < editsP.size && editsP[end] is TokenEdit.Delete) {
                edits += editsP[end]!!
                end += 1
            }
            deleted = end
        }

        if (index < editsC.size) {
            val edit: TokenEdit = editsC[index] ?: TokenEdit.Insert(current[index], index)
            editsC[index] = edit
            edits += edit
        }
    }

    return edits
}

/** [diffTokens] over two snippets of source, tokenized the same way. */
fun diffCode(previous: String, current: String, language: String = ""): List<TokenEdit> =
    diffTokens(tokenizeCode(previous, language), tokenizeCode(current, language))

/** Where a key's single occurrence was, kept only while it has exactly one. */
private data class TokenContext(val index: Int)

/**
 * How often each key occurs, and where the ones that occur once sit.
 *
 * The second map goes to null the moment a key is seen twice: a key that occurs
 * more than once has no unambiguous position, which is precisely what
 * disqualifies it as an anchor.
 */
private fun frequencyAndContext(
    tokens: List<CodeToken>,
): Pair<Map<TokenKey, Int>, Map<TokenKey, TokenContext?>> {
    val frequency = HashMap<TokenKey, Int>()
    val contexts = HashMap<TokenKey, TokenContext?>()

    tokens.forEachIndexed { index, token ->
        val key: TokenKey = token.key
        val count: Int = frequency[key] ?: 0
        contexts[key] = if (count == 0) TokenContext(index) else null
        frequency[key] = count + 1
    }

    return frequency to contexts
}
