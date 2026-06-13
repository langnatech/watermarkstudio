package com.watermarkstudio.removal

import kotlin.math.min

/**
 * Scales large-region inpaint thresholds when preview resolution is below export cap.
 */
data class PreviewScaleContext(
    val exportMaxDim: Int,
    val previewMaxDim: Int,
) {
    init {
        require(exportMaxDim > 0)
        require(previewMaxDim > 0)
    }

    val areaScale: Float
        get() {
            val ratio = previewMaxDim.toFloat() / exportMaxDim.toFloat()
            return (ratio * ratio).coerceIn(0.05f, 1f)
        }

    companion object {
        fun forBitmap(exportMaxDim: Int, width: Int, height: Int): PreviewScaleContext {
            val previewMaxDim = min(width, height).coerceAtLeast(1)
            return PreviewScaleContext(exportMaxDim, previewMaxDim)
        }
    }
}
