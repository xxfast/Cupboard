package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.isBold
import io.github.xxfast.cupboard.document.isVisibleAt
import io.github.xxfast.cupboard.document.stepCount

/**
 * The deck as a `.pptx`, best-effort and openly so.
 *
 * PowerPoint's model is not Cupboard's, and the parts of a slide that make a
 * Cupboard deck what it is (code with its steps, terminals, diagrams, equations,
 * masked images, groups) have no counterpart there at all. So the mapping is
 * deliberately blunt: **each slide goes in as a full-slide picture**, rendered at
 * its last step, and the text boxes and simple shapes are laid over the top as
 * real PowerPoint objects.
 *
 * That means text on an exported slide appears twice, once as pixels underneath
 * and once as editable text. It is the trade that keeps the export honest: the
 * picture is what the slide *looks* like, and the objects over it are what can be
 * *edited*. Someone rewriting a headline moves the text box and repaints that
 * corner; someone who only wants the deck to open gets a deck that opens
 * identical to the original.
 *
 * Builds do not travel: a slide is one state, its last. Speaker notes do not
 * either, since a notes slide drags in a notes master and its theme for something
 * no exported slide would animate anyway.
 */
fun exportPptx(document: Document, rasterizer: Rasterizer): ByteArray {
    val indices: List<Int> = document.exportedSlideIndices()
    val width: Long = document.slideWidth.toEmu()
    val height: Long = document.slideHeight.toEmu()

    val entries = mutableListOf<ZipEntry>()
    entries += ZipEntry("[Content_Types].xml", contentTypes(indices.size).part())
    entries += ZipEntry("_rels/.rels", PackageRels.part())
    entries += ZipEntry("ppt/presentation.xml", presentation(indices.size, width, height).part())
    entries += ZipEntry("ppt/_rels/presentation.xml.rels", presentationRels(indices.size).part())
    entries += ZipEntry("ppt/slideMasters/slideMaster1.xml", SlideMaster.part())
    entries += ZipEntry("ppt/slideMasters/_rels/slideMaster1.xml.rels", SlideMasterRels.part())
    entries += ZipEntry("ppt/slideLayouts/slideLayout1.xml", SlideLayout.part())
    entries += ZipEntry("ppt/slideLayouts/_rels/slideLayout1.xml.rels", SlideLayoutRels.part())
    entries += ZipEntry("ppt/theme/theme1.xml", Theme.part())

    for ((position, index) in indices.withIndex()) {
        val slide: Slide = document.slides[index]
        val step: Int = slide.stepCount() - 1
        val number: Int = position + 1
        entries += ZipEntry(
            "ppt/media/slide$number.png",
            encodePng(rasterizer.frame(index, step)),
        )
        entries += ZipEntry("ppt/slides/slide$number.xml", slideXml(slide, step, width, height).part())
        entries += ZipEntry("ppt/slides/_rels/slide$number.xml.rels", slideRels(number).part())
    }

    return zipArchive(entries)
}

/** English Metric Units per point: OOXML's unit, and the deck's is points. */
private const val EmuPerPoint: Long = 12700

/** Hundredths of a point, which is what DrawingML sets type in. */
private const val TypeScale: Float = 100f

private fun Float.toEmu(): Long = (this * EmuPerPoint).toLong()

/** The XML declaration and the part's root, as one file's bytes. */
private fun String.part(): ByteArray =
    ("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" + "\n" + this).encodeToByteArray()

private const val ContentTypesNamespace: String =
    "http://schemas.openxmlformats.org/package/2006/content-types"
private const val DocumentNamespace: String =
    "http://schemas.openxmlformats.org/officeDocument/2006"
private const val PresentationNamespace: String =
    "http://schemas.openxmlformats.org/presentationml/2006/main"
private const val DrawingNamespace: String =
    "http://schemas.openxmlformats.org/drawingml/2006/main"
