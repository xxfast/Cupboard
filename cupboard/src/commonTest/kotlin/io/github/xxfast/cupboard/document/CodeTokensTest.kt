package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lexer a code morph diffs on: language-agnostic, fault tolerant, and cut so
 * that a token is always a thing with one position on screen.
 */
class CodeTokensTest {
    private fun kinds(code: String, language: String = ""): List<CodeTokenKind> =
        tokenizeCode(code, language).map { it.kind }

    private fun contents(code: String, language: String = ""): List<String> =
        tokenizeCode(code, language).map { it.content }

    @Test
    fun emptyCodeIsNoTokensAtAll() {
        assertEquals(emptyList(), tokenizeCode(""))
    }

    @Test
    fun eachKindComesOutAsItself() {
        val tokens: List<CodeToken> = tokenizeCode("val x = 10 // note")

        assertEquals(
            listOf(
                CodeTokenKind.Word,
                CodeTokenKind.Whitespace,
                CodeTokenKind.Word,
                CodeTokenKind.Whitespace,
                CodeTokenKind.Punctuation,
                CodeTokenKind.Whitespace,
                CodeTokenKind.Number,
                CodeTokenKind.Whitespace,
                CodeTokenKind.Comment,
            ),
            tokens.map { it.kind },
        )
        assertEquals("// note", tokens.last().content)
    }

    @Test
    fun numbersKeepTheirRadixSeparatorsAndSuffix() {
        assertEquals(listOf("0xFF", " ", "1_000", " ", "1.5f"), contents("0xFF 1_000 1.5f"))
        // A dot with no digit behind it is not part of the number.
        assertEquals(listOf("1", ".", ".", "5"), contents("1..5"))
    }

    @Test
    fun anIdentifierRunIsOneWordAndAnOperatorIsOneCharacterEach() {
        assertEquals(listOf("my_name$1", " ", "-", ">"), contents("my_name\$1 ->"))
    }

    @Test
    fun aBracketPairSharesADepthAndTheBodyBetweenThemIsDeeper() {
        val tokens: List<CodeToken> = tokenizeCode("f(a[b])")
        val depths: Map<String, Int> = tokens.associate { it.content to it.depth }

        assertEquals(0, depths.getValue("f"))
        assertEquals(0, depths.getValue("("))
        assertEquals(0, depths.getValue(")"))
        assertEquals(1, depths.getValue("a"))
        assertEquals(1, depths.getValue("["))
        assertEquals(1, depths.getValue("]"))
        assertEquals(2, depths.getValue("b"))
    }

    @Test
    fun aStrayCloserNeverTakesTheDepthBelowZero() {
        assertTrue(tokenizeCode("} ) x").all { it.depth == 0 })
    }

    @Test
    fun aBlockCommentIsCutAtEveryNewline() {
        val tokens: List<CodeToken> = tokenizeCode("/* one\n   two */ x")
        val comments: List<CodeToken> = tokens.filter { it.kind == CodeTokenKind.Comment }

        assertEquals(listOf("/* one", "   two */"), comments.map { it.content })
        assertEquals(listOf(0, 1), comments.map { it.line })
        // The token after it is code again, and on the line it was typed on.
        assertEquals("x", tokens.last().content)
        assertEquals(1, tokens.last().line)
    }

    @Test
    fun aRawStringIsCutAtEveryNewlineToo() {
        val strings: List<CodeToken> = tokenizeCode("val a = \"\"\"one\ntwo\"\"\"")
            .filter { it.kind == CodeTokenKind.String }

        assertEquals(listOf("\"\"\"one", "two\"\"\""), strings.map { it.content })
        assertEquals(listOf(0, 1), strings.map { it.line })
    }

    @Test
    fun columnsAreCountedWithinTheirOwnLine() {
        val second: CodeToken = tokenizeCode("ab\n  cd").last()

        assertEquals("cd", second.content)
        assertEquals(1, second.line)
        assertEquals(2, second.start)
        assertEquals(4, second.end)
    }

    @Test
    fun leadingIndentIsOneWhitespaceToken() {
        val first: CodeToken = tokenizeCode("    x").first()

        assertEquals(CodeTokenKind.Whitespace, first.kind)
        assertEquals("    ", first.content)
    }

    @Test
    fun anEscapedQuoteStaysInsideItsString() {
        assertEquals(listOf("\"a\\\"b\""), contents("\"a\\\"b\""))
    }

    @Test
    fun anUnterminatedStringStopsAtTheNewline() {
        val tokens: List<CodeToken> = tokenizeCode("\"oops\nx")

        assertEquals("\"oops", tokens.first().content)
        assertEquals(CodeTokenKind.String, tokens.first().kind)
        assertEquals(listOf(CodeTokenKind.Word), kinds("x"))
        assertEquals("x", tokens.last().content)
    }

    @Test
    fun hashOpensACommentOnlyInTheLanguagesThatSayItDoes() {
        assertEquals(listOf(CodeTokenKind.Comment), kinds("# note", "Python"))
        assertEquals(listOf(CodeTokenKind.Comment), kinds("# note", "yaml"))
        // Anywhere else it is an operator or a preprocessor mark, not a comment.
        assertEquals(
            listOf(CodeTokenKind.Punctuation, CodeTokenKind.Whitespace, CodeTokenKind.Word),
            kinds("# note", "kotlin"),
        )
    }
}
