package io.github.xxfast.cupboard.document

/**
 * One piece of parsed LaTeX: the tree a renderer walks to lay an equation out.
 *
 * Deliberately a box model rather than a syntax tree. Every node here is
 * something with a width, an ascent and a descent, which is what the canvas
 * needs and all it needs: nothing carries a source range, and nothing carries a
 * position. Where a node lands is the layout's business, and the layout is a
 * pure function of this tree, so it is the same on every target.
 *
 * The kinds a renderer can't guess at from a string are the ones with their own
 * node: a [Fraction] stacks, a [Root] draws a radical, a [BigOperator] moves its
 * limits when the style changes. Everything else collapses into a leaf that
 * knows whether it is set upright or italic, which is the whole of what
 * distinguishes a variable from a function name on a slide.
 */
sealed interface MathNode {
    /** Boxes set left to right, sharing a baseline. */
    data class Row(val children: List<MathNode>) : MathNode

    /** A variable, digit or bracket. Italic is what makes it read as a variable. */
    data class Symbol(val text: String, val italic: Boolean = false) : MathNode

    /** A binary or relational sign, which is a [Symbol] with air either side of it. */
    data class Operator(val text: String) : MathNode

    /** [rule] off is `\binom`: the same stack without the line between its halves. */
    data class Fraction(
        val numerator: MathNode,
        val denominator: MathNode,
        val rule: Boolean = true,
    ) : MathNode

    data class Root(val body: MathNode, val index: MathNode? = null) : MathNode

    /** Both scripts on one node, so `x^2_i` and `x_i^2` are the same equation. */
    data class Scripts(
        val base: MathNode,
        val superscript: MathNode? = null,
        val subscript: MathNode? = null,
    ) : MathNode

    /**
     * A sum, product, integral or limit, holding its own limits rather than
     * wearing them as [Scripts]: where they sit is a function of the style, and
     * only a node that knows it is an operator can move them.
     */
    data class BigOperator(
        val symbol: String,
        val upper: MathNode? = null,
        val lower: MathNode? = null,
    ) : MathNode

    /** `\left ... \right`, with "." for the side that draws nothing. */
    data class Delimited(val left: String, val body: MathNode, val right: String) : MathNode

    /** Upright prose inside an equation: `\text`, `\mathrm`, `\mathbf`. */
    data class Text(val text: String, val bold: Boolean = false) : MathNode

    /** An upright operator name, `\sin` and its kind, set with a thin space after it. */
    data class Function(val name: String) : MathNode

    /** [kind] is one of hat, bar, vec, dot, ddot, tilde. */
    data class Accent(val kind: String, val base: MathNode) : MathNode

    /** Width and nothing else, in ems of the size it appears at. */
    data class Space(val em: Float) : MathNode
}

/**
 * [latex] as a tree, for the LaTeX subset a slide needs.
 *
 * Never throws, and that is the point: a deck is edited live in front of an
 * audience, so a typo has to cost a glyph rather than the slide. An unknown
 * `\command` comes back as the literal text of the command, an unbalanced `{`
 * closes at the end of the input, and a `}` with nothing open is dropped.
 *
 * Single-line only: `\\` and `&` are read and ignored, so a pasted matrix comes
 * out as one long row rather than as nothing at all. `%` starts a comment.
 */
fun parseMath(latex: String): MathNode = MathScanner(latex).parse()

/** Lowercase greek is set italic like any other variable; uppercase is upright. */
private val GreekLetters: Map<String, String> = mapOf(
    "alpha" to "α",
    "beta" to "β",
    "gamma" to "γ",
    "delta" to "δ",
    "epsilon" to "ε",
    "varepsilon" to "ε",
    "zeta" to "ζ",
    "eta" to "η",
    "theta" to "θ",
    "vartheta" to "ϑ",
    "iota" to "ι",
    "kappa" to "κ",
    "lambda" to "λ",
    "mu" to "μ",
    "nu" to "ν",
    "xi" to "ξ",
    "omicron" to "ο",
    "pi" to "π",
    "rho" to "ρ",
    "sigma" to "σ",
    "tau" to "τ",
    "upsilon" to "υ",
    "phi" to "φ",
    "varphi" to "ϕ",
    "chi" to "χ",
    "psi" to "ψ",
    "omega" to "ω",
    "Gamma" to "Γ",
    "Delta" to "Δ",
    "Theta" to "Θ",
    "Lambda" to "Λ",
    "Xi" to "Ξ",
    "Pi" to "Π",
    "Sigma" to "Σ",
    "Phi" to "Φ",
    "Psi" to "Ψ",
    "Omega" to "Ω",
)

