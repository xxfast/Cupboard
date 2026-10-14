package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Document
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString

/**
 * The HTML player written straight to the file at [path], over whatever was
 * there.
 *
 * What a shell calls rather than taking the page back as a string: the page is
 * the size of every frame in the deck, and streaming it to disk is what keeps
 * that off the heap. A failure part way (a full disk, a frame that would not
 * draw) throws, and takes the half-written file with it.
 */
fun exportHtmlPlayer(document: Document, rasterizer: Rasterizer, path: Path) {
    try {
        SystemFileSystem.sink(path).buffered().use { sink ->
            exportHtmlPlayer(document, rasterizer, Utf8Appendable(sink))
        }
    } catch (failure: Throwable) {
        runCatching { SystemFileSystem.delete(path, mustExist = false) }
        throw failure
    }
}

/**
 * [sink] as something text can be appended to, encoded as UTF-8 on the way in.
 *
 * A lone [Char] is written as a string of one. That splits a surrogate pair
 * appended a char at a time, which the player never does: its chars are line
 * breaks, and everything else arrives as whole strings.
 */
private class Utf8Appendable(private val sink: Sink) : Appendable {
    override fun append(value: Char): Appendable = append(value.toString())

    override fun append(value: CharSequence?): Appendable {
        sink.writeString(value.toString())
        return this
    }

    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable {
        sink.writeString(value.toString(), startIndex, endIndex)
        return this
    }
}
