package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide

/**
 * The deck as one HTML file that plays it: every step a PNG inlined as a data
 * URI, and eighty lines of script to walk them.
 *
 * One file and no server, so it opens off a USB stick, an email attachment or a
 * static host, which is the point: it is what gets handed to someone who wants
 * to click through the talk without Cupboard. The frames are pictures, so the
 * builds land as whole steps rather than as animations, and the notes travel
 * along under N for the presenter's own copy.
 *
 * A step is an `<img>` of its own rather than one image whose source changes:
 * every frame is then decoded by the time it is shown, and clicking forward has
 * nothing to wait for.
 *
 * Links come along as they look and no further: a linked box or a linked run of
 * words is underlined in its frame, but a frame is a picture, and a click on it
 * is the next step like a click anywhere else.
 */
fun exportHtmlPlayer(document: Document, rasterizer: Rasterizer): String =
    buildString { exportHtmlPlayer(document, rasterizer, this) }

/**
 * The player written into [out] as it is made, rather than built up and handed
 * back.
 *
 * A frame is a PNG of the whole slide and a deck walked build by build is
 * hundreds of them, so the page is easily a few hundred megabytes of base64. As
 * one string that is more than a Kotlin/Native heap will hand out in one piece,
 * and twice over while it is being copied into a bigger one. Here a frame is
 * drawn, encoded and appended a chunk at a time, and gone before the next one is
 * drawn: what is held at once is one frame, whatever the length of the deck.
 */
fun exportHtmlPlayer(document: Document, rasterizer: Rasterizer, out: Appendable) {
    val frames: List<ExportFrame> = document.exportFrames(everyBuild = true)

    out.append(
        """
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${document.name.htmlEscaped()}</title>
<style>
  :root { color-scheme: dark; }
  body {
    margin: 0; background: #0b0b0f; color: #d8d4e4; overflow: hidden;
    font: 13px -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif;
  }
  #stage { display: flex; align-items: center; justify-content: center; height: 100vh; }
  .frame { display: none; max-width: 100vw; max-height: 100vh; }
  .frame.on { display: block; }
  #counter {
    position: fixed; right: 12px; bottom: 10px; padding: 3px 8px; border-radius: 6px;
    background: rgba(0, 0, 0, .55); color: #9b95ad; font-variant-numeric: tabular-nums;
  }
  #notes {
    position: fixed; inset: auto 0 0 0; max-height: 40vh; overflow: auto; padding: 16px 20px;
    background: #14141c; border-top: 1px solid #26262f; line-height: 1.5; white-space: pre-wrap;
  }
  #notes[hidden] { display: none; }
  .note { display: none; margin: 0; }
  .note.on { display: block; }
</style>
<div id="stage">
""".trimStart()
    )

    frames.forEachIndexed { index, frame ->
        if (index > 0) out.append('\n')
        val png: ByteArray = encodePng(rasterizer.frame(frame.slideIndex, frame.step))
        out.append("""    <img class="frame" alt="" src="data:image/png;base64,""")
        out.appendBase64(png)
        out.append("\">")
    }

    out.append("\n</div>\n<aside id=\"notes\" hidden>\n")

    frames.forEachIndexed { index, frame ->
        if (index > 0) out.append('\n')
        val slide: Slide = document.slides[frame.slideIndex]
        val text: String = slide.notes.ifBlank { "No notes." }
        out.append("""    <p class="note">${text.htmlEscaped()}</p>""")
    }

    out.append(PlayerTail)
}

/** Everything after the notes: the counter, and the script that walks the frames. */
private const val PlayerTail: String = """
</aside>
<div id="counter"></div>
<script>
  const frames = Array.from(document.querySelectorAll('.frame'));
  const notes = Array.from(document.querySelectorAll('.note'));
  const counter = document.getElementById('counter');
  const aside = document.getElementById('notes');
  let at = 0;
  let typed = '';

  function show(index) {
    at = Math.max(0, Math.min(frames.length - 1, index));
    frames.forEach((frame, position) => frame.classList.toggle('on', position === at));
    notes.forEach((note, position) => note.classList.toggle('on', position === at));
    counter.textContent = (at + 1) + ' / ' + frames.length;
  }

  // A number then Enter jumps, the way a presenter remote's keypad does.
  document.addEventListener('keydown', event => {
    if (event.key >= '0' && event.key <= '9') { typed += event.key; return; }
    if (event.key === 'Enter') { if (typed) show(parseInt(typed, 10) - 1); typed = ''; return; }
    typed = '';
    if (['ArrowRight', 'ArrowDown', ' ', 'PageDown'].includes(event.key)) { show(at + 1); event.preventDefault(); }
    else if (['ArrowLeft', 'ArrowUp', 'PageUp'].includes(event.key)) { show(at - 1); event.preventDefault(); }
    else if (event.key === 'Home') show(0);
    else if (event.key === 'End') show(frames.length - 1);
    else if (event.key === 'n' || event.key === 'N') aside.hidden = !aside.hidden;
  });

  document.getElementById('stage').addEventListener('click', () => show(at + 1));
  show(0);
</script>
"""

/** Text as it can sit in an element: the three characters that would end it, escaped. */
private fun String.htmlEscaped(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** The standard base64 alphabet, since a data URI is the point of this file. */
private const val Base64Alphabet: String =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/** How many characters of base64 are handed to the sink at a time. A multiple of four. */
private const val Base64Chunk: Int = 4096

/**
 * [data] appended as base64, [Base64Chunk] characters at a time.
 *
 * Into the sink rather than returned: a frame's base64 is a third bigger than
 * its PNG, and as a string of its own it would be one more copy of the biggest
 * thing in the file. One reused buffer is the most this holds.
 *
 * Written out rather than taken from `kotlin.io.encoding`, whose opt-in has moved
 * more than once and which every target would have to agree on.
 */
internal fun Appendable.appendBase64(data: ByteArray) {
    val chunk = StringBuilder(Base64Chunk)
    var at = 0
    while (at + 2 < data.size) {
        val triple: Int = ((data[at].toInt() and 0xFF) shl 16) or
            ((data[at + 1].toInt() and 0xFF) shl 8) or
            (data[at + 2].toInt() and 0xFF)
        chunk.append(Base64Alphabet[triple ushr 18])
        chunk.append(Base64Alphabet[(triple ushr 12) and 0x3F])
        chunk.append(Base64Alphabet[(triple ushr 6) and 0x3F])
        chunk.append(Base64Alphabet[triple and 0x3F])
        at += 3

        if (chunk.length >= Base64Chunk) {
            append(chunk)
            chunk.clear()
        }
    }

    val left: Int = data.size - at
    if (left == 1) {
        val byte: Int = data[at].toInt() and 0xFF
        chunk.append(Base64Alphabet[byte ushr 2])
        chunk.append(Base64Alphabet[(byte shl 4) and 0x3F])
        chunk.append("==")
    } else if (left == 2) {
        val pair: Int = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)
        chunk.append(Base64Alphabet[pair ushr 10])
        chunk.append(Base64Alphabet[(pair ushr 4) and 0x3F])
        chunk.append(Base64Alphabet[(pair shl 2) and 0x3F])
        chunk.append('=')
    }
    append(chunk)
}
