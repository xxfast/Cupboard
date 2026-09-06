package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.Slide

/**
 * A deck as a PDF, written by hand: no library, because `:cupboard` carries
 * mingwX64 and every shell must produce the same file.
 *
 * The document written is PDF 1.4 with uncompressed content streams and one
 * `/FlateDecode` image per slide, which is the smallest shape of file every
 * reader takes. Text is Helvetica, the one font a reader is required to have, so
 * nothing has to be embedded.
 */

/** One image on a page: where it goes, and the pixels that go there. */
data class PdfImage(
    /**
     * The box in points, measured from the *top left* of the page, the way
     * everything else in this codebase measures a frame. PDF's own origin is the
     * bottom left, and the flip happens here rather than at every call site.
     */
    val frame: Frame,
    val raster: RasterFrame,
    /**
     * What the image's transparency resolves against, packed RGB. Black, because
     * a slide is dark and a slide's own corners are the only transparent pixels
     * an export sees. White for anything drawn on the page itself.
     */
    val background: Int = 0x000000,
)

/** One line of Helvetica on a page: a note, or a label under a handout frame. */
data class PdfText(
    val x: Float,
    /** The baseline, measured down from the top of the page. */
    val y: Float,
    val size: Float,
    val text: String,
)

/** One page: its size in points, and what is on it, images under text. */
data class PdfPage(
    val width: Float,
    val height: Float,
    val images: List<PdfImage> = emptyList(),
    val texts: List<PdfText> = emptyList(),
)

/** Letter, landscape: what a handout is printed on. */
private const val PageWidth: Float = 792f
private const val PageHeight: Float = 612f

/** The margin around a page's grid, and the gap between its cells. */
private const val PageMargin: Float = 36f
private const val CellGutter: Float = 18f

/** Type sizes: the label under a handout frame, and the notes under a slide. */
private const val LabelSize: Float = 8f
private const val NotesSize: Float = 10f

/** Helvetica's average advance, near enough to wrap notes on without measuring. */
private const val AverageAdvance: Float = 0.5f

/**
 * How many slides go on a page, and how they sit there.
 *
 * [OneWithNotes] is one per page like [OnePerPage], but with the slide pushed up
 * to make room for the speaker notes underneath, which is the layout a presenter
 * prints for themselves rather than for the room.
 */
enum class HandoutLayout(val columns: Int, val rows: Int) {
    OnePerPage(1, 1),
    TwoPerPage(1, 2),
    FourPerPage(2, 2),
    SixPerPage(2, 3),
    OneWithNotes(1, 1),
}

/** How many frames one page of this layout holds. */
val HandoutLayout.perPage: Int get() = columns * rows

/**
 * What a PDF export covers and how it is laid out.
 *
 * [everyBuild] writes a page's worth per *step* rather than per slide, which is
 * the handout of a deck whose builds carry the argument. [skippedSlides] puts the
 * slides the presenter took out back in, for the times the PDF is the archive
 * rather than the handout.
 */
data class PdfOptions(
    val everyBuild: Boolean = false,
    val layout: HandoutLayout = HandoutLayout.OnePerPage,
    val includeNotes: Boolean = false,
    val skippedSlides: Boolean = false,
)

/**
 * The deck as a PDF: every slide drawn by [rasterizer] and laid out per
 * [options].
 *
 * [PdfOptions.layout] decides the grid, and a deck of nine slides four to a page
 * is three pages, the last one part empty. Notes are written under the slide for
 * [HandoutLayout.OneWithNotes], and under each frame in a grid when
 * [PdfOptions.includeNotes] asks for them.
 */
