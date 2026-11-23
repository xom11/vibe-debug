package io.github.xom11.vibedebug

import io.github.xom11.vibedebug.model.OemBrand
import io.github.xom11.vibedebug.model.OemBrandDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OemBrandDetectorTest {

    @Test
    fun `detect maps Samsung devices cleanly`() {
        assertEquals(OemBrand.SAMSUNG, OemBrandDetector.detect("samsung", "samsung"))
        assertEquals(OemBrand.SAMSUNG, OemBrandDetector.detect("Samsung Electronics", "galaxy"))
    }

    @Test
    fun `detect maps OnePlus OPPO and Realme into ColorOS OxygenOS family`() {
        assertEquals(OemBrand.ONEPLUS_OPPO_REALME, OemBrandDetector.detect("OnePlus", "OnePlus"))
        assertEquals(OemBrand.ONEPLUS_OPPO_REALME, OemBrandDetector.detect("OPPO", "OPPO"))
        assertEquals(OemBrand.ONEPLUS_OPPO_REALME, OemBrandDetector.detect("realme", "RMX"))
    }

    @Test
    fun `detect maps Xiaomi Redmi and POCO into HyperOS MIUI family`() {
        assertEquals(OemBrand.XIAOMI, OemBrandDetector.detect("Xiaomi", "Xiaomi"))
        assertEquals(OemBrand.XIAOMI, OemBrandDetector.detect("Xiaomi", "Redmi"))
        assertEquals(OemBrand.XIAOMI, OemBrandDetector.detect("POCO", "POCO"))
    }

    @Test
    fun `detect maps Vivo and iQOO devices`() {
        assertEquals(OemBrand.VIVO, OemBrandDetector.detect("vivo", "vivo"))
        assertEquals(OemBrand.VIVO, OemBrandDetector.detect("vivo", "iQOO"))
    }

    @Test
    fun `detect falls back to Google Stock Other for Pixel or unknown brands`() {
        assertEquals(OemBrand.GOOGLE_OTHER, OemBrandDetector.detect("Google", "google"))
        assertEquals(OemBrand.GOOGLE_OTHER, OemBrandDetector.detect("Sony", "Xperia"))
        assertEquals(OemBrand.GOOGLE_OTHER, OemBrandDetector.detect(null, null))
        assertEquals(OemBrand.GOOGLE_OTHER, OemBrandDetector.detect("  ", ""))
    }

    @Test
    fun `fromPreviewKey resolves supported debug preview brand overrides`() {
        assertEquals(OemBrand.SAMSUNG, OemBrandDetector.fromPreviewKey("samsung"))
        assertEquals(OemBrand.ONEPLUS_OPPO_REALME, OemBrandDetector.fromPreviewKey("oneplus"))
        assertEquals(OemBrand.XIAOMI, OemBrandDetector.fromPreviewKey("xiaomi"))
        assertEquals(OemBrand.VIVO, OemBrandDetector.fromPreviewKey("vivo"))
        assertEquals(OemBrand.GOOGLE_OTHER, OemBrandDetector.fromPreviewKey("google"))
        assertNull(OemBrandDetector.fromPreviewKey("unknown_brand"))
        assertNull(OemBrandDetector.fromPreviewKey(null))
    }
}
