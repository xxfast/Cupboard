package io.github.xxfast.cupboard.export

/**
 * Writes Kotlin source a line at a time, four spaces per level, and collects the
 * imports the lines it was handed need.
 *
 * The imports ride along with the writer rather than being listed per file,
 * because what an element emits is what decides them: a deck with no code block
 * has no business importing `FontFamily`.
 */
internal class SourceWriter {
    private val text = StringBuilder()
    private var depth: Int = 0

    /** Fully-qualified names this source needs, deduplicated. Sorted on the way out. */
    val imports: MutableSet<String> = mutableSetOf()

    fun import(vararg names: String) {
        imports += names
    }

    /** One line at the current depth. Empty writes a blank line, with no trailing spaces. */
    fun line(text: String = "") {
        if (text.isEmpty()) {
            this.text.append('\n')
            return
        }
        repeat(depth) { this.text.append("    ") }
        this.text.append(text).append('\n')
    }

    /** [body], one level deeper. */
    fun indented(body: () -> Unit) {
        depth++
        body()
        depth--
    }

    /** [open], then [body] one level deeper, then [close]. */
    fun block(open: String, close: String = "}", body: () -> Unit) {
        line(open)
        depth++
        body()
        depth--
        line(close)
    }

    override fun toString(): String = text.toString()
}

/**
 * A whole file: the package line, the writer's imports sorted, and its body.
 *
 * Every generated file ends in a newline, which [SourceWriter.line] already
 * guarantees for anything with a body.
 */
internal fun SourceWriter.toFile(packageName: String): String {
    val header = StringBuilder()
    header.append("package ").append(packageName).append("\n\n")
    if (imports.isNotEmpty()) {
        for (name in imports.sorted()) header.append("import ").append(name).append("\n")
        header.append("\n")
    }
    return header.append(toString()).toString()
}

/**
 * [text] as a Kotlin string literal, quotes included.
 *
 * Always a plain literal rather than a raw one: code and terminal transcripts are
 * the multi-line strings here, and either can hold the three quotes that would
 * end a raw one.
 */
internal fun kotlinString(text: String): String {
    val escaped = StringBuilder("\"")
    for (char in text) {
        when (char) {
            '\\' -> escaped.append("\\\\")
            '"' -> escaped.append("\\\"")
            '$' -> escaped.append("\\$")
            '\n' -> escaped.append("\\n")
            '\r' -> escaped.append("\\r")
            '\t' -> escaped.append("\\t")
            else -> escaped.append(char)
        }
    }
    return escaped.append('"').toString()
}

/** This float as a literal. Negatives come parenthesised, so `(-1.0f).sp` parses. */
internal fun Float.literal(): String = if (this < 0f) "(${this}f)" else "${this}f"

/** A packed ARGB colour as a hex literal. `L`-suffixed: half of them fit in an Int. */
internal fun Long.colorLiteral(): String = "0x" + toString(16).uppercase().padStart(8, '0') + "L"
