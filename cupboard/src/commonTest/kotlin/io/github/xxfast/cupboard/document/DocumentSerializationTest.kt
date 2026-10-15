package io.github.xxfast.cupboard.document

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a `.cupboard` bundle's `document.json` promises across versions: this
 * build reads every deck it has ever written, and refuses, out loud, one it
 * cannot.
 */
class DocumentSerializationTest {
    @Test
    fun writesTheFormatVersionAndReadsItBack() {
        val json: String = sampleDocument().encodeToString()

        assertTrue(json.contains("\"formatVersion\": $CURRENT_FORMAT_VERSION"), json.take(120))
        val loaded = assertIs<DocumentLoad.Loaded>(decodeDocument(json))
        assertEquals(CURRENT_FORMAT_VERSION, loaded.document.formatVersion)
    }

    @Test
    fun readsAVersionlessDeckAndStampsItCurrent() {
        // Every deck written before the field existed. `ignoreUnknownKeys` is
        // what carries new fields forwards; this is the other direction. Read,
        // it has been migrated, so it saves as the version it now is.
        val json = """{ "id": "doc", "name": "Old", "slides": [] }"""

        val loaded = assertIs<DocumentLoad.Loaded>(decodeDocument(json))
        assertEquals(CURRENT_FORMAT_VERSION, loaded.document.formatVersion)
        assertEquals("Old", loaded.document.name)
    }

    @Test
    fun readsAnElementWrittenBeforeNamesAndHidingAsUnnamedAndShowing() {
        val json = """
            {
              "id": "doc",
              "name": "Old",
              "slides": [
                {
                  "id": "slide",
                  "elements": [
                    {
                      "type": "text",
                      "id": "text",
                      "frame": { "x": 0.0, "y": 0.0, "width": 10.0, "height": 10.0 },
                      "text": "Hello"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val loaded = assertIs<DocumentLoad.Loaded>(decodeDocument(json))
        val element: Element = loaded.document.slides.single().elements.single()
        assertNull(element.name)
        assertFalse(element.hidden)
        assertEquals("Hello", element.displayName)
    }

    @Test
    fun refusesADeckFromANewerCupboard() {
        // Named rather than half-decoded: the fields this build recognises may
        // well parse, and be wrong.
        val json = """{ "formatVersion": 99, "id": "doc", "name": "Future", "slides": [] }"""

        assertEquals(DocumentLoad.TooNew(99), decodeDocument(json))
    }

    @Test
    fun reportsGarbageAsCorruptRatherThanThrowing() {
        assertIs<DocumentLoad.Corrupt>(decodeDocument("not a deck at all"))
        assertIs<DocumentLoad.Corrupt>(decodeDocument("[]"))
        // A version it can read, holding something it cannot.
        assertIs<DocumentLoad.Corrupt>(decodeDocument("""{ "formatVersion": 1, "slides": 7 }"""))
    }

    /**
     * A version 2 shadow was a step and a translucent colour. Read now, it is a
     * drop shadow of the same reach along the same direction, its alpha moved
     * into the opacity: on a shape, on a line inside a group on a layout, and
     * on a saved style, wherever the walk finds one.
     */
    @Test
    fun readsAVersionTwoShadowAsAnOffsetAlongAnAngle() {
        val json = """
            {
              "formatVersion": 2,
              "id": "doc",
              "name": "Old",
              "slides": [
                {
                  "id": "slide",
                  "elements": [
                    {
                      "type": "shape",
                      "id": "down",
                      "frame": { "x": 0.0, "y": 0.0, "width": 10.0, "height": 10.0 },
                      "shadow": { "color": 2147483648, "blur": 12.0, "dx": 0.0, "dy": 6.0 }
                    },
                    {
                      "type": "shape",
                      "id": "defaults",
                      "frame": { "x": 0.0, "y": 0.0, "width": 10.0, "height": 10.0 },
                      "shadow": { }
                    }
                  ]
                }
              ],
              "layouts": [
                {
                  "id": "layout",
                  "elements": [
                    {
                      "type": "group",
                      "id": "group",
                      "frame": { "x": 0.0, "y": 0.0, "width": 10.0, "height": 10.0 },
                      "children": [
                        {
                          "type": "line",
                          "id": "line",
                          "start": { "x": 0.0, "y": 0.0 },
                          "end": { "x": 10.0, "y": 10.0 },
                          "strokeColor": 4294967295,
                          "shadow": { "color": 1711276287, "blur": 4.0, "dx": 3.0, "dy": -3.0 }
                        }
                      ]
                    }
                  ]
                }
              ],
              "objectStyles": [
                {
                  "id": "style",
                  "name": "Lifted",
                  "fill": 4294967295,
                  "strokeColor": 4278190080,
                  "strokeWidth": 1.0,
                  "cornerRadius": 0.0,
                  "shadow": { "color": 4278190335, "blur": 8.0, "dx": -5.0, "dy": 0.0 }
                }
              ]
            }
        """.trimIndent()

        val loaded = assertIs<DocumentLoad.Loaded>(decodeDocument(json))
        val document: Document = loaded.document
        assertEquals(CURRENT_FORMAT_VERSION, document.formatVersion)

        val (down: Element, defaults: Element) = document.slides.single().elements
        val straightDown: ShapeShadow = assertNotNull((down as ShapeElement).shadow)
        assertEquals(ShadowKind.Drop, straightDown.kind)
        assertEquals(0xFF000000, straightDown.color)
        assertEquals(12f, straightDown.blur)
        assertEquals(6f, straightDown.offset, 0.001f)
        assertEquals(270f, straightDown.angle, 0.001f)
        assertEquals(128 / 255f, straightDown.opacity, 0.001f)

        // A field the file left out is the old default it stood for.
        assertEquals(straightDown, (defaults as ShapeElement).shadow)

        val group = document.layouts.single().elements.single() as GroupElement
        val upAndRight: ShapeShadow = assertNotNull((group.children.single() as LineElement).shadow)
        assertEquals(0xFF0000FF, upAndRight.color)
        assertEquals(hypot(3f, 3f), upAndRight.offset, 0.001f)
        assertEquals(45f, upAndRight.angle, 0.001f)
        assertEquals(0x66 / 255f, upAndRight.opacity, 0.001f)

        val left: ShapeShadow = assertNotNull(document.objectStyles.single().shadow)
        assertEquals(0xFF0000FF, left.color)
        assertEquals(5f, left.offset, 0.001f)
        assertEquals(180f, left.angle, 0.001f)
        assertEquals(1f, left.opacity, 0.001f)
    }

    /** A version 3 shadow is read as written: the migration is for older files alone. */
    @Test
    fun roundTripsAVersionThreeShadow() {
        val shadows: List<ShapeShadow> = listOf(
            ShapeShadow(color = 0xFF7F52FF, blur = 9f, offset = 14f, angle = 315f, opacity = 0.3f),
            ShadowKind.Contact.shadow().copy(perspective = 24f),
            ShadowKind.Curved.shadow().copy(curve = -0.6f),
        )
        val document = Document(
            slides = listOf(
                Slide(
                    elements = shadows.map { shadow ->
                        ShapeElement(frame = Frame(0f, 0f, 10f, 10f), shadow = shadow)
                    },
                ),
            ),
        )

        val json: String = document.encodeToString()
        assertTrue(json.contains("\"formatVersion\": 3"), json.take(120))
        val loaded = assertIs<DocumentLoad.Loaded>(decodeDocument(json))
        assertEquals(document, loaded.document)
        assertEquals(
            shadows,
            loaded.document.slides.single().elements.map { (it as ShapeElement).shadow },
        )
    }
}