/** The greek that stays upright: the capitals, per the convention every paper uses. */
private val UprightGreek: Set<String> = setOf(
    "Gamma", "Delta", "Theta", "Lambda", "Xi", "Pi", "Sigma", "Phi", "Psi", "Omega",
)

/** Everything that maps to one glyph and carries no structure. */
private val NamedSymbols: Map<String, String> = mapOf(
    "cdot" to "⋅",
    "times" to "×",
    "div" to "÷",
    "pm" to "±",
    "mp" to "∓",
    "leq" to "≤",
    "le" to "≤",
    "geq" to "≥",
    "ge" to "≥",
    "neq" to "≠",
    "ne" to "≠",
    "approx" to "≈",
    "equiv" to "≡",
    "sim" to "∼",
    "propto" to "∝",
    "infty" to "∞",
    "partial" to "∂",
    "nabla" to "∇",
    "to" to "→",
    "rightarrow" to "→",
    "leftarrow" to "←",
    "Rightarrow" to "⇒",
    "Leftrightarrow" to "⇔",
    "mapsto" to "↦",
    "in" to "∈",
    "notin" to "∉",
    "subset" to "⊂",
    "subseteq" to "⊆",
    "supset" to "⊃",
    "cup" to "∪",
    "cap" to "∩",
    "forall" to "∀",
    "exists" to "∃",
    "emptyset" to "∅",
    "ldots" to "…",
    "cdots" to "⋯",
    "vdots" to "⋮",
    "ddots" to "⋱",
    "prime" to "′",
    "circ" to "∘",
    "angle" to "∠",
    "degree" to "°",
    "hbar" to "ℏ",
    "ell" to "ℓ",
)

/** The [NamedSymbols] that bind two things together, and so get air either side. */
private val NamedOperators: Set<String> = setOf(
    "cdot", "times", "div", "pm", "mp", "leq", "le", "geq", "ge", "neq", "ne", "approx",
    "equiv", "sim", "propto", "to", "rightarrow", "leftarrow", "Rightarrow",
    "Leftrightarrow", "mapsto", "in", "notin", "subset", "subseteq", "supset", "cup",
    "cap", "circ",
)

/** Operator names set upright, the ones LaTeX ships a command for. */
private val FunctionNames: Set<String> = setOf(
    "sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan", "sinh",
    "cosh", "tanh", "log", "ln", "lg", "exp", "min", "max", "det", "dim", "gcd", "sup",
    "inf", "arg", "deg", "ker", "Pr",
)

/** The operators that take limits, and the glyph each is set in. */
private val BigOperatorGlyphs: Map<String, String> = mapOf(
    "sum" to "∑",
    "prod" to "∏",
    "int" to "∫",
    "oint" to "∮",
    "lim" to "lim",
)

/** The spacing commands, in ems. `\!` is the negative one, and pulls back. */
private val SpaceCommands: Map<String, Float> = mapOf(
    "," to 0.17f,
    ":" to 0.22f,
    ";" to 0.28f,
    "!" to -0.17f,
    "quad" to 1f,
    "qquad" to 2f,
    " " to 0.33f,
)

/** Characters that only mean themselves once they are escaped. */
private val EscapedCharacters: Set<String> = setOf("{", "}", "%", "&", "_", "$", "#")

/** What `\hat` and its kind draw, folded onto the six marks the canvas knows. */
private val AccentKinds: Map<String, String> = mapOf(
    "hat" to "hat",
    "widehat" to "hat",
    "bar" to "bar",
    "overline" to "bar",
    "vec" to "vec",
    "dot" to "dot",
    "ddot" to "ddot",
    "tilde" to "tilde",
    "widetilde" to "tilde",
)

/** The delimiters `\left` and `\right` name by command rather than by character. */
private val NamedDelimiters: Map<String, String> = mapOf(
    "|" to "‖",
    "langle" to "⟨",
    "rangle" to "⟩",
    "lfloor" to "⌊",
    "rfloor" to "⌋",
    "lceil" to "⌈",
    "rceil" to "⌉",
    "{" to "{",
    "}" to "}",
)

/** The single characters that bind, and so are set as operators rather than glyphs. */
private const val OperatorCharacters: String = "+-=<>*/"

/** `~` is a space in LaTeX, and the one that doesn't start with a backslash. */
private const val TildeSpace: Float = 0.33f

