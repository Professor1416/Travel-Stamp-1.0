package com.example.data.model

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Structured hyperlink metadata associated with a range of text in a Moment Note.
 *
 * @property startIndex Inclusive 0-based character index in the Note text.
 * @property endIndex Exclusive 0-based character index in the Note text.
 * @property url Normalized web destination (HTTP or HTTPS only).
 */
data class MomentHyperlink(
    val startIndex: Int,
    val endIndex: Int,
    val url: String
) {
    /**
     * Validates whether this hyperlink span fits within a given note length.
     */
    fun isValid(textLength: Int): Boolean {
        return startIndex in 0..textLength &&
                endIndex in 0..textLength &&
                startIndex < endIndex &&
                url.isNotBlank() &&
                HyperlinkUtils.isSafeWebUrl(url)
    }
}

object HyperlinkUtils {

    private val RAW_URL_REGEX = Regex(
        """(https?://[^\s<>]+|www\.[^\s<>]+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Validates and normalizes user input into a safe HTTP/HTTPS URL.
     * Rejects dangerous schemes like javascript:, file:, content:, intent:, data:.
     */
    fun normalizeUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        val lower = trimmed.lowercase(Locale.ROOT)
        // Reject dangerous / unsupported URI schemes
        if (lower.startsWith("javascript:") ||
            lower.startsWith("file:") ||
            lower.startsWith("content:") ||
            lower.startsWith("intent:") ||
            lower.startsWith("data:") ||
            lower.startsWith("about:") ||
            lower.startsWith("blob:") ||
            lower.startsWith("chrome:") ||
            lower.startsWith("market:") ||
            lower.startsWith("tel:") ||
            lower.startsWith("sms:") ||
            lower.startsWith("mailto:")
        ) {
            return null
        }

        val urlWithScheme = when {
            lower.startsWith("https://") || lower.startsWith("http://") -> trimmed
            lower.startsWith("www.") -> "https://$trimmed"
            !trimmed.contains("://") && trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> return null
        }

        return try {
            val uri = Uri.parse(urlWithScheme)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            val host = uri.host
            if ((scheme == "http" || scheme == "https") && !host.isNullOrBlank()) {
                urlWithScheme
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks if a URL is a valid, safe HTTP/HTTPS link.
     */
    fun isSafeWebUrl(url: String?): Boolean {
        return normalizeUrl(url) != null
    }

    /**
     * Serializes a list of hyperlink spans to a JSON string for persistence.
     */
    fun serializeToJson(links: List<MomentHyperlink>): String {
        if (links.isEmpty()) return "[]"
        val array = JSONArray()
        links.forEach { link ->
            val obj = JSONObject().apply {
                put("startIndex", link.startIndex)
                put("endIndex", link.endIndex)
                put("url", link.url)
            }
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Deserializes a JSON string into a list of sorted, valid hyperlink spans.
     */
    fun parseFromJson(json: String?): List<MomentHyperlink> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<MomentHyperlink>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val start = obj.getInt("startIndex")
                val end = obj.getInt("endIndex")
                val url = obj.getString("url")
                val normalized = normalizeUrl(url)
                if (start in 0 until end && normalized != null) {
                    list.add(MomentHyperlink(startIndex = start, endIndex = end, url = normalized))
                }
            }
            list.sortedBy { it.startIndex }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Finds raw HTTP/HTTPS URLs present in the text that do not overlap with any explicit hyperlink span.
     */
    fun extractRawUrls(
        text: String,
        explicitLinks: List<MomentHyperlink>
    ): List<MomentHyperlink> {
        if (text.isBlank()) return emptyList()
        val rawLinks = mutableListOf<MomentHyperlink>()

        for (match in RAW_URL_REGEX.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            val rawValue = match.value

            val normalized = normalizeUrl(rawValue) ?: continue

            // Check if this raw URL overlaps with any explicit hyperlink
            val overlaps = explicitLinks.any { explicit ->
                start < explicit.endIndex && end > explicit.startIndex
            }

            if (!overlaps) {
                rawLinks.add(
                    MomentHyperlink(
                        startIndex = start,
                        endIndex = end,
                        url = normalized
                    )
                )
            }
        }
        return rawLinks
    }

    /**
     * Merges explicit hyperlink spans with non-overlapping raw URLs in display order.
     */
    fun getAllDisplayLinks(
        text: String,
        explicitLinks: List<MomentHyperlink>
    ): List<MomentHyperlink> {
        val validExplicit = explicitLinks.filter { it.isValid(text.length) }
        val rawLinks = extractRawUrls(text, validExplicit)
        return (validExplicit + rawLinks).sortedBy { it.startIndex }
    }

    /**
     * Adjusts hyperlink character spans dynamically when the user modifies the text.
     * Correctly handles insertions, deletions, character edits, and pasted content
     * before, inside, and after hyperlinks without corrupting ranges.
     */
    fun adjustHyperlinksOnTextChange(
        oldText: String,
        newText: String,
        existingLinks: List<MomentHyperlink>
    ): List<MomentHyperlink> {
        if (existingLinks.isEmpty()) return emptyList()
        if (newText.isEmpty()) return emptyList()
        if (oldText == newText) return existingLinks.filter { it.isValid(newText.length) }

        val oldLen = oldText.length
        val newLen = newText.length

        // Find common prefix
        var prefixLen = 0
        while (prefixLen < oldLen && prefixLen < newLen && oldText[prefixLen] == newText[prefixLen]) {
            prefixLen++
        }

        // Find common suffix
        var suffixLen = 0
        while (suffixLen < (oldLen - prefixLen) && suffixLen < (newLen - prefixLen) &&
            oldText[oldLen - 1 - suffixLen] == newText[newLen - 1 - suffixLen]
        ) {
            suffixLen++
        }

        val editStartInOld = prefixLen
        val editEndInOld = oldLen - suffixLen
        val lengthDelta = newLen - oldLen

        val adjusted = mutableListOf<MomentHyperlink>()

        for (link in existingLinks) {
            val start = link.startIndex
            val end = link.endIndex

            if (end <= editStartInOld) {
                // Completely before edit: unchanged
                if (start in 0 until end && end <= newLen) {
                    adjusted.add(link)
                }
            } else if (start >= editEndInOld) {
                // Completely after edit: shifted by lengthDelta
                val newStart = start + lengthDelta
                val newEnd = end + lengthDelta
                if (newStart in 0 until newEnd && newEnd <= newLen) {
                    adjusted.add(link.copy(startIndex = newStart, endIndex = newEnd))
                }
            } else if (editStartInOld >= start && editEndInOld <= end) {
                // Edit happened strictly INSIDE or spanning the linked text
                val newEnd = end + lengthDelta
                if (start in 0 until newEnd && newEnd <= newLen) {
                    adjusted.add(link.copy(startIndex = start, endIndex = newEnd))
                }
            } else {
                // Edit overlapped or deleted a boundary or the whole link
                val newStart = when {
                    start < editStartInOld -> start
                    start >= editEndInOld -> start + lengthDelta
                    else -> editStartInOld
                }
                val newEnd = when {
                    end <= editStartInOld -> end
                    end > editEndInOld -> end + lengthDelta
                    else -> editStartInOld + (newLen - prefixLen - suffixLen)
                }

                if (newStart in 0 until newEnd && newEnd <= newLen) {
                    adjusted.add(link.copy(startIndex = newStart, endIndex = newEnd))
                }
            }
        }

        // Ensure no invalid spans or overlapping spans
        return cleanupAndDeduplicateSpans(adjusted, newLen)
    }

    /**
     * Cleans up spans: clamps to text bounds, removes zero-length spans, and resolves overlaps.
     */
    fun cleanupAndDeduplicateSpans(
        links: List<MomentHyperlink>,
        textLength: Int
    ): List<MomentHyperlink> {
        val valid = links.mapNotNull { link ->
            val start = link.startIndex.coerceIn(0, textLength)
            val end = link.endIndex.coerceIn(0, textLength)
            val normalized = normalizeUrl(link.url)
            if (start < end && normalized != null) {
                MomentHyperlink(startIndex = start, endIndex = end, url = normalized)
            } else null
        }.sortedBy { it.startIndex }

        val nonOverlapping = mutableListOf<MomentHyperlink>()
        var lastEnd = 0
        for (link in valid) {
            if (link.startIndex >= lastEnd) {
                nonOverlapping.add(link)
                lastEnd = link.endIndex
            }
        }
        return nonOverlapping
    }
}
