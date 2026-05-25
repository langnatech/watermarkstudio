package com.watermarkstudio.ui.components

/**
 * Maps media content (0–100%) to preview canvas pixels when the image uses [androidx.compose.ui.layout.ContentScale.Fit].
 * Export uses the same percentages on full media width/height, so preview must use the fitted content rect, not letterbox padding.
 */
internal object WatermarkContentGeometry {

    data class ContentRect(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
    ) {
        val right: Float get() = left + width
        val bottom: Float get() = top + height
    }

    fun fittedContentRect(
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        contentWidthPx: Float,
        contentHeightPx: Float,
    ): ContentRect {
        if (canvasWidthPx <= 0f || canvasHeightPx <= 0f || contentWidthPx <= 0f || contentHeightPx <= 0f) {
            return ContentRect(0f, 0f, canvasWidthPx.coerceAtLeast(1f), canvasHeightPx.coerceAtLeast(1f))
        }
        val scale = minOf(canvasWidthPx / contentWidthPx, canvasHeightPx / contentHeightPx)
        val dw = contentWidthPx * scale
        val dh = contentHeightPx * scale
        return ContentRect(
            left = (canvasWidthPx - dw) / 2f,
            top = (canvasHeightPx - dh) / 2f,
            width = dw,
            height = dh,
        )
    }
}
