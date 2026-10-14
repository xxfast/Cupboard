package io.github.xxfast.cupboard.document

import java.io.File
import kotlin.test.Test

/**
 * Scratch: an SVG through Cupboard's own importer, as `document.json` would hold it.
 * Runs only with `ICON_SVG` (or `-Dicon.svg`) set; `ICON_OUT` is where it goes.
 */
class SvgImportScratch {
    @Test
    fun importAnSvg() {
        val path: String = setting("icon.svg", "ICON_SVG") ?: return println("icon.svg not set, skipped")
        val out = File(setting("icon.out", "ICON_OUT") ?: "build/icon")
        out.mkdirs()

        val element: Element = checkNotNull(svgElement(File(path).readText(), 240f, 240f)) { "not an svg: $path" }
        val file = File(out, "plugin-icon.json")
        file.writeText(DocumentJson.encodeToString(Element.serializer(), element))
        println("svg element: ${file.absolutePath}")
    }

    private fun setting(property: String, env: String): String? =
        System.getProperty(property) ?: System.getenv(env)
}
