package com.example.ui.poster

/**
 * Configuration parameters for rendering a 1080x1920 poster.
 * Decoupled from entitlment logic and ready for future Phase 5 extensions.
 */
data class PosterRenderConfig(
    val template: PosterTemplate = PosterTemplate.PHOTO_STAMP,
    val photoUri: String? = null,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f
)
