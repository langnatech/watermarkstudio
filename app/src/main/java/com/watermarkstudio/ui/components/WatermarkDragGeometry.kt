package com.watermarkstudio.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.util.RemovalRegion

/**
 * Maps [WatermarkConfig] x/y (0–100 of **media content**) to overlay top-left on the preview canvas.
 * When [contentRect] is set (Fit letterboxing), coordinates align with export on full-resolution media.
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
            WatermarkType.REMOVE -> {
                val cx = area.left + area.width * (config.x / 100f)
                val cy = area.top + area.height * (config.y / 100f)
                PxOffset(
                    x = cx - overlayWidthPx / 2f,
                    y = cy - overlayHeightPx / 2f,
                )
            }
            WatermarkType.TEXT -> {
                PxOffset(
                    x = area.left + area.width * (config.x / 100f),
                    y = area.top + area.height * (config.y / 100f),
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
        contentRect: WatermarkContentGeometry.ContentRect? = null,
    ): WatermarkConfig {
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
            WatermarkType.REMOVE -> {
                val cx = xPx + overlayWidthPx / 2f
                val cy = yPx + overlayHeightPx / 2f
                config.copy(
                    x = ((cx - area.left) / area.width * 100f).coerceIn(0f, 100f),
                    y = ((cy - area.top) / area.height * 100f).coerceIn(0f, 100f),
                )
            }
            WatermarkType.TEXT -> {
                config.copy(
                    x = ((xPx - area.left) / area.width * 100f).coerceIn(0f, 100f),
                    y = ((yPx - area.top) / area.height * 100f).coerceIn(0f, 100f),
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
        contentWidthDp: Dp? = null,
        contentHeightDp: Dp? = null,
    ): Pair<Dp, Dp> =
        when (config.type) {
            WatermarkType.TEXT -> textOverlayW to textOverlayH
            WatermarkType.IMAGE -> {
                val side = (80f * config.scale).dp
                side to side
            }
            WatermarkType.REMOVE -> {
                val w = contentWidthDp ?: canvasW
                val h = contentHeightDp ?: canvasH
                w * RemovalRegion.WIDTH_RATIO * config.scale to
                    h * RemovalRegion.HEIGHT_RATIO * config.scale
            }
        }
}
