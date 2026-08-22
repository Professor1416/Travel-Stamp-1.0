package com.example.data.model

enum class LocationCategory(val displayName: String, val emoji: String) {
    FORT("Fort", "🏰"),
    TREK("Trek", "🥾"),
    WATERFALL("Waterfall", "🌊"),
    TEMPLE("Temple", "🛕"),
    BEACH("Beach", "🏖️"),
    CITY("City", "🏙️"),
    HILL_STATION("Hill Station", "⛰️"),
    MONUMENT("Monument", "🏛️"),
    HERITAGE("Heritage Site", "🏺"),
    NATURE("Nature", "🌲"),
    ATTRACTION("Attraction", "✨"),
    OTHER("Destination", "📍");

    companion object {
        fun fromString(value: String?): LocationCategory {
            if (value == null) return OTHER
            val trimmed = value.trim()
            return entries.firstOrNull {
                it.name.equals(trimmed, ignoreCase = true) ||
                it.displayName.equals(trimmed, ignoreCase = true)
            } ?: OTHER
        }

        fun inferFromName(name: String): LocationCategory {
            val lower = name.lowercase()
            return when {
                lower.contains("fort") || lower.contains("gad") || lower.contains("killa") || lower.contains("durg") -> FORT
                lower.contains("trek") || lower.contains("peak") || lower.contains("shikhar") || lower.contains("pass") -> TREK
                lower.contains("falls") || lower.contains("waterfall") -> WATERFALL
                lower.contains("temple") || lower.contains("mandir") || lower.contains("ghat") -> TEMPLE
                lower.contains("beach") || lower.contains("coast") -> BEACH
                lower.contains("hill") || lower.contains("valley") || lower.contains("plateau") -> HILL_STATION
                lower.contains("monument") || lower.contains("gate") || lower.contains("minar") || lower.contains("mahal") -> MONUMENT
                lower.contains("cave") || lower.contains("caves") || lower.contains("palace") || lower.contains("ruin") -> HERITAGE
                lower.contains("lake") || lower.contains("park") || lower.contains("forest") || lower.contains("sanctuary") -> NATURE
                else -> OTHER
            }
        }
    }
}

data class LocationSuggestion(
    val name: String,
    val destination: String,
    val category: LocationCategory = LocationCategory.OTHER,
    val aliases: List<String> = emptyList(),
    val isFromHistory: Boolean = false
)