private const val RelationshipsNamespace: String =
    "http://schemas.openxmlformats.org/package/2006/relationships"

/** The `xmlns` run every presentation part opens with. */
private val PartNamespaces: String =
    """xmlns:a="$DrawingNamespace" xmlns:r="$DocumentNamespace/relationships" """ +
        """xmlns:p="$PresentationNamespace""""

private fun contentTypes(slides: Int): String = buildString {
    append("""<Types xmlns="$ContentTypesNamespace">""")
    append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
    append("""<Default Extension="xml" ContentType="application/xml"/>""")
    append("""<Default Extension="png" ContentType="image/png"/>""")
    append(
        """<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""",
    )
    append(
        """<Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>""",
    )
    append(
        """<Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>""",
    )
    append(
        """<Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>""",
    )
    for (number in 1..slides) {
        append(
            """<Override PartName="/ppt/slides/slide$number.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""",
        )
    }
    append("</Types>")
}

private val PackageRels: String =
    """<Relationships xmlns="$RelationshipsNamespace">""" +
        """<Relationship Id="rId1" Type="$DocumentNamespace/relationships/officeDocument" Target="ppt/presentation.xml"/>""" +
        "</Relationships>"

private fun presentation(slides: Int, width: Long, height: Long): String = buildString {
    append("""<p:presentation $PartNamespaces>""")
    // rId1 is the master, so the slides start at rId2 and the ids run with them.
    append("""<p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>""")
    append("<p:sldIdLst>")
    for (number in 1..slides) {
        append("""<p:sldId id="${255 + number}" r:id="rId${number + 1}"/>""")
    }
    append("</p:sldIdLst>")
    append("""<p:sldSz cx="$width" cy="$height"/>""")
    append("""<p:notesSz cx="$height" cy="$width"/>""")
    append("</p:presentation>")
}

private fun presentationRels(slides: Int): String = buildString {
    append("""<Relationships xmlns="$RelationshipsNamespace">""")
    append(
        """<Relationship Id="rId1" Type="$DocumentNamespace/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>""",
    )
    for (number in 1..slides) {
        append(
            """<Relationship Id="rId${number + 1}" Type="$DocumentNamespace/relationships/slide" Target="slides/slide$number.xml"/>""",
        )
    }
    append(
        """<Relationship Id="rId${slides + 2}" Type="$DocumentNamespace/relationships/theme" Target="theme/theme1.xml"/>""",
    )
    append("</Relationships>")
}

/** An empty shape tree, which is all a master needs to be a valid one. */
private val EmptyTree: String =
    "<p:cSld><p:spTree>" +
        """<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>""" +
        "<p:grpSpPr/>" +
        "</p:spTree></p:cSld>"

/** The colour map every slide inherits, and which a master must spell out. */
private val ColorMap: String =
    """<p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" hlink="hlink" folHlink="folHlink"""" +
        """ accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4"""" +
        """ accent5="accent5" accent6="accent6"/>"""

private val SlideMaster: String =
    """<p:sldMaster $PartNamespaces>$EmptyTree$ColorMap""" +
        """<p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst>""" +
        "</p:sldMaster>"

private val SlideMasterRels: String =
    """<Relationships xmlns="$RelationshipsNamespace">""" +
        """<Relationship Id="rId1" Type="$DocumentNamespace/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>""" +
        """<Relationship Id="rId2" Type="$DocumentNamespace/relationships/theme" Target="../theme/theme1.xml"/>""" +
        "</Relationships>"

private val SlideLayout: String =
    """<p:sldLayout $PartNamespaces type="blank" preserve="1">$EmptyTree""" +
        "<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"

private val SlideLayoutRels: String =
    """<Relationships xmlns="$RelationshipsNamespace">""" +
        """<Relationship Id="rId1" Type="$DocumentNamespace/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>""" +
        "</Relationships>"

