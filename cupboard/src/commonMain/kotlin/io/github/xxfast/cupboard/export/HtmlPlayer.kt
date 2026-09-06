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
 */
fun exportHtmlPlayer(document: Document, rasterizer: Rasterizer): String {
    val frames: List<ExportFrame> = document.exportFrames(everyBuild = true)
    val images: String = frames.joinToString("\n") { frame ->
        val png: ByteArray = encodePng(rasterizer.frame(frame.slideIndex, frame.step))
        """    <img class="frame" alt="" src="data:image/png;base64,${base64(png)}">"""
    }

    val notes: String = frames.joinToString("\n") { frame ->
        val slide: Slide = document.slides[frame.slideIndex]
        val text: String = slide.notes.ifBlank { "No notes." }
        """    <p class="note">${text.htmlEscaped()}</p>"""
    }

    return """
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
$images
</div>
<aside id="notes" hidden>
$notes
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
""".trimStart()
}

/** Text as it can sit in an element: the three characters that would end it, escaped. */
private fun String.htmlEscaped(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** The standard base64 alphabet, since a data URI is the point of this file. */
private const val Base64Alphabet: String =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/**
 * [data] as base64.
 *
 * Written out rather than taken from `kotlin.io.encoding`, whose opt-in has moved
 * more than once and which every target would have to agree on.
 */
internal fun base64(data: ByteArray): String {
    val out = StringBuilder((data.size + 2) / 3 * 4)
    var at = 0
    while (at + 2 < data.size) {
        val triple: Int = ((data[at].toInt() and 0xFF) shl 16) or
            ((data[at + 1].toInt() and 0xFF) shl 8) or
            (data[at + 2].toInt() and 0xFF)
        out.append(Base64Alphabet[triple ushr 18])
        out.append(Base64Alphabet[(triple ushr 12) and 0x3F])
        out.append(Base64Alphabet[(triple ushr 6) and 0x3F])
        out.append(Base64Alphabet[triple and 0x3F])
        at += 3
    }

    val left: Int = data.size - at
    if (left == 1) {
        val byte: Int = data[at].toInt() and 0xFF
        out.append(Base64Alphabet[byte ushr 2])
        out.append(Base64Alphabet[(byte shl 4) and 0x3F])
        out.append("==")
    } else if (left == 2) {
        val pair: Int = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)
        out.append(Base64Alphabet[pair ushr 10])
        out.append(Base64Alphabet[(pair ushr 4) and 0x3F])
        out.append(Base64Alphabet[(pair shl 2) and 0x3F])
        out.append('=')
    }
    return out.toString()
}
