package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The token diff a code morph is built on: what survives from one version to the
 * next, what arrives, and what leaves.
 *
 * The cases the original storyboard tests cover, asserted rather than printed:
 * the value of a diff is in which tokens it keeps, and a test that only prints
 * it never notices when it stops keeping them.
 */
class CodeDiffTest {
    private fun matched(edits: List<TokenEdit>): List<String> =
        edits.filterIsInstance<TokenEdit.Match>().map { it.current.content }

    private fun inserted(edits: List<TokenEdit>): List<String> =
        edits.filterIsInstance<TokenEdit.Insert>().map { it.token.content }

    private fun deleted(edits: List<TokenEdit>): List<String> =
        edits.filterIsInstance<TokenEdit.Delete>().map { it.token.content }

    @Test
    fun identicalVersionsAreAllMatchesAndNothingElse() {
        val code = "fun main() {\n    println(\"hi\")\n}"
        val edits: List<TokenEdit> = diffCode(code, code, "kotlin")

        assertEquals(tokenizeCode(code).size, edits.size)
        assertTrue(edits.all { it is TokenEdit.Match })
    }

    @Test
    fun emptyToSomethingIsAllInserts() {
        val edits: List<TokenEdit> = diffCode("", "val x = 10", "kotlin")

        assertTrue(edits.all { it is TokenEdit.Insert })
        assertEquals(listOf("val", " ", "x", " ", "=", " ", "10"), inserted(edits))
    }

    @Test
    fun somethingToEmptyIsAllDeletes() {
        val edits: List<TokenEdit> = diffCode("val x = 10", "", "kotlin")

        assertTrue(edits.all { it is TokenEdit.Delete })
        assertEquals(listOf("val", " ", "x", " ", "=", " ", "10"), deleted(edits))
    }

    @Test
    fun onlyTheRenamedIdentifierMoves() {
        val edits: List<TokenEdit> = diffCode("val x = 10", "val y = 10", "kotlin")

        assertEquals(listOf("x"), deleted(edits))
        assertEquals(listOf("y"), inserted(edits))
        assertTrue("val" in matched(edits) && "10" in matched(edits))
    }

    @Test
    fun onlyTheChangedNumberMoves() {
        val edits: List<TokenEdit> = diffCode("val x = 10", "val x = 20", "kotlin")

        assertEquals(listOf("10"), deleted(edits))
        assertEquals(listOf("20"), inserted(edits))
        assertEquals(listOf("val", " ", "x", " ", "=", " "), matched(edits))
    }

    @Test
    fun aTokenThatMovesToAnotherLineStillMatches() {
        val previous = "val x = 10\nfun convert(input: Int) {\n  // ...\n}"
        val current = "fun convert(input: Int) {\n  println(\"done\")\n}"
        val edits: List<TokenEdit> = diffCode(previous, current, "kotlin")

        val moved: TokenEdit.Match = edits.filterIsInstance<TokenEdit.Match>()
            .single { it.current.content == "convert" }
        assertEquals(1, moved.previous.line)
        assertEquals(0, moved.current.line)

        // The signature travels whole, and only the body is rewritten.
        assertTrue(listOf("fun", "convert", "input", "Int", "{", "}").all { it in matched(edits) })
        assertEquals(listOf("// ..."), deleted(edits).filter { it.startsWith("//") })
    }

    @Test
    fun aLineLeavingAndAnotherArrivingKeepsTheOnesBetween() {
        val previous = "val a = 10\nval b = 20\nval c = 30"
        val current = "val b = 20\nval c = 30\nval d = 40"
        val edits: List<TokenEdit> = diffCode(previous, current, "kotlin")

        assertTrue(listOf("b", "20", "c", "30").all { it in matched(edits) })
        assertTrue("a" in deleted(edits) && "10" in deleted(edits))
        assertTrue("d" in inserted(edits) && "40" in inserted(edits))
    }

    @Test
    fun oneMoreEntryInAJsonArrayIsOneInsertion() {
        val previous = "{\n  \"a\" : 10,\n  \"b\" : [1, 2]\n}"
        val current = "{\n  \"a\" : 10,\n  \"b\" : [1, 2, 3]\n}"
        val edits: List<TokenEdit> = diffCode(previous, current, "json")

        assertEquals(emptyList(), deleted(edits))
        assertEquals(listOf(",", " ", "3"), inserted(edits))
    }

    @Test
    fun contiguousDeletesComeOutAdjacent() {
        val edits: List<TokenEdit> = diffCode("val a = 1\nval b = 2", "val a = 1", "kotlin")
        val deletes: List<Int> = edits.withIndex()
            .filter { (_, edit) -> edit is TokenEdit.Delete }
            .map { (index, _) -> index }

        assertTrue(deletes.isNotEmpty())
        assertEquals((deletes.first()..deletes.last()).toList(), deletes)
    }

    @Test
    fun aUniqueIndentNeverAnchorsAMatch() {
        // The four-space indent is unique on both sides, but it belongs to two
        // unrelated lines: anchoring on it would drag `println` onto `return`.
        val previous = "fun a() {\n    println(1)\n}"
        val current = "fun a() {\n\t\treturn 1\n}"
        val edits: List<TokenEdit> = diffCode(previous, current, "kotlin")

        assertTrue("println" in deleted(edits))
        assertTrue("return" in inserted(edits))
        assertTrue("    " in deleted(edits))
    }

    @Test
    fun matchesCarryBothPositionsSoTheMorphKnowsWhereToTween() {
        val edits: List<TokenEdit> = diffCode("  x", "x", "kotlin")
        val match: TokenEdit.Match = edits.filterIsInstance<TokenEdit.Match>().single()

        assertEquals(2, match.previous.start)
        assertEquals(0, match.current.start)
        assertEquals("x", match.current.content)
    }
}