private fun slideRels(number: Int): String =
    """<Relationships xmlns="$RelationshipsNamespace">""" +
        """<Relationship Id="rId1" Type="$DocumentNamespace/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>""" +
        """<Relationship Id="rId2" Type="$DocumentNamespace/relationships/image" Target="../media/slide$number.png"/>""" +
        "</Relationships>"

/**
 * A theme with one colour scheme, one font scheme and one format scheme.
 *
 * The smallest thing PowerPoint accepts: a master without a theme part will not
 * open, and every colour a slide actually uses is written on the object itself,
 * so nothing here is ever looked up.
 */
private val Theme: String = buildString {
    append("""<a:theme xmlns:a="$DrawingNamespace" name="Cupboard">""")
    append("<a:themeElements><a:clrScheme name=\"Cupboard\">")
    append("""<a:dk1><a:srgbClr val="000000"/></a:dk1><a:lt1><a:srgbClr val="FFFFFF"/></a:lt1>""")
    append("""<a:dk2><a:srgbClr val="1B1B22"/></a:dk2><a:lt2><a:srgbClr val="EEEEEE"/></a:lt2>""")
    for (accent in 1..6) append("""<a:accent$accent><a:srgbClr val="7C6BFF"/></a:accent$accent>""")
    append("""<a:hlink><a:srgbClr val="7C6BFF"/></a:hlink>""")
    append("""<a:folHlink><a:srgbClr val="9B95AD"/></a:folHlink></a:clrScheme>""")
    append("""<a:fontScheme name="Cupboard">""")
    append("""<a:majorFont><a:latin typeface="Helvetica"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont>""")
    append("""<a:minorFont><a:latin typeface="Helvetica"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont>""")
    append("</a:fontScheme>")
    append("""<a:fmtScheme name="Cupboard">""")
    append("<a:fillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>")
    append("<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>")
    append("<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:fillStyleLst>")
    append("<a:lnStyleLst>")
    repeat(3) { append("""<a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>""") }
    append("</a:lnStyleLst>")
    append("<a:effectStyleLst>")
    repeat(3) { append("<a:effectStyle><a:effectLst/></a:effectStyle>") }
    append("</a:effectStyleLst>")
    append("<a:bgFillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>")
    append("<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>")
    append("<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:bgFillStyleLst>")
    append("</a:fmtScheme></a:themeElements></a:theme>")
}

/** One slide: the picture of it, then whatever of it PowerPoint can hold as objects. */
private fun slideXml(slide: Slide, step: Int, width: Long, height: Long): String = buildString {
    append("""<p:sld $PartNamespaces><p:cSld><p:spTree>""")
    append("""<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>""")
    append("<p:grpSpPr><a:xfrm>")
    append("""<a:off x="0" y="0"/><a:ext cx="$width" cy="$height"/>""")
    append("""<a:chOff x="0" y="0"/><a:chExt cx="$width" cy="$height"/>""")
    append("</a:xfrm></p:grpSpPr>")

    append("""<p:pic><p:nvPicPr><p:cNvPr id="2" name="Slide"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>""")
    append("""<p:blipFill><a:blip r:embed="rId2"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>""")
    append("<p:spPr><a:xfrm>")
    append("""<a:off x="0" y="0"/><a:ext cx="$width" cy="$height"/>""")
    append("""</a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>""")

    var id = 3
    for (element in slide.elements) {
        if (!slide.isVisibleAt(element.id, step)) continue
        val shape: String? = when (element) {
            is TextElement -> textBox(element, id)
            is ShapeElement -> shapeBox(element, id)
            // Everything Cupboard draws itself is already in the picture behind:
            // see this file's own note on what the export is and is not.
            else -> null
        }
        if (shape == null) continue
        append(shape)
        id++
    }

    append("</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>")
}

