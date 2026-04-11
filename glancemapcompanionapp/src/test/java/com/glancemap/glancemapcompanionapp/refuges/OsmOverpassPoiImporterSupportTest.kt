package com.glancemap.glancemapcompanionapp.refuges

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OsmOverpassPoiImporterSupportTest {

    @Test
    fun `keeps small bbox in a single overpass request`() {
        val bbox = BBox(
            minLon = 6.0,
            minLat = 45.0,
            maxLon = 6.8,
            maxLat = 45.7
        )

        val tiles = splitBboxForOverpass(bbox)

        assertEquals(1, tiles.size)
        assertEquals(bbox, tiles.single())
    }

    @Test
    fun `splits large bbox into smaller overpass tiles`() {
        val bbox = BBox(
            minLon = 8.9,
            minLat = 47.1,
            maxLon = 13.9,
            maxLat = 50.5
        )

        val tiles = splitBboxForOverpass(bbox)

        assertEquals(9, tiles.size)
        assertEquals(bbox.minLon, tiles.first().minLon, 0.00001)
        assertEquals(bbox.minLat, tiles.first().minLat, 0.00001)
        assertEquals(bbox.maxLon, tiles.last().maxLon, 0.00001)
        assertEquals(bbox.maxLat, tiles.last().maxLat, 0.00001)
    }

    @Test
    fun `summarizes html overpass errors without dumping markup`() {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head><title>504 Gateway Time-out</title></head>
            <body>proxy timeout</body>
            </html>
        """.trimIndent()

        val message = summarizeOsmOverpassFailure(504, html)

        assertTrue(message.contains("504 Gateway Time-out"))
        assertTrue(!message.contains("<html"))
    }

    @Test
    fun `parses overpass status with available slots`() {
        val body = """
            Connected as: 12345
            Current time: 2026-04-09T09:31:00Z
            Rate limit: 2
            2 slots available now.
        """.trimIndent()

        val summary = parseOverpassStatusSummary(body)

        assertEquals(2, summary.rateLimit)
        assertEquals(2, summary.slotsAvailable)
        assertEquals(null, summary.nextSlotSeconds)
        assertEquals("2 slots available now", summary.message)
    }

    @Test
    fun `parses overpass status with future slot wait`() {
        val body = """
            Connected as: 12345
            Current time: 2026-04-09T09:31:00Z
            Rate limit: 2
            Slot available after: 2026-04-09T09:31:12Z, in 12 seconds.
        """.trimIndent()

        val summary = parseOverpassStatusSummary(body)

        assertEquals(2, summary.rateLimit)
        assertEquals(null, summary.slotsAvailable)
        assertEquals(12, summary.nextSlotSeconds)
        assertEquals("next slot in 12s", summary.message)
    }

    @Test
    fun `splits overflow tile into smaller requests`() {
        val bbox = BBox(
            minLon = 8.765,
            minLat = 47.157,
            maxLon = 11.4215,
            maxLat = 48.916
        )

        val tiles = splitBboxForOverpassOverflow(bbox)

        assertEquals(4, tiles.size)
        assertEquals(bbox.minLon, tiles.first().minLon, 0.00001)
        assertEquals(bbox.minLat, tiles.first().minLat, 0.00001)
        assertEquals(bbox.maxLon, tiles.last().maxLon, 0.00001)
        assertEquals(bbox.maxLat, tiles.last().maxLat, 0.00001)
    }

    @Test
    fun `detects too large overpass response message through nested cause`() {
        val error = IllegalStateException(
            "outer",
            IllegalStateException(OSM_OVERPASS_TOO_LARGE_MESSAGE)
        )

        assertTrue(isOverpassResponseTooLarge(error))
        assertFalse(isOverpassResponseTooLarge(IllegalStateException("something else")))
    }

    @Test
    fun `default osm categories keep mountain essentials selected`() {
        val defaultIds = defaultOsmPoiCategoryIds()

        assertEquals(
            linkedSetOf("huts", "water", "peaks"),
            defaultIds
        )
    }

    @Test
    fun `osm category presets only expose essentials and all`() {
        val presets = buildOsmPoiCategoryPresets(
            linkedSetOf("huts", "water", "peaks", "camping", "viewpoints", "food")
        )

        assertEquals(2, presets.size)
        assertEquals("Essentials", presets[0].label)
        assertEquals(linkedSetOf("huts", "water", "peaks"), presets[0].categoryIds)
        assertEquals("All", presets[1].label)
        assertEquals(
            linkedSetOf("huts", "water", "peaks", "camping", "viewpoints", "food"),
            presets[1].categoryIds
        )
    }

    @Test
    fun `normalizes osm categories and drops invalid ids`() {
        val normalized = normalizeOsmPoiCategoryIds(
            linkedSetOf("food", "invalid", "parking", "food")
        )

        assertEquals(linkedSetOf("food", "parking"), normalized)
    }
}
