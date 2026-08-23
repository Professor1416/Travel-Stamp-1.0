package com.example.ui.poster

/**
 * Configuration parameters for rendering a Travel Stamp edition or story poster.
 * Decoupled from entitlement logic and strictly presentation/export state.
 */
data class PosterRenderConfig(
    val template: PosterTemplate = PosterTemplate.PASSPORT_STAMP,
    val format: StampEditionFormat = StampEditionFormat.PORTRAIT,
    val photoUri: String? = null,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f,
    val stampSize: StampSize = StampSize.MEDIUM,
    val stampPositionX: Float = 0.5f,
    val stampPositionY: Float = 0.44f
)
