package com.watermarkstudio.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType

/**
 * Maps [WatermarkConfig] x/y (0–100 of **media content**) to overlay top-left on the preview canvas.
 * When [contentRect] is set (Fit letterboxing), coordinates align with export on full-resolution media.
 *
 * REMOVE layers use [RemovalBrushOverlay] instead; do not pass REMOVE configs here.
 */
internal object WatermarkDragGeometry {

    data class PxOffset(val x: Float, val y: Float)

    private fun area(content: WatermarkContentGeometry.ContentRect?, canvasW: Float, canvasH: Float) =
        content ?: WatermarkContentGeometry.ContentRect(0f, 0f, canvasW, canvasH)

    fun topLeftPx(
        config: WatermarkConfig,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        overlayWidthPx: Float,
        overlayHeightPx: Float,
        contentRect: WatermarkContentGeometry.ContentRect? = null,
    ): PxOffset {
        require(config.type != WatermarkType.REMOVE) { "REMOVE uses RemovalBrushOverlay" }
        if (canvasWidthPx <= 0f || canvasHeightPx <= 0f) {
            return PxOffset(0f, 0f)
        }
        val area = area(contentRect, canvasWidthPx, canvasHeightPx)
        return when (config.type) {
            WatermarkType.IMAGE -> {
                val rangeX = (area.width - overlayWidthPx).coerceAtLeast(1f)
                val rangeY = (area.height - overlayHeightPx).coerceAtLeast(1f)
                PxOffset(
                    x = area.left + rangeX * (config.x / 100f),
                    y = area.top + rangeY * (config.y / 100f),
                )
            }
            WatermarkType.TEXT -> {
                PxOffset(
                    x = area.left + area.width * (config.x / 100f),
                    y = area.top + area.height * (config.y / 100f),
                )
            }
            WatermarkType.REMOVE -> error("unreachable")
        }
    }

    fun configFromTopLeftPx(
        config: WatermarkConfig,
        topLeft: PxOffset,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        overlayWidthPx: Float,
        overlayHeightPx: Float,
        contentRect: WatermarkContentGeometry.ContentRect? = null,
    ): WatermarkConfig {
        require(config.type != WatermarkType.REMOVE) { "REMOVE uses RemovalBrushOverlay" }
        val area = area(contentRect, canvasWidthPx, canvasHeightPx)
        val minX = area.left
        val minY = area.top
        val maxX = (area.right - overlayWidthPx).coerceAtLeast(minX)
        val maxY = (area.bottom - overlayHeightPx).coerceAtLeast(minY)
        val xPx = topLeft.x.coerceIn(minX, maxX)
        val yPx = topLeft.y.coerceIn(minY, maxY)
        return when (config.type) {
            WatermarkType.IMAGE -> {
                val rangeX = (area.width - overlayWidthPx).coerceAtLeast(1f)
                val rangeY = (area.height - overlayHeightPx).coerceAtLeast(1f)
                config.copy(
                    x = ((xPx - area.left) / rangeX * 100f).coerceIn(0f, 100f),
                    y = ((yPx - area.top) / rangeY * 100f).coerceIn(0f, 100f),
                )
            }
            WatermarkType.TEXT -> {
                config.copy(
                    x = ((xPx - area.left) / area.width * 100f).coerceIn(0f, 100f),
                    y = ((yPx - area.top) / area.height * 100f).coerceIn(0f, 100f),
                )
            }
            WatermarkType.REMOVE -> error("unreachable")
        }
    }

    fun overlaySizeDp(
        config: WatermarkConfig,
        textOverlayW: Dp,
        textOverlayH: Dp,
    ): Pair<Dp, Dp> {
        require(config.type != WatermarkType.REMOVE) { "REMOVE uses RemovalBrushOverlay" }
        return when (config.type) {
            WatermarkType.TEXT -> textOverlayW to textOverlayH
            WatermarkType.IMAGE -> {
                val side = (80f * config.scale).dp
                side to side
            }
            WatermarkType.REMOVE -> error("unreachable")
        }
    }
}