fun exportPdf(
    document: Document,
    rasterizer: Rasterizer,
    options: PdfOptions = PdfOptions(),
): ByteArray {
    val frames: List<ExportFrame> = document.exportFrames(options.everyBuild, options.skippedSlides)
    val notes: Boolean = options.includeNotes || options.layout == HandoutLayout.OneWithNotes
    val labelled: Boolean = options.layout.perPage > 1

    val pages: List<PdfPage> = frames.chunked(options.layout.perPage).map { onPage ->
        val images = mutableListOf<PdfImage>()
        val texts = mutableListOf<PdfText>()

        for ((position, frame) in onPage.withIndex()) {
            val slide: Slide = document.slides[frame.slideIndex]
            val cell: Frame = cellFrame(options.layout, position)
            val text: String = if (notes) slide.notes else ""
            // The picture takes the whole cell when nothing is written under it,
            // and the top of it when something is.
            val pictureHeight: Float = when {
                text.isEmpty() && !labelled -> cell.height
                options.layout == HandoutLayout.OneWithNotes -> cell.height * 0.62f
                text.isEmpty() -> cell.height - LabelSize * 2
                else -> cell.height * 0.7f
            }

            val picture: Frame = fitted(
                cell.copy(height = pictureHeight),
                document.slideWidth / document.slideHeight,
            )
            images += PdfImage(picture, rasterizer.frame(frame.slideIndex, frame.step))

            var baseline: Float = picture.y + picture.height + LabelSize * 1.6f
            if (labelled) {
                texts += PdfText(picture.x, baseline, LabelSize, label(frame, options.everyBuild))
                baseline += LabelSize * 1.6f
            }

            if (text.isEmpty()) continue
            val size: Float = if (options.layout == HandoutLayout.OneWithNotes) NotesSize else LabelSize
            val room: Int = ((cell.y + cell.height - baseline) / (size * 1.35f)).toInt()
            val columns: Int = (cell.width / (size * AverageAdvance)).toInt()
            for (line in wrapped(text, maxOf(8, columns)).take(maxOf(0, room))) {
                texts += PdfText(cell.x, baseline, size, line)
                baseline += size * 1.35f
            }
        }

        PdfPage(PageWidth, PageHeight, images, texts)
    }

    return pdfDocument(pages.ifEmpty { listOf(PdfPage(PageWidth, PageHeight)) })
}

/**
 * The pages as a PDF file: catalog, page tree, one Helvetica, and a content
 * stream and an image XObject per page.
 *
 * Objects are written in the order they are numbered and the cross-reference
 * table records where each one landed, so the file opens by seeking rather than
 * by scanning.
 */
fun pdfDocument(pages: List<PdfPage>): ByteArray {
    // 1 catalog, 2 page tree, 3 Helvetica, then a page, its content stream and
    // its images, page by page.
    val catalog = 1
    val tree = 2
    val font = 3
    var next = 4
    val pageNumbers: List<Int> = pages.map { next++ }
    val contentNumbers: List<Int> = pages.map { next++ }
    val imageNumbers: List<List<Int>> = pages.map { page -> page.images.map { next++ } }
    val count: Int = next - 1

    val out = ByteSink(1 shl 16)
    val offsets = IntArray(count + 1)
    out.text("%PDF-1.4\n")
    // A comment of high bytes, which is how a reader is told the file is binary
    // and must not be line-ending translated on the way through.
    out.bytes(byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))

    fun open(number: Int) {
        offsets[number] = out.size
        out.text("$number 0 obj\n")
    }

    fun close() {
        out.text("\nendobj\n")
    }

    open(catalog)
    out.text("<< /Type /Catalog /Pages $tree 0 R >>")
    close()

    open(tree)
    out.text("<< /Type /Pages /Count ${pages.size} /Kids [")
    out.text(pageNumbers.joinToString(" ") { "$it 0 R" })
    out.text("] >>")
    close()

    open(font)
    out.text("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
    close()

    for ((index, page) in pages.withIndex()) {
        val images: List<Int> = imageNumbers[index]
        val resources: String = buildString {
            append("<< /Font << /F1 $font 0 R >>")
            if (images.isNotEmpty()) {
                append(" /XObject << ")
                images.forEachIndexed { at, number -> append("/Im$at $number 0 R ") }
                append(">>")
            }
            append(" >>")
        }

        open(pageNumbers[index])
        out.text(
            "<< /Type /Page /Parent $tree 0 R" +
                " /MediaBox [0 0 ${number(page.width)} ${number(page.height)}]" +
                " /Resources $resources /Contents ${contentNumbers[index]} 0 R >>",
        )
        close()

        val content: ByteArray = contentStream(page).encodeToByteArray()
        open(contentNumbers[index])
        out.text("<< /Length ${content.size} >>\nstream\n")
        out.bytes(content)
        out.text("\nendstream")
        close()

        for ((at, number) in images.withIndex()) {
            val image: PdfImage = page.images[at]
            val pixels: ByteArray = zlibStored(image.raster.rgbBytes(image.background))
            open(number)
            out.text(
                "<< /Type /XObject /Subtype /Image" +
                    " /Width ${image.raster.width} /Height ${image.raster.height}" +
                    " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode" +
                    " /Length ${pixels.size} >>\nstream\n",
            )
            out.bytes(pixels)
            out.text("\nendstream")
            close()
        }
    }

    val xref: Int = out.size
    out.text("xref\n0 ${count + 1}\n")
    out.text("0000000000 65535 f \n")
    for (number in 1..count) out.text("${offsets[number].toString().padStart(10, '0')} 00000 n \n")
    out.text("trailer\n<< /Size ${count + 1} /Root $catalog 0 R >>\nstartxref\n$xref\n%%EOF\n")
    return out.toByteArray()
}

