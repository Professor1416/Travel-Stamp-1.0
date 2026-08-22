package com.example

import com.example.data.local.entity.MomentEntity
import com.example.data.model.HyperlinkUtils
import com.example.data.model.Moment
import com.example.data.model.MomentCategory
import com.example.data.model.MomentHyperlink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MomentHyperlinkUnitTest {

    @Test
    fun testNormalizeUrl_validHttpAndHttps() {
        assertEquals("https://maps.app.goo.gl/123", HyperlinkUtils.normalizeUrl("https://maps.app.goo.gl/123"))
        assertEquals("http://example.com/guide", HyperlinkUtils.normalizeUrl("http://example.com/guide"))
        // Auto-prepend https://
        assertEquals("https://www.nationalparks.org", HyperlinkUtils.normalizeUrl("www.nationalparks.org"))
        assertEquals("https://trailhead.com/path", HyperlinkUtils.normalizeUrl("trailhead.com/path"))
    }

    @Test
    fun testNormalizeUrl_rejectDangerousSchemes() {
        assertNull(HyperlinkUtils.normalizeUrl("javascript:alert(1)"))
        assertNull(HyperlinkUtils.normalizeUrl("file:///android_asset/secret.txt"))
        assertNull(HyperlinkUtils.normalizeUrl("data:text/html,<b>hi</b>"))
        assertNull(HyperlinkUtils.normalizeUrl("content://media/external/images/media/1"))
        assertNull(HyperlinkUtils.normalizeUrl(""))
        assertNull(HyperlinkUtils.normalizeUrl("   "))
        assertNull(HyperlinkUtils.normalizeUrl("not a valid url with spaces"))
    }

    @Test
    fun testJsonSerialization_roundTrip() {
        val original = listOf(
            MomentHyperlink(startIndex = 0, endIndex = 10, url = "https://example.com/1"),
            MomentHyperlink(startIndex = 15, endIndex = 30, url = "https://example.com/2")
        )

        val json = HyperlinkUtils.serializeToJson(original)
        assertNotNull(json)

        val parsed = HyperlinkUtils.parseFromJson(json)
        assertEquals(2, parsed.size)
        assertEquals(0, parsed[0].startIndex)
        assertEquals(10, parsed[0].endIndex)
        assertEquals("https://example.com/1", parsed[0].url)
        assertEquals(15, parsed[1].startIndex)
        assertEquals(30, parsed[1].endIndex)
        assertEquals("https://example.com/2", parsed[1].url)
    }

    @Test
    fun testJsonSerialization_nullAndEmptySafety() {
        assertTrue(HyperlinkUtils.parseFromJson(null).isEmpty())
        assertTrue(HyperlinkUtils.parseFromJson("").isEmpty())
        assertTrue(HyperlinkUtils.parseFromJson("[]").isEmpty())
        assertTrue(HyperlinkUtils.parseFromJson("not json").isEmpty())
    }

    @Test
    fun testSpanAdjustments_insertTextBeforeSpan() {
        val originalLinks = listOf(
            MomentHyperlink(startIndex = 10, endIndex = 15, url = "https://example.com")
        )
        val oldText = "Hello all great world"
        val newText = "Hello all my great world" // inserted "my " (3 chars) before index 10

        val adjusted = HyperlinkUtils.adjustHyperlinksOnTextChange(
            oldText = oldText,
            newText = newText,
            existingLinks = originalLinks
        )

        assertEquals(1, adjusted.size)
        assertEquals(13, adjusted[0].startIndex)
        assertEquals(18, adjusted[0].endIndex)
        assertEquals("https://example.com", adjusted[0].url)
    }

    @Test
    fun testSpanAdjustments_insertTextInsideSpan() {
        val originalLinks = listOf(
            MomentHyperlink(startIndex = 6, endIndex = 11, url = "https://example.com")
        )
        val oldText = "Visit Paris now"
        val newText = "Visit Par-is now" // inserted "-is" inside "Paris" (1 char inserted inside)

        val adjusted = HyperlinkUtils.adjustHyperlinksOnTextChange(
            oldText = oldText,
            newText = newText,
            existingLinks = originalLinks
        )

        assertEquals(1, adjusted.size)
        assertEquals(6, adjusted[0].startIndex)
        assertEquals(12, adjusted[0].endIndex)
        assertEquals("https://example.com", adjusted[0].url)
    }

    @Test
    fun testSpanAdjustments_deleteTextEncompassingSpan() {
        val originalLinks = listOf(
            MomentHyperlink(startIndex = 6, endIndex = 11, url = "https://example.com")
        )
        val oldText = "Visit Paris now"
        val newText = "Visit now" // deleted "Paris "

        val adjusted = HyperlinkUtils.adjustHyperlinksOnTextChange(
            oldText = oldText,
            newText = newText,
            existingLinks = originalLinks
        )

        // The linked span was completely removed, so link should be gracefully dropped
        assertTrue(adjusted.isEmpty())
    }

    @Test
    fun testCleanupAndDeduplicateSpans() {
        val rawLinks = listOf(
            MomentHyperlink(startIndex = 0, endIndex = 10, url = "https://a.com"),
            MomentHyperlink(startIndex = 5, endIndex = 20, url = "https://b.com"), // overlaps with 0..10
            MomentHyperlink(startIndex = 25, endIndex = 35, url = "https://c.com")
        )

        val cleaned = HyperlinkUtils.cleanupAndDeduplicateSpans(rawLinks, textLength = 30)
        assertEquals(2, cleaned.size)
        assertEquals(0, cleaned[0].startIndex)
        assertEquals(10, cleaned[0].endIndex)
        assertEquals("https://a.com", cleaned[0].url)
        assertEquals(25, cleaned[1].startIndex)
        assertEquals(30, cleaned[1].endIndex)
        assertEquals("https://c.com", cleaned[1].url)
    }

    @Test
    fun testGetAllDisplayLinks_autoDetectRawUrls() {
        val text = "Check out https://trailguide.org for details and https://maps.google.com"
        val explicitLinks = emptyList<MomentHyperlink>()

        val displayLinks = HyperlinkUtils.getAllDisplayLinks(text, explicitLinks)
        assertEquals(2, displayLinks.size)
        assertEquals("https://trailguide.org", displayLinks[0].url)
        assertEquals("https://maps.google.com", displayLinks[1].url)
    }

    @Test
    fun testMomentEntity_domainMapping() {
        val domain = Moment(
            id = 42L,
            uuid = "test-uuid-42",
            tripId = 7L,
            category = MomentCategory.FOOD,
            note = "Had local ramen at the summit",
            hyperlinks = listOf(
                MomentHyperlink(startIndex = 4, endIndex = 15, url = "https://ramen.guide")
            ),
            imageUri = "file:///storage/moment.jpg",
            timestamp = 1700000000000L
        )

        val entity = MomentEntity.fromDomain(domain)
        assertEquals(42L, entity.id)
        assertEquals("test-uuid-42", entity.uuid)
        assertEquals(7L, entity.tripId)
        assertEquals("FOOD", entity.category)
        assertNotNull(entity.hyperlinksJson)

        val restoredDomain = entity.toDomain()
        assertEquals(domain.id, restoredDomain.id)
        assertEquals(domain.uuid, restoredDomain.uuid)
        assertEquals(domain.category, restoredDomain.category)
        assertEquals(domain.note, restoredDomain.note)
        assertEquals(1, restoredDomain.hyperlinks.size)
        assertEquals(4, restoredDomain.hyperlinks[0].startIndex)
        assertEquals(15, restoredDomain.hyperlinks[0].endIndex)
        assertEquals("https://ramen.guide", restoredDomain.hyperlinks[0].url)
    }
}
