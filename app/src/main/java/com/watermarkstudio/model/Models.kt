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

/** Active tool for REMOVE-mode mask editing. */
enum class RemovalBrushTool {
    PAINT,
    ERASER,
    SMART_SELECT,
}

data class RemovalStrokePoint(
    val xPct: Float,
    val yPct: Float,
)

data class RemovalStroke(
    val points: List<RemovalStrokePoint>,
    val radiusPct: Float,
    /** When true, this stroke subtracts from the painted mask (eraser). */
    val isEraser: Boolean = false,
    /**
     * Strokes created by one smart-select fill share the same non-null [batchId]
     * so Undo can remove the whole fill at once.
     */
    val batchId: Long? = null,
)

data class WatermarkConfig(
    val type: WatermarkType,
    val text: String = "",
    val imageUri: Uri? = null,
    val opacity: Float = 0.88f,
    val x: Float = 50f, // Percentage 0-100
    val y: Float = 50f, // Percentage 0-100
    val scale: Float = 1.0f,
    /** Brush radius as % of media shorter edge (REMOVE layers only). */
    val brushRadiusPct: Float = DEFAULT_BRUSH_RADIUS_PCT,
    /** Active REMOVE mask tool. */
    val brushTool: RemovalBrushTool = RemovalBrushTool.PAINT,
    /**
     * Extra morphological erode (px) applied after soft-mask blur to shrink selection edges.
     * 0 keeps the soft mask as generated.
     */
    val maskErodePx: Int = 0,
    /** RGB channel tolerance for [RemovalBrushTool.SMART_SELECT] flood fill. */
    val smartSelectTolerance: Int = DEFAULT_SMART_SELECT_TOLERANCE,
    /**
     * When true and [brushTool] is [RemovalBrushTool.SMART_SELECT], flood-fill strokes
     * are eraser strokes (subtract from mask).
     */
    val smartSelectSubtract: Boolean = false,
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
        const val MAX_MASK_ERODE_PX = 6
        const val DEFAULT_SMART_SELECT_TOLERANCE = 36
        const val MIN_SMART_SELECT_TOLERANCE = 12
        const val MAX_SMART_SELECT_TOLERANCE = 80
    }
}

/** Drops the last paint/eraser stroke, or an entire smart-select batch. */
fun List<RemovalStroke>.dropLastStrokeOrBatch(): List<RemovalStroke> {
    if (isEmpty()) return this
    val batchId = last().batchId
    return if (batchId != null) {
        dropLastWhile { it.batchId == batchId }
    } else {
        dropLast(1)
    }
}
