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

/**
 * Atom One Dark. `design/` defines no code token palette, so we take the built-in that reads
 * best on the code box background (`0xFF14151F`). Unhighlighted spans keep the caller's text
 * color rather than the theme's `code` value.
 */
private val CodeTheme: SyntaxTheme = SyntaxThemes.atom(darkMode = true)

/** Syntax-highlights [code] as [language], falling back to no-language highlighting. */
internal fun highlightCode(code: String, language: String): AnnotatedString {
    if (code.isEmpty()) return AnnotatedString("")

    val highlights = Highlights.Builder(
        code = code,
        language = language.toSyntaxLanguage(),
        theme = CodeTheme,
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
