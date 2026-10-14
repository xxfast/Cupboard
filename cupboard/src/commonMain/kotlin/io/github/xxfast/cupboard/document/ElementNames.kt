package io.github.xxfast.cupboard.document

/** What this kind of element is called, one word: the Object List's row icon keys off it. */
val Element.kindName: String
    get() = when (this) {
        is TextElement -> "Text"
        is ShapeElement -> "Shape"
        is LineElement -> "Line"
        is ImageElement -> "Image"
        is GalleryElement -> "Gallery"
        is GroupElement -> "Group"
        is TerminalElement -> "Terminal"
        is CodeElement -> "Code"
        is DiagramElement -> "Diagram"
        is EquationElement -> "Equation"
        is VideoElement -> "Video"
        is AudioElement -> "Audio"
    }

/**
 * What the Object List calls the element: the user's [Element.name] when they
 * gave it one, otherwise a name read off the content, Keynote-style.
 *
 * A text box goes by its text, a shape by its label or else by what the shape
 * catalog calls its kind, an image by its caption, and the kinds with a title
 * of their own by that title, each cut to its first line that says anything.
 * Everything else, and anything whose content is empty, goes by its [kindName].
 */
val Element.displayName: String
    get() = name?.takeIf { it.isNotBlank() } ?: defaultName

private val Element.defaultName: String
    get() {
        val content: String = when (this) {
            is TextElement -> text
            is ShapeElement -> label.ifBlank { kind.title }
            is ImageElement -> caption
            is TerminalElement -> title
            is VideoElement -> title
            is AudioElement -> title
            else -> ""
        }

        // A row is one line high, so the first line that says anything is the name.
        return content.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: kindName
    }

/**
 * The catalog's name for the kind ("Oval", "Hexagon"), the enum's own where it has
 * none. A [ShapeKind.Path] is the exception: "Path" is how it is stored, not what
 * anyone would call an imported drawing, so it goes by the plain [kindName].
 */
private val ShapeKind.title: String
    get() = when (this) {
        ShapeKind.Path -> "Shape"
        else -> ShapeCatalog.entries
            .filterIsInstance<ShapeCatalogEntry.Shape>()
            .firstOrNull { it.kind == this }
            ?.title
            ?: name
    }