/**
 * One page's drawing operators: each image placed by a scale-and-translate
 * matrix, then each line of text.
 *
 * PDF measures from the bottom left, so every y flips here, and an image's
 * matrix carries its size rather than its scale because the unit image is one
 * point square.
 */
private fun contentStream(page: PdfPage): String = buildString {
    for ((index, image) in page.images.withIndex()) {
        val bottom: Float = page.height - image.frame.y - image.frame.height
        append("q\n")
        append(
            "${number(image.frame.width)} 0 0 ${number(image.frame.height)} " +
                "${number(image.frame.x)} ${number(bottom)} cm\n",
        )
        append("/Im$index Do\nQ\n")
    }

    if (page.texts.isEmpty()) return@buildString
    append("0 g\n")
    for (text in page.texts) {
        append("BT /F1 ${number(text.size)} Tf ")
        append("${number(text.x)} ${number(page.height - text.y)} Td ")
        append("(${text.text.pdfEscaped()}) Tj ET\n")
    }
}

/** The cell at [position] of this layout's grid, in points from the page's top left. */
private fun cellFrame(layout: HandoutLayout, position: Int): Frame {
    val width: Float =
        (PageWidth - PageMargin * 2 - CellGutter * (layout.columns - 1)) / layout.columns
    val height: Float =
        (PageHeight - PageMargin * 2 - CellGutter * (layout.rows - 1)) / layout.rows
    val column: Int = position % layout.columns
    val row: Int = position / layout.columns

    return Frame(
        x = PageMargin + column * (width + CellGutter),
        y = PageMargin + row * (height + CellGutter),
        width = width,
        height = height,
    )
}

/** [frame] with [aspect] kept, as large as fits, centred in what it was given. */
private fun fitted(frame: Frame, aspect: Float): Frame {
    val width: Float = minOf(frame.width, frame.height * aspect)
    val height: Float = width / aspect
    return Frame(
        x = frame.x + (frame.width - width) / 2,
        y = frame.y + (frame.height - height) / 2,
        width = width,
        height = height,
    )
}

/** The number under a handout frame: the slide, and the step when steps are being written. */
private fun label(frame: ExportFrame, everyBuild: Boolean): String =
    if (!everyBuild) "${frame.slideIndex + 1}"
    else "${frame.slideIndex + 1}.${frame.step + 1}"

/** [text] broken into lines of at most [columns] characters, on its own breaks first. */
private fun wrapped(text: String, columns: Int): List<String> {
    val lines = mutableListOf<String>()
    for (paragraph in text.split("\n")) {
        var line = ""
        for (word in paragraph.split(" ").filter { it.isNotEmpty() }) {
            val joined: String = if (line.isEmpty()) word else "$line $word"
            if (joined.length <= columns) {
                line = joined
                continue
            }
            if (line.isNotEmpty()) lines += line
            // A single word longer than the line is cut rather than allowed to
            // run off the page: this is a handout, not a typesetter.
            line = if (word.length <= columns) word else word.take(columns)
        }
        lines += line
    }
    return lines
}

/**
 * A float as PDF writes one: no exponent, and no trailing zeroes to read past.
 * Rounded to hundredths of a point, which is finer than anything prints.
 */
private fun number(value: Float): String {
    val hundredths: Long = (value * 100).toLong()
    val whole: Long = hundredths / 100
    val part: Long = if (hundredths < 0) -(hundredths % 100) else hundredths % 100
    if (part == 0L) return "$whole"
    return "$whole.${part.toString().padStart(2, '0').trimEnd('0')}"
}

/**
 * The string as PDF takes one: parentheses and backslashes escaped, and anything
 * past ASCII replaced.
 *
 * Latin only, deliberately: writing anything else means embedding a font with a
 * CID map, and what goes through here is slide numbers and speaker notes.
 */
private fun String.pdfEscaped(): String = buildString {
    for (character in this@pdfEscaped) {
        when {
            character == '\\' || character == '(' || character == ')' -> {
                append('\\')
                append(character)
            }

            character.code in 32..126 -> append(character)
            else -> append('?')
        }
    }
}
