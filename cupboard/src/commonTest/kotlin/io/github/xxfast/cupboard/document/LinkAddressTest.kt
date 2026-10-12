package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a typed address becomes before it is stored, for every place that takes one. */
class LinkAddressTest {
    @Test
    fun anAddressWithNoSchemeGetsHttps() {
        assertEquals("https://kotlinlang.org/docs", webAddress("kotlinlang.org/docs"))
        assertEquals("https://localhost:8080", webAddress("localhost:8080"))
    }

    @Test
    fun anAddressThatNamesASchemeIsLeftAlone() {
        assertEquals("http://example.com", webAddress("http://example.com"))
        assertEquals("https://example.com", webAddress("https://example.com"))
        assertEquals("mailto:hi@example.com", webAddress("mailto:hi@example.com"))
        assertEquals("MAILTO:hi@example.com", webAddress("MAILTO:hi@example.com"))
        assertEquals("tel:+15551234", webAddress("tel:+15551234"))
    }

    @Test
    fun anAddressIsTrimmedAndABlankOneIsNull() {
        assertEquals("https://example.com", webAddress("  example.com \n"))
        assertEquals("http://example.com", webAddress("  http://example.com  "))
        assertNull(webAddress(""))
        assertNull(webAddress("   "))
    }

    @Test
    fun onlyAUrlTargetIsNormalized() {
        assertEquals(LinkTarget.Url("https://example.com"), LinkTarget.Url("example.com").normalized())
        assertEquals(LinkTarget.Url("  "), LinkTarget.Url("  ").normalized())
        assertEquals(LinkTarget.Slide("s"), LinkTarget.Slide("s").normalized())
        assertEquals(LinkTarget.Next, LinkTarget.Next.normalized())
        assertEquals(LinkTarget.ExitShow, LinkTarget.ExitShow.normalized())
    }
}