/** An element's frame as an `a:xfrm`, in EMU. */
private fun Element.xfrm(): String =
    "<a:xfrm><a:off x=\"${frame.x.toEmu()}\" y=\"${frame.y.toEmu()}\"/>" +
        "<a:ext cx=\"${frame.width.toEmu()}\" cy=\"${frame.height.toEmu()}\"/></a:xfrm>"

private fun textBox(element: TextElement, id: Int): String = buildString {
    append("""<p:sp><p:nvSpPr><p:cNvPr id="$id" name="Text $id"/>""")
    append("""<p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr>""")
    append("<p:spPr>${element.xfrm()}")
    append("""<a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/></p:spPr>""")
    append("""<p:txBody><a:bodyPr wrap="square"><a:spAutoFit/></a:bodyPr><a:lstStyle/>""")

    val align: String = when (element.align) {
        TextAlign.Start -> "l"
        TextAlign.Center -> "ctr"
        TextAlign.End -> "r"
    }
    val run: String = """<a:rPr lang="en-US" sz="${(element.fontSize * TypeScale).toInt()}"""" +
        """ b="${if (element.isBold) 1 else 0}" i="${if (element.italic) 1 else 0}"""" +
        """ u="${if (element.underline) "sng" else "none"}"""" +
        """ strike="${if (element.strikethrough) "sngStrike" else "noStrike"}">""" +
        fill(element.color) + "</a:rPr>"

    for (line in element.text.split("\n")) {
        append("""<a:p><a:pPr algn="$align"/>""")
        append("<a:r>$run<a:t>${line.xmlEscaped()}</a:t></a:r></a:p>")
    }
    append("</p:txBody></p:sp>")
}

/**
 * A shape as a preset geometry, for the three kinds that map cleanly.
 *
 * Everything else (lines with arrowheads, callouts, stars with a point count of
 * their own) is left to the picture underneath rather than approximated into a
 * shape that is nearly right.
 */
private fun shapeBox(element: ShapeElement, id: Int): String? {
    val preset: String = when (element.kind) {
        ShapeKind.Rectangle -> if (element.cornerRadius > 0f) "roundRect" else "rect"
        ShapeKind.Ellipse -> "ellipse"
        else -> return null
    }

    return buildString {
        append("""<p:sp><p:nvSpPr><p:cNvPr id="$id" name="Shape $id"/>""")
        append("<p:cNvSpPr/><p:nvPr/></p:nvSpPr>")
        append("<p:spPr>${element.xfrm()}")
        append("""<a:prstGeom prst="$preset"><a:avLst/></a:prstGeom>""")
        append(fill(element.fill))
        if (element.strokeWidth > 0f) {
            append("""<a:ln w="${element.strokeWidth.toEmu()}">""")
            append("${fill(element.strokeColor)}</a:ln>")
        }
        append("</p:spPr>")
        append("""<p:txBody><a:bodyPr anchor="ctr"/><a:lstStyle/>""")
        append("""<a:p><a:pPr algn="ctr"/><a:r>""")
        append("""<a:rPr lang="en-US" sz="${(element.labelSize * TypeScale).toInt()}">""")
        append(fill(element.labelColor))
        append("</a:rPr><a:t>${element.label.xmlEscaped()}</a:t></a:r></a:p></p:txBody></p:sp>")
    }
}

/** A packed ARGB colour as DrawingML writes one, alpha only when there is any to write. */
private fun color(argb: Long): String {
    val hex: String = (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
    val alpha: Int = ((argb shr 24) and 0xFF).toInt()
    if (alpha >= 0xFF) return """<a:srgbClr val="$hex"/>"""
    val percent: Int = alpha * 100000 / 255
    return """<a:srgbClr val="$hex"><a:alpha val="$percent"/></a:srgbClr>"""
}

/** [color] as the solid fill that a run, a shape or a line takes. */
private fun fill(argb: Long): String = "<a:solidFill>${color(argb)}</a:solidFill>"

/** The five characters XML will not take in text or in an attribute. */
private fun String.xmlEscaped(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")
