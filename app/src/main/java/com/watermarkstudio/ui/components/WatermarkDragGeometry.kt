package com.watermarkstudio.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.util.RemovalRegion

/**
 * Maps [WatermarkConfig] x/y (0–100) to overlay top-left in the preview canvas.
 * Aligned with [com.watermarkstudio.util.MediaProcessor] image export semantics.
 */
internal object WatermarkDragGeometry {

    data class PxOffset(val x: Float, val y: Float)

    fun topLeftPx(
        config: WatermarkConfig,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        overlayWidthPx: Float,
        overlayHeightPx: Float,
    ): PxOffset {
        if (canvasWidthPx <= 0f || canvasHeightPx <= 0f) {
            return PxOffset(0f, 0f)
        }
        return when (config.type) {
            WatermarkType.IMAGE -> {
                val rangeX = (canvasWidthPx - overlayWidthPx).coerceAtLeast(1f)
                val rangeY = (canvasHeightPx - overlayHeightPx).coerceAtLeast(1f)
                PxOffset(
                    x = rangeX * (config.x / 100f),
                    y = rangeY * (config.y / 100f),
                )
            }
            WatermarkType.REMOVE -> {
                val cx = canvasWidthPx * (config.x / 100f)
                val cy = canvasHeightPx * (config.y / 100f)
                PxOffset(
                    x = cx - overlayWidthPx / 2f,
                    y = cy - overlayHeightPx / 2f,
                )
            }
            WatermarkType.TEXT -> {
                PxOffset(
                    x = canvasWidthPx * (config.x / 100f),
                    y = canvasHeightPx * (config.y / 100f),
                )
            }
        }
    }

    fun configFromTopLeftPx(
        config: WatermarkConfig,
        topLeft: PxOffset,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        overlayWidthPx: Float,
        overlayHeightPx: Float,
    ): WatermarkConfig {
        val maxX = (canvasWidthPx - overlayWidthPx).coerceAtLeast(0f)
        val maxY = (canvasHeightPx - overlayHeightPx).coerceAtLeast(0f)
        val xPx = topLeft.x.coerceIn(0f, maxX)
        val yPx = topLeft.y.coerceIn(0f, maxY)
        return when (config.type) {
            WatermarkType.IMAGE -> {
                val rangeX = (canvasWidthPx - overlayWidthPx).coerceAtLeast(1f)
                val rangeY = (canvasHeightPx - overlayHeightPx).coerceAtLeast(1f)
                config.copy(
                    x = (xPx / rangeX * 100f).coerceIn(0f, 100f),
                    y = (yPx / rangeY * 100f).coerceIn(0f, 100f),
                )
            }
            WatermarkType.REMOVE -> {
                val cx = xPx + overlayWidthPx / 2f
                val cy = yPx + overlayHeightPx / 2f
                config.copy(
                    x = (cx / canvasWidthPx * 100f).coerceIn(0f, 100f),
                    y = (cy / canvasHeightPx * 100f).coerceIn(0f, 100f),
                )
            }
            WatermarkType.TEXT -> {
                config.copy(
                    x = (xPx / canvasWidthPx * 100f).coerceIn(0f, 100f),
                    y = (yPx / canvasHeightPx * 100f).coerceIn(0f, 100f),
                )
            }
        }
    }

    fun overlaySizeDp(
        config: WatermarkConfig,
        canvasW: Dp,
        canvasH: Dp,
        textOverlayW: Dp,
        textOverlayH: Dp,
    ): Pair<Dp, Dp> =
        when (config.type) {
            WatermarkType.TEXT -> textOverlayW to textOverlayH
            WatermarkType.IMAGE -> {
                val side = (80f * config.scale).dp
                side to side
            }
            WatermarkType.REMOVE -> {
                canvasW * RemovalRegion.WIDTH_RATIO * config.scale to
                    canvasH * RemovalRegion.HEIGHT_RATIO * config.scale
            }
        }
}
