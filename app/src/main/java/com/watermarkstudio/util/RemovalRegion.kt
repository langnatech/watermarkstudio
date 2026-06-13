package com.watermarkstudio.util

import android.graphics.Rect
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.mask.BrushStrokeGeometry

/**
 * Performance crop bounding box around painted strokes for PatchMatch / temporal recovery.
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
        const val HEAL_GRID_SIZE = 16
        private const val MIN_REGION_SIZE = 10
        private const val STROKE_REGION_PADDING_FACTOR = 1.25f

        fun fromConfig(bitmapWidth: Int, bitmapHeight: Int, config: WatermarkConfig): RemovalRegion =
            if (config.removalStrokes.isNotEmpty()) {
                fromStrokes(bitmapWidth, bitmapHeight, config)
            } else {
                empty(bitmapWidth, bitmapHeight)
            }

        fun fromStrokes(bitmapWidth: Int, bitmapHeight: Int, config: WatermarkConfig): RemovalRegion {
            if (bitmapWidth <= 0 || bitmapHeight <= 0 || config.removalStrokes.isEmpty()) {
                return empty(bitmapWidth, bitmapHeight)
            }

            var minX = bitmapWidth.toFloat()
            var minY = bitmapHeight.toFloat()
            var maxX = 0f
            var maxY = 0f
            var hasPoint = false
            var maxRadiusPx = 0f

            config.removalStrokes.forEach { stroke ->
                val radiusPx =
                    BrushStrokeGeometry.strokeRadiusPx(bitmapWidth, bitmapHeight, stroke.radiusPct)
                        .coerceAtLeast(1f)
                maxRadiusPx = maxOf(maxRadiusPx, radiusPx)
                stroke.points.forEach { point ->
                    val x = bitmapWidth * (point.xPct / 100f)
                    val y = bitmapHeight * (point.yPct / 100f)
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                    hasPoint = true
                }
            }

            if (!hasPoint) return empty(bitmapWidth, bitmapHeight)

            val padding = (maxRadiusPx * STROKE_REGION_PADDING_FACTOR).toInt().coerceAtLeast(1)
            val left = (minX.toInt() - padding).coerceIn(0, bitmapWidth)
            val top = (minY.toInt() - padding).coerceIn(0, bitmapHeight)
            val right = (maxX.toInt() + padding).coerceIn(0, bitmapWidth)
            val bottom = (maxY.toInt() + padding).coerceIn(0, bitmapHeight)

            return if (right - left >= MIN_REGION_SIZE && bottom - top >= MIN_REGION_SIZE) {
                RemovalRegion(left, top, right, bottom)
            } else {
                val centerX = ((left + right) / 2).coerceIn(0, bitmapWidth)
                val centerY = ((top + bottom) / 2).coerceIn(0, bitmapHeight)
                val half = MIN_REGION_SIZE / 2
                RemovalRegion(
                    (centerX - half).coerceIn(0, bitmapWidth),
                    (centerY - half).coerceIn(0, bitmapHeight),
                    (centerX + half).coerceIn(0, bitmapWidth),
                    (centerY + half).coerceIn(0, bitmapHeight),
                )
            }
        }

        fun empty(bitmapWidth: Int, bitmapHeight: Int): RemovalRegion =
            RemovalRegion(
                0,
                0,
                0.coerceAtMost(bitmapWidth.coerceAtLeast(0)),
                0.coerceAtMost(bitmapHeight.coerceAtLeast(0)),
            )
    }
}
