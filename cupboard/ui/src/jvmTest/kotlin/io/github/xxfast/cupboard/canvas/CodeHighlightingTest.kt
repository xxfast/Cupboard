package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import dev.snipme.highlights.model.SyntaxLanguage
import io.github.xxfast.cupboard.document.CodeLanguages
import io.github.xxfast.cupboard.document.CodeTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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

    @Test
    fun everyOfferedLanguageResolves() {
        for (language in CodeLanguages) {
            val expected = if (language == "Plain") SyntaxLanguage.DEFAULT else null
            val resolved = language.toSyntaxLanguage()
            if (expected != null) assertEquals(expected, resolved, language)
            else assertNotEquals(SyntaxLanguage.DEFAULT, resolved, language)
        }
    }

    @Test
    fun everyThemeHighlightsTheSameCodeVerbatim() {
        val code = "fun main() {\n    val x = 1 // comment\n}"
        for (theme in CodeTheme.entries) {
            val highlighted = highlightCode(code, "kotlin", theme)
            assertEquals(code, highlighted.text, theme.name)
            assertTrue(highlighted.spanStyles.any { it.item.color.isSpecified }, theme.name)
        }
    }

    @Test
    fun themesColorTheirKeywordsDifferently() {
        val code = "fun main() {}"
        val atom = highlightCode(code, "kotlin", CodeTheme.Atom).spanStyles
        val monokai = highlightCode(code, "kotlin", CodeTheme.Monokai).spanStyles
        assertNotEquals(atom, monokai)
    }

    @Test
    fun everyThemeDressesItsOwnBlock() {
        val chromes = CodeTheme.entries.map { it.chrome }
        assertEquals(chromes.distinct(), chromes)
        // Atom is what the code box has always drawn in, and stays that way.
        assertEquals(Color(0xFF14151F), CodeTheme.Atom.chrome.background)
        assertEquals(Color(0xFF33363D), CodeTheme.Atom.chrome.border)
        assertTrue(chromes.all { it.background != it.text && it.gutter != it.text })
    }
}