/**
 * A cursor over the source.
 *
 * One pass, no token list: every construct here is decided by the character
 * under the cursor and at most one command name after it, so a separate lexer
 * would be a second thing to keep in step with this for nothing in return.
 *
 * [depth] is how many `{` are open. It is what tells a `}` apart from a stray
 * one, which is the difference between closing a group and dropping a typo.
 */
private class MathScanner(private val source: String) {
    private var at: Int = 0

    fun parse(): MathNode = row(depth = 0)

    private fun row(depth: Int): MathNode {
        val items: MutableList<MathNode> = mutableListOf()
        while (at < source.length) {
            if (source[at] == '}') {
                if (depth > 0) break
                at++
                continue
            }

            if (commandAt(at) == "right") break

            val atom: MathNode = atom(depth) ?: continue
            items += scripted(atom, depth)
        }

        return items.toRow()
    }

    /**
     * The command name at [from], without its backslash, or null if there is no
     * command there. Doesn't move the cursor: `\right` has to be recognised from
     * inside a row it is not part of, and `\rightarrow` must not be mistaken for
     * it, which is what reading the whole name buys over matching a prefix.
     */
    private fun commandAt(from: Int): String? {
        if (from >= source.length || source[from] != '\\') return null
        if (from + 1 >= source.length) return null
        if (!source[from + 1].isLetter()) return source.substring(from + 1, from + 2)

        var end: Int = from + 1
        while (end < source.length && source[end].isLetter()) end++
        return source.substring(from + 1, end)
    }

    /** One atom, or null for input that carries no box: a space, a comment, a `&`. */
    private fun atom(depth: Int): MathNode? {
        val char: Char = source[at]
        return when {
            char.isWhitespace() -> {
                at++
                null
            }

            char == '%' -> {
                while (at < source.length && source[at] != '\n') at++
                null
            }

            // Single-line for now, so an alignment tab and a row break are read
            // and dropped rather than left to end the parse early.
            char == '&' -> {
                at++
                null
            }

            char == '~' -> {
                at++
                MathNode.Space(TildeSpace)
            }

            char == '{' -> {
                at++
                val body: MathNode = row(depth + 1)
                if (at < source.length && source[at] == '}') at++
                body
            }

            // A script with nothing in front of it still parses: the empty row is
            // the base, and `scripted` hangs the script off it.
            char == '^' || char == '_' -> MathNode.Row(emptyList())

            char == '\\' -> command(depth)

            char.isDigit() -> number()

            char.isLetter() -> {
                at++
                MathNode.Symbol(char.toString(), italic = true)
            }

            char in OperatorCharacters -> {
                at++
                MathNode.Operator(operatorGlyph(char))
            }

            else -> {
                at++
                MathNode.Symbol(char.toString())
            }
        }
    }

    /** A whole number, decimal point and all, so its digits are kerned as one word. */
    private fun number(): MathNode {
        val start: Int = at
        while (at < source.length && source[at].isDigit()) at++
        while (at + 1 < source.length && source[at] == '.' && source[at + 1].isDigit()) {
            at++
            while (at < source.length && source[at].isDigit()) at++
        }
        return MathNode.Symbol(source.substring(start, at))
    }

    /**
     * [base] with whatever scripts follow it, in whichever order they were
     * written: `x^2_i` and `x_i^2` are one node either way, and a repeated script
     * is the last one written rather than an error.
     */
    private fun scripted(base: MathNode, depth: Int): MathNode {
        var superscript: MathNode? = null
        var subscript: MathNode? = null

        while (true) {
            skipSpace()
            if (at >= source.length) break
            val char: Char = source[at]
            if (char != '^' && char != '_') break

            at++
            if (char == '^') superscript = argument(depth) else subscript = argument(depth)
        }

        if (superscript == null && subscript == null) return base

        // A big operator keeps its limits in its own slots: where they are drawn
        // depends on the style, and only the operator node carries that.
        if (base is MathNode.BigOperator) {
            return base.copy(
                upper = superscript ?: base.upper,
                lower = subscript ?: base.lower,
            )
        }

        return MathNode.Scripts(base, superscript, subscript)
    }

    /** One argument: a braced group, or the single atom that follows. */
    private fun argument(depth: Int): MathNode {
        skipSpace()
        if (at >= source.length) return MathNode.Row(emptyList())

        if (source[at] == '{') {
            at++
            val body: MathNode = row(depth + 1)
            if (at < source.length && source[at] == '}') at++
            return body
        }

        return atom(depth) ?: MathNode.Row(emptyList())
    }

