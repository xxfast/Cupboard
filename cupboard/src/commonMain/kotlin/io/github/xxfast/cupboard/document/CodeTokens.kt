package io.github.xxfast.cupboard.document

/**
 * The kinds a [CodeToken] can be. Deliberately coarse: this is not a syntax
 * highlighter (the renderer has one), it is the grain a morph animates at, and
 * two tokens only ever match when they agree on their kind.
 */
enum class CodeTokenKind { Word, Number, String, Comment, Punctuation, Whitespace }

/**
 * One piece of a code block, as [tokenizeCode] cut it.
 *
 * [line] is 0-based, [start] and [end] are column offsets within that line, end
 * exclusive. A token never spans lines: a block comment or a raw string is cut
 * at every newline, and the newline itself is no token at all, since [line]
 * already carries it. That keeps a morph's unit of animation a thing that has
 * one position on screen.
 *
 * [depth] is bracket nesting, and it is part of what makes a match a match: the
 * `}` that closes a function body is not the `}` that closes a lambda inside it,
 * however identical the two characters are.
 */
data class CodeToken(
    val content: String,
    val kind: CodeTokenKind,
    val depth: Int,
    val line: Int,
    val start: Int,
    val end: Int,
)

/**
 * The languages whose `#` starts a comment. Everywhere else `#` is punctuation,
 * because in C-family code it is an operator or a preprocessor mark rather than
 * a comment, and calling it one would swallow the rest of the line.
 */
private val HashCommentLanguages: Set<String> = setOf(
    "python",
    "ruby",
    "bash",
    "shell",
    "sh",
    "yaml",
    "toml",
    "dockerfile",
    "makefile",
    "r",
    "perl",
)

/** The quotes a string can open with. Backtick included: it is a string in shell and in JS. */
private const val StringDelimiters: String = "\"'`"

/**
 * Cuts [code] into the tokens a morph animates, language-agnostically.
 *
 * Fault tolerant by construction: there is no grammar to violate, so a half
 * written line, an unterminated string, or a language this knows nothing about
 * all come out as tokens rather than as an error. Slides hold snippets, and a
 * snippet is almost never a whole compilable file.
 *
 * [language] is consulted for one thing only, whether `#` opens a comment, and
 * is matched case-insensitively like [CodeElement.language] is everywhere else.
 */
fun tokenizeCode(code: String, language: String = ""): List<CodeToken> {
    if (code.isEmpty()) return emptyList()

    val hashComments: Boolean = language.lowercase() in HashCommentLanguages
    val tokens = mutableListOf<CodeToken>()

    // Carried across lines, which is the whole reason this is not a per-line
    // loop with a fresh scanner each time: a block comment and a raw string are
    // one construct cut into per-line pieces, not a new construct every line.
    var depth: Int = 0
    var inBlockComment: Boolean = false
    var inRawString: Boolean = false

    code.split("\n").forEachIndexed { line, text ->
        var index: Int = 0

        fun take(kind: CodeTokenKind, end: Int, at: Int = depth) {
            tokens += CodeToken(text.substring(index, end), kind, at, line, index, end)
            index = end
        }

        /**
         * Where [close] ends, or -1 when this line does not close what is open:
         * the caller then takes the rest of the line and stays open into the next.
         */
        fun closingAt(close: String, from: Int): Int {
            val found: Int = text.indexOf(close, from)
            return if (found < 0) -1 else found + close.length
        }

        if (inBlockComment) {
            val close: Int = closingAt("*/", 0)
            inBlockComment = close < 0
            val end: Int = if (close < 0) text.length else close
            if (text.isNotEmpty()) take(CodeTokenKind.Comment, end)
        }

        if (inRawString) {
            val close: Int = closingAt("\"\"\"", 0)
            inRawString = close < 0
            val end: Int = if (close < 0) text.length else close
            if (text.isNotEmpty()) take(CodeTokenKind.String, end)
        }

        while (index < text.length) {
            val character: Char = text[index]
            val rest: String = text.substring(index)

            when {
                character.isWhitespace() -> {
                    var end: Int = index
                    while (end < text.length && text[end].isWhitespace()) end += 1
                    take(CodeTokenKind.Whitespace, end)
                }

                rest.startsWith("\"\"\"") -> {
                    val close: Int = closingAt("\"\"\"", index + 3)
                    inRawString = close < 0
                    take(CodeTokenKind.String, if (close < 0) text.length else close)
                }

                rest.startsWith("//") || (hashComments && character == '#') ->
                    take(CodeTokenKind.Comment, text.length)

                rest.startsWith("/*") -> {
                    val close: Int = closingAt("*/", index + 2)
                    inBlockComment = close < 0
                    take(CodeTokenKind.Comment, if (close < 0) text.length else close)
                }

                character in StringDelimiters -> {
                    // An escape takes the next character with it, so `"\""` is
                    // one token. An unterminated run stops at the newline rather
                    // than eating the rest of the block.
                    var end: Int = index + 1
                    while (end < text.length && text[end] != character) {
                        end += if (text[end] == '\\') 2 else 1
                    }
                    take(CodeTokenKind.String, minOf(end + 1, text.length))
                }

                character.isDigit() -> {
                    // Letters and underscores ride along, so `0xFF`, `1_000` and
                    // `1.5f` are each one number. A dot only counts when a digit
                    // follows it, which keeps `1..5` a number, a range, a number.
                    var end: Int = index + 1
                    while (end < text.length) {
                        val next: Char = text[end]
                        val decimal: Boolean =
                            next == '.' && end + 1 < text.length && text[end + 1].isDigit()
                        if (!next.isLetterOrDigit() && next != '_' && !decimal) break
                        end += 1
                    }
                    take(CodeTokenKind.Number, end)
                }

                character.isLetter() || character == '_' || character == '$' -> {
                    var end: Int = index + 1
                    while (end < text.length) {
                        val next: Char = text[end]
                        if (!next.isLetterOrDigit() && next != '_' && next != '$') break
                        end += 1
                    }
                    take(CodeTokenKind.Word, end)
                }

                character == '(' || character == '[' || character == '{' -> {
                    // The opener carries the depth outside it and the closer
                    // decrements before it takes one, so a pair shares a depth
                    // and the body between them sits one deeper.
                    take(CodeTokenKind.Punctuation, index + 1)
                    depth += 1
                }

                character == ')' || character == ']' || character == '}' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    take(CodeTokenKind.Punctuation, index + 1)
                }

                // One token per character: `->` is two. Splitting operators by
                // language is a rabbit hole, and a morph reads the same either
                // way, since the two halves move together.
                else -> take(CodeTokenKind.Punctuation, index + 1)
            }
        }
    }

    return tokens
}
