package com.example.ui.poster

/**
 * Stamp Format presentation options for Stamp Edition creation and export.
 * Note: Format is presentation/export UI state only and is NOT persisted in Room database.
 */
enum class StampEditionFormat(
    val id: String,
    val title: String,
    val description: String,
    val width: Int,
    val height: Int,
    val aspectRatio: Float
) {
    SQUARE(
        id = "SQUARE",
        title = "Square",
        description = "Balanced square format",
        width = 1080,
        height = 1080,
        aspectRatio = 1f
    ),
    PORTRAIT(
        id = "PORTRAIT",
        title = "Portrait",
        description = "Classic portrait format (4:5)",
        width = 1080,
        height = 1350,
        aspectRatio = 4f / 5f
    ),
    STORY(
        id = "STORY",
        title = "Story",
        description = "Full vertical story format",
        width = 1080,
        height = 1920,
        aspectRatio = 9f / 16f
    )
}
