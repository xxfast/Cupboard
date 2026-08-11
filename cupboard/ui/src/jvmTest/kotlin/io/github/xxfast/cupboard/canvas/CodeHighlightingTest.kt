package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.graphics.isSpecified
import dev.snipme.highlights.model.SyntaxLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodeHighlightingTest {

    @Test
    fun keepsTheCodeVerbatim() {
        val code = "fun main() {\n    println(\"hi\")\n}"
        val highlighted = highlightCode(code, "kotlin")
        assertEquals(code, highlighted.text)
    }

    @Test
    fun colorsAtLeastOneSpan() {
        val code = "fun main() {\n    val x = 1 // comment\n}"
        val highlighted = highlightCode(code, "kotlin")
        assertTrue(highlighted.spanStyles.any { it.item.color.isSpecified })
    }

    @Test
    fun unknownLanguageStillRendersEveryCharacter() {
        val code = "++++[>++++<-]>."
        val highlighted = highlightCode(code, "brainfuck")
        assertEquals(code, highlighted.text)
    }

    @Test
    fun emptyCodeIsEmpty() {
        val highlighted = highlightCode("", "kotlin")
        assertEquals("", highlighted.text)
        assertTrue(highlighted.spanStyles.isEmpty())
    }

    @Test
    fun languageNamesMapCaseInsensitively() {
        assertEquals(SyntaxLanguage.KOTLIN, "Kotlin".toSyntaxLanguage())
        assertEquals(SyntaxLanguage.JAVASCRIPT, "JS".toSyntaxLanguage())
        assertEquals(SyntaxLanguage.CPP, "c++".toSyntaxLanguage())
        assertEquals(SyntaxLanguage.CSHARP, "C#".toSyntaxLanguage())
        assertEquals(SyntaxLanguage.SHELL, "bash".toSyntaxLanguage())
        assertEquals(SyntaxLanguage.DEFAULT, "brainfuck".toSyntaxLanguage())
    }

    @Test
    fun spansStayInsideTheCode() {
        val code = "val greeting = \"hello\" // done"
        val highlighted = highlightCode(code, "kotlin")
        assertTrue(highlighted.spanStyles.all { it.start in 0..it.end && it.end <= code.length })
    }
}
