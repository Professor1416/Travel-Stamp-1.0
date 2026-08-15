package com.example.data.model

enum class MomentCategory(
    val title: String,
    val emoji: String,
    val description: String,
    val badgeColorHex: String
) {
    PHOTO(
        title = "Photo",
        emoji = "📸",
        description = "A visual glimpse of the trail",
        badgeColorHex = "#3A506B"
    ),
    NOTE(
        title = "Note",
        emoji = "📝",
        description = "Thoughts, quotes & field notes",
        badgeColorHex = "#4A4E69"
    ),
    CHAI(
        title = "Chai",
        emoji = "☕",
        description = "Steaming tea by misty stops",
        badgeColorHex = "#B07D46"
    ),
    RAIN(
        title = "Rain",
        emoji = "🌧️",
        description = "Monsoon showers & mountain mist",
        badgeColorHex = "#3A86FF"
    ),
    VIEW(
        title = "View",
        emoji = "🏔️",
        description = "Epic ridges, peaks & horizons",
        badgeColorHex = "#1E3A2F"
    ),
    MEMORY(
        title = "Memory",
        emoji = "❤️",
        description = "Special unrepeatable moments",
        badgeColorHex = "#C85A32"
    ),
    FUN(
        title = "Fun",
        emoji = "😂",
        description = "Laughs, inside jokes & stories",
        badgeColorHex = "#D4A373"
    ),
    FOOD(
        title = "Food",
        emoji = "🍴",
        description = "Local delicacies & summit snacks",
        badgeColorHex = "#8B5E34"
    );

    companion object {
        fun fromName(name: String?): MomentCategory {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: NOTE
        }
    }
}
