package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Document

/**
 * One file of a binary export: where it goes, and the bytes that go there.
 *
 * [ExportedFile]'s counterpart for everything that is not source: a PNG is not a
 * string, and giving that one a `ByteArray` would make every text export carry an
 * encode. Writing these out is the shell's job, exactly as it is there.
 */
data class ExportedBytes(val path: String, val bytes: ByteArray)

/**
 * Every slide as a PNG, numbered in presentation order.
 *
 * `slide-01.png` per slide, and `slide-01-step-2.png` for the steps past the
 * first when [everyBuild] walks the build order. Skipped slides are left out, and
 * a slide's single frame is its *last* step: a still of a slide half-built is a
 * still with the point missing.
 *
 * The bytes come from [encodePng] rather than from the rasteriser's own encoder,
 * so this returns the same file on every target rather than only where Skia is.
 * There is no JPEG option, and see [encodePng] for why.
 */
fun exportPngs(
    document: Document,
    rasterizer: Rasterizer,
    everyBuild: Boolean = false,
): List<ExportedBytes> {
    val numbers: Map<Int, Int> = document.exportedSlideIndices()
        .withIndex()
        .associate { (position, index) -> index to position + 1 }

    return document.exportFrames(everyBuild).map { frame ->
        val number: String = (numbers[frame.slideIndex] ?: frame.slideIndex + 1)
            .toString()
            .padStart(2, '0')
        // The step suffix only means anything while the steps are being walked:
        // a per-slide export draws the last step, and that file is the slide.
        val name: String =
            if (!everyBuild || frame.step == 0) "slide-$number.png"
            else "slide-$number-step-${frame.step + 1}.png"
        ExportedBytes(name, encodePng(rasterizer.frame(frame.slideIndex, frame.step)))
    }
}
