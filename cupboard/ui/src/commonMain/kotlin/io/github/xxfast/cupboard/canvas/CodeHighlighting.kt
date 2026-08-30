package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxTheme
import dev.snipme.highlights.model.SyntaxThemes
import io.github.xxfast.cupboard.document.CodeTheme

/**
 * The block a theme is drawn on: its background, its outline, the colour the
 * spans the highlighter said nothing about fall back to, and the gutter's.
 *
 * `design/` defines no code token palette, so each of these is the theme's own
 * editor chrome rather than anything of ours, which is what makes a picked theme
 * read as itself rather than as Atom wearing different keywords.
 */
internal data class CodeChrome(
    val background: Color,
    val border: Color,
    val text: Color,
    val gutter: Color,
)

/**
 * Always dark mode: the slide stays dark in both app themes, so the light
 * variants would highlight against a background nothing here ever draws.
 * [CodeTheme.Notepad] is the exception the pale [CodeChrome.background] is for.
 */
private fun CodeTheme.syntaxTheme(): SyntaxTheme = when (this) {
    CodeTheme.Atom -> SyntaxThemes.atom(darkMode = true)
    CodeTheme.Darcula -> SyntaxThemes.darcula(darkMode = true)
    CodeTheme.Monokai -> SyntaxThemes.monokai(darkMode = true)
    CodeTheme.Pastel -> SyntaxThemes.pastel(darkMode = true)
    CodeTheme.Matrix -> SyntaxThemes.matrix(darkMode = true)
    CodeTheme.Notepad -> SyntaxThemes.notepad(darkMode = true)
}

/** Atom keeps the colours the code box has always drawn in; the rest come from their editors. */
internal val CodeTheme.chrome: CodeChrome get() = when (this) {
    CodeTheme.Atom -> CodeChrome(
        background = Color(0xFF14151F),
        border = Color(0xFF33363D),
        text = Color(0xFFD9CFFF),
        gutter = Color(0xFF5C6370),
    )

    CodeTheme.Darcula -> CodeChrome(
        background = Color(0xFF2B2B2B),
        border = Color(0xFF4E5254),
        text = Color(0xFFEDEDED),
        gutter = Color(0xFF606366),
    )

    CodeTheme.Monokai -> CodeChrome(
        background = Color(0xFF272822),
        border = Color(0xFF49483E),
        text = Color(0xFFF8F8F2),
        gutter = Color(0xFF75715E),
    )

    CodeTheme.Pastel -> CodeChrome(
        background = Color(0xFF2E3436),
        border = Color(0xFF555753),
        text = Color(0xFFDFDEE0),
        gutter = Color(0xFF888A85),
    )

    CodeTheme.Matrix -> CodeChrome(
        background = Color(0xFF000000),
        border = Color(0xFF00591F),
        text = Color(0xFF008500),
        gutter = Color(0xFF004D00),
    )

    CodeTheme.Notepad -> CodeChrome(
        background = Color(0xFFFDFDF6),
        border = Color(0xFFBFBFB4),
        text = Color(0xFF000080),
        gutter = Color(0xFF9A9A90),
    )
}

/** Syntax-highlights [code] as [language] in [theme], falling back to no-language highlighting. */
internal fun highlightCode(
    code: String,
    language: String,
    theme: CodeTheme = CodeTheme.Atom,
): AnnotatedString {
    if (code.isEmpty()) return AnnotatedString("")

    val highlights = Highlights.Builder(
        code = code,
        language = language.toSyntaxLanguage(),
        theme = theme.syntaxTheme(),
    ).build().getHighlights()

    return buildAnnotatedString {
        append(code)
        for (highlight in highlights) {
            val start = highlight.location.start.coerceIn(0, code.length)
            val end = highlight.location.end.coerceIn(start, code.length)
            if (start == end) continue
            val style = when (highlight) {
                is ColorHighlight -> SpanStyle(color = Color(highlight.rgb.toLong() or 0xFF000000))
                is BoldHighlight -> SpanStyle(fontWeight = FontWeight.Bold)
            }
            addStyle(style, start, end)
        }
    }
}

/** Maps a code element's free-form `language` string onto the engine's languages, case-insensitively. */
internal fun String.toSyntaxLanguage(): SyntaxLanguage = when (lowercase().trim()) {
    "kotlin", "kt", "kts" -> SyntaxLanguage.KOTLIN
    "swift" -> SyntaxLanguage.SWIFT
    "java" -> SyntaxLanguage.JAVA
    "javascript", "js" -> SyntaxLanguage.JAVASCRIPT
    "typescript", "ts" -> SyntaxLanguage.TYPESCRIPT
    "python", "py" -> SyntaxLanguage.PYTHON
    "rust", "rs" -> SyntaxLanguage.RUST
    "c" -> SyntaxLanguage.C
    "cpp", "c++" -> SyntaxLanguage.CPP
    "csharp", "c#" -> SyntaxLanguage.CSHARP
    "go" -> SyntaxLanguage.GO
    "dart" -> SyntaxLanguage.DART
    "php" -> SyntaxLanguage.PHP
    "ruby", "rb" -> SyntaxLanguage.RUBY
    "shell", "bash", "sh", "zsh" -> SyntaxLanguage.SHELL
    "perl" -> SyntaxLanguage.PERL
    "coffeescript", "coffee" -> SyntaxLanguage.COFFEESCRIPT
    else -> SyntaxLanguage.DEFAULT
}