    /**
     * One argument as literal text, braces balanced but nothing else read.
     *
     * `\text{d x}` keeps its space and `\text{-1}` keeps its hyphen, because
     * what is inside prose is prose: the moment it is parsed as math it stops
     * being the thing that was typed.
     */
    private fun rawArgument(): String {
        skipSpace()
        if (at >= source.length) return ""

        if (source[at] != '{') {
            val name: String? = commandAt(at)
            if (name == null) return source[at++].toString()
            at += 1 + name.length
            return name
        }

        at++
        val start: Int = at
        var open: Int = 1
        while (at < source.length) {
            when (source[at]) {
                '{' -> open++
                '}' -> {
                    open--
                    if (open == 0) break
                }
            }
            at++
        }

        val text: String = source.substring(start, at)
        if (at < source.length) at++
        return text
    }

    private fun command(depth: Int): MathNode? {
        val name: String = commandAt(at) ?: run {
            at++
            return null
        }
        at += 1 + name.length

        // A row break, on a single line: read so it can't derail the parse.
        if (name == "\\") return null

        SpaceCommands[name]?.let { return MathNode.Space(it) }
        if (name in EscapedCharacters) return MathNode.Symbol(name)
        AccentKinds[name]?.let { return MathNode.Accent(it, argument(depth)) }
        BigOperatorGlyphs[name]?.let { return MathNode.BigOperator(it) }
        GreekLetters[name]?.let { return MathNode.Symbol(it, italic = name !in UprightGreek) }
        NamedSymbols[name]?.let {
            return if (name in NamedOperators) MathNode.Operator(it) else MathNode.Symbol(it)
        }
        NamedDelimiters[name]?.let { return MathNode.Symbol(it) }
        if (name in FunctionNames) return MathNode.Function(name)

        return when (name) {
            // Display and text fractions are the same stack here: which one it is
            // is the style it is laid out in, which the canvas already knows.
            "frac", "dfrac", "tfrac" -> MathNode.Fraction(argument(depth), argument(depth))

            "binom" -> MathNode.Delimited(
                left = "(",
                body = MathNode.Fraction(argument(depth), argument(depth), rule = false),
                right = ")",
            )

            "sqrt" -> root(depth)
            "left" -> delimited(depth)

            // A `\right` with nothing open: dropped, the way a stray `}` is.
            "right" -> null

            "text", "mathrm" -> MathNode.Text(rawArgument())
            "mathbf" -> MathNode.Text(rawArgument(), bold = true)
            "mathit" -> MathNode.Symbol(rawArgument(), italic = true)
            "operatorname" -> MathNode.Function(rawArgument())

            // The whole point of never throwing: a command nobody taught this
            // parser draws as itself, so the typo is on the slide to be seen.
            else -> MathNode.Text("\\$name")
        }
    }

    private fun root(depth: Int): MathNode {
        skipSpace()
        var index: MathNode? = null

        if (at < source.length && source[at] == '[') {
            at++
            val close: Int = source.indexOf(']', at)
            val inner: String = if (close < 0) source.substring(at) else source.substring(at, close)
            at = if (close < 0) source.length else close + 1
            index = parseMath(inner)
        }

        return MathNode.Root(argument(depth), index)
    }

    private fun delimited(depth: Int): MathNode {
        val left: String = delimiter()
        val body: MathNode = row(depth)

        // An unclosed `\left` closes on nothing at the end of the input, which is
        // the same thing `\right.` asks for.
        val right: String = if (commandAt(at) != "right") "." else {
            at += "\\right".length
            delimiter()
        }

        return MathNode.Delimited(left, body, right)
    }

    private fun delimiter(): String {
        skipSpace()
        if (at >= source.length) return "."

        val name: String? = commandAt(at)
        if (name == null) return source[at++].toString()

        at += 1 + name.length
        return NamedDelimiters[name] ?: name
    }

    private fun skipSpace() {
        while (at < source.length && source[at].isWhitespace()) at++
    }
}

/** A hyphen is not a minus sign, and on a slide the difference is visible. */
private fun operatorGlyph(char: Char): String = when (char) {
    '-' -> "−"
    '*' -> "∗"
    else -> char.toString()
}

/** One box stays itself: a row of one is the thing it holds. */
private fun List<MathNode>.toRow(): MathNode =
    if (size == 1) single() else MathNode.Row(this)
