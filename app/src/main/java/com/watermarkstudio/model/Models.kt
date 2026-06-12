package com.watermarkstudio.model

import android.net.Uri

enum class MediaType {
    IMAGE, VIDEO
}

data class MediaItem(
    val uri: Uri,
    val type: MediaType,
    val name: String,
    val size: Long = 0,
    val duration: Long = 0 // For videos
)

enum class WatermarkType {
    TEXT, IMAGE, REMOVE
}

/** Maps to Android [android.graphics.Typeface] and Compose [androidx.compose.ui.text.font.FontFamily]. */
enum class WatermarkFontFamily {
    SANS, SERIF, MONOSPACE, BOLD,
}

data class RemovalStrokePoint(
    val xPct: Float,
    val yPct: Float,
)

data class RemovalStroke(
    val points: List<RemovalStrokePoint>,
    val radiusPct: Float,
)

data class WatermarkConfig(
    val type: WatermarkType,
    val text: String = "",
    val imageUri: Uri? = null,
    val opacity: Float = 0.88f,
    val x: Float = 50f, // Percentage 0-100
    val y: Float = 50f, // Percentage 0-100
    val scale: Float = 1.0f,
    /** Brush radius as % of media width (REMOVE layers only). */
    val brushRadiusPct: Float = DEFAULT_BRUSH_RADIUS_PCT,
    val rotation: Float = 0f,
    /** ARGB; default white. Applied with [opacity] at export/preview time. */
    val color: Int = 0xFFFFFFFF.toInt(),
    /** Text watermark font size in sp (independent of [scale], which is for image/remove layers). */
    val textSizeSp: Float = 24f,
    val fontFamily: WatermarkFontFamily = WatermarkFontFamily.SANS,
    val removalStrokes: List<RemovalStroke> = emptyList(),
) {
    companion object {
        const val DEFAULT_BRUSH_RADIUS_PCT = 2.5f
        const val MIN_BRUSH_RADIUS_PCT = 0.5f
        const val MAX_BRUSH_RADIUS_PCT = 8f
    }
}
