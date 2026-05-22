package com.watermarkstudio.util

import android.graphics.Rect
import com.watermarkstudio.model.WatermarkConfig

/**
 * Shared removal rectangle for preview overlay and [MediaProcessor] image healing.
 */
data class RemovalRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    fun toRect(): Rect = Rect(left, top, right, bottom)

    companion object {
        const val WIDTH_RATIO = 0.18f
        const val HEIGHT_RATIO = 0.08f
        const val HEAL_GRID_SIZE = 16

        fun fromConfig(bitmapWidth: Int, bitmapHeight: Int, config: WatermarkConfig): RemovalRegion {
            val rectWidth = (bitmapWidth * WIDTH_RATIO * config.scale).toInt().coerceAtLeast(10)
            val rectHeight = (bitmapHeight * HEIGHT_RATIO * config.scale).toInt().coerceAtLeast(10)
            val centerX = (bitmapWidth * (config.x / 100f)).toInt()
            val centerY = (bitmapHeight * (config.y / 100f)).toInt()

            val left = (centerX - rectWidth / 2).coerceIn(0, bitmapWidth - 1)
            val top = (centerY - rectHeight / 2).coerceIn(0, bitmapHeight - 1)
            val right = (centerX + rectWidth / 2).coerceIn(0, bitmapWidth)
            val bottom = (centerY + rectHeight / 2).coerceIn(0, bitmapHeight)

            return RemovalRegion(left, top, right, bottom)
        }
    }
}
