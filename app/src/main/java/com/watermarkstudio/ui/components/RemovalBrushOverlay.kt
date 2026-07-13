package com.watermarkstudio.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.watermarkstudio.model.RemovalBrushTool
import com.watermarkstudio.model.RemovalStroke
import com.watermarkstudio.model.RemovalStrokePoint
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.mask.BrushStrokeGeometry
import com.watermarkstudio.removal.mask.RemovalSmartSelect
import kotlin.math.roundToInt

@Composable
fun RemovalBrushOverlay(
    config: WatermarkConfig,
    previewBitmap: Bitmap?,
    /** Original (non-inpainted) frame for smart-select color sampling; falls back to [previewBitmap]. */
    sourceBitmap: Bitmap? = null,
    mediaWidthPx: Float?,
    mediaHeightPx: Float?,
    brushEnabled: Boolean,
    isActiveLayer: Boolean,
    onConfigUpdate: (WatermarkConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layoutBitmap = previewBitmap ?: sourceBitmap
    var currentPoints by remember(config.removalStrokes.size, config.brushTool) {
        mutableStateOf<List<RemovalStrokePoint>>(emptyList())
    }

    BoxWithConstraints(modifier = modifier) {
        val canvasWidthPx = with(density) { maxWidth.toPx() }
        val canvasHeightPx = with(density) { maxHeight.toPx() }
        val contentSourceWidth =
            layoutBitmap?.width?.toFloat()
                ?: mediaWidthPx
                ?: return@BoxWithConstraints
        val contentSourceHeight =
            layoutBitmap?.height?.toFloat()
                ?: mediaHeightPx
                ?: return@BoxWithConstraints
        val contentRect =
            remember(canvasWidthPx, canvasHeightPx, contentSourceWidth, contentSourceHeight) {
                WatermarkContentGeometry.fittedContentRect(
                    canvasWidthPx,
                    canvasHeightPx,
                    contentSourceWidth,
                    contentSourceHeight,
                )
            }

        val canPaint = brushEnabled && isActiveLayer
        val sampleBitmap = sourceBitmap ?: previewBitmap
        val gestureModifier =
            if (!canPaint) {
                Modifier
            } else if (config.brushTool == RemovalBrushTool.SMART_SELECT) {
                Modifier.pointerInput(
                    contentRect,
                    sampleBitmap,
                    config.smartSelectTolerance,
                    config.smartSelectSubtract,
                ) {
                    detectTapGestures { tap ->
                        val point = tap.toStrokePoint(contentRect) ?: return@detectTapGestures
                        val bitmap = sampleBitmap ?: return@detectTapGestures
                        val seedX = (point.xPct / 100f * bitmap.width).roundToInt()
                            .coerceIn(0, bitmap.width - 1)
                        val seedY = (point.yPct / 100f * bitmap.height).roundToInt()
                            .coerceIn(0, bitmap.height - 1)
                        val batchId = System.nanoTime()
                        val smartStrokes =
                            RemovalSmartSelect.floodFillToStrokes(
                                bitmap = bitmap,
                                seedX = seedX,
                                seedY = seedY,
                                colorTolerance = config.smartSelectTolerance,
                                batchId = batchId,
                                isEraser = config.smartSelectSubtract,
                            )
                        if (smartStrokes.isNotEmpty()) {
                            onConfigUpdate(
                                config.copy(removalStrokes = config.removalStrokes + smartStrokes),
                            )
                        }
                    }
                }
            } else {
                Modifier.pointerInput(contentRect, config.brushRadiusPct, config.brushTool) {
                    detectDragGestures(
                        onDragStart = { start ->
                            start.toStrokePoint(contentRect)?.let { point ->
                                currentPoints = listOf(point)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            change.position.toStrokePoint(contentRect)?.let { point ->
                                currentPoints = currentPoints + point
                            }
                        },
                        onDragEnd = {
                            if (currentPoints.isNotEmpty()) {
                                onConfigUpdate(
                                    config.copy(
                                        removalStrokes =
                                            config.removalStrokes +
                                                RemovalStroke(
                                                    points = currentPoints,
                                                    radiusPct = config.brushRadiusPct,
                                                    isEraser = config.brushTool == RemovalBrushTool.ERASER,
                                                ),
                                    ),
                                )
                            }
                            currentPoints = emptyList()
                        },
                        onDragCancel = {
                            currentPoints = emptyList()
                        },
                    )
                }
            }

        Canvas(modifier = Modifier.fillMaxSize().then(gestureModifier)) {
            val paintAlpha =
                when {
                    !brushEnabled -> 0.2f
                    isActiveLayer -> 0.72f
                    else -> 0.36f
                }
            config.removalStrokes.forEach { stroke ->
                val strokeColor =
                    if (stroke.isEraser) {
                        Color(0xFFF59E0B).copy(alpha = paintAlpha * 0.85f)
                    } else {
                        Color(0xFF10B981).copy(alpha = paintAlpha)
                    }
                drawStroke(
                    stroke.points,
                    stroke.radiusPct,
                    contentRect,
                    contentSourceWidth,
                    contentSourceHeight,
                    strokeColor,
                )
            }
            if (canPaint && config.brushTool != RemovalBrushTool.SMART_SELECT) {
                val activeColor =
                    if (config.brushTool == RemovalBrushTool.ERASER) {
                        Color(0xFFFBBF24).copy(alpha = 0.9f)
                    } else {
                        Color(0xFF34D399).copy(alpha = 0.86f)
                    }
                drawStroke(
                    currentPoints,
                    config.brushRadiusPct,
                    contentRect,
                    contentSourceWidth,
                    contentSourceHeight,
                    activeColor,
                )
            }
        }
    }
}

private fun Offset.toStrokePoint(
    contentRect: WatermarkContentGeometry.ContentRect,
): RemovalStrokePoint? {
    if (
        x < contentRect.left ||
        x > contentRect.right ||
        y < contentRect.top ||
        y > contentRect.bottom
    ) {
        return null
    }
    return RemovalStrokePoint(
        xPct = ((x - contentRect.left) / contentRect.width * 100f).coerceIn(0f, 100f),
        yPct = ((y - contentRect.top) / contentRect.height * 100f).coerceIn(0f, 100f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    points: List<RemovalStrokePoint>,
    radiusPct: Float,
    contentRect: WatermarkContentGeometry.ContentRect,
    mediaWidthPx: Float,
    mediaHeightPx: Float,
    color: Color,
) {
    if (points.isEmpty()) return
    val mediaDiameter = BrushStrokeGeometry.strokeDiameterPx(mediaWidthPx, mediaHeightPx, radiusPct)
    val displayScale = contentRect.width / mediaWidthPx
    val strokeWidth = (mediaDiameter * displayScale).coerceAtLeast(4f)
    if (points.size == 1) {
        val p = points.first().toOffset(contentRect)
        drawCircle(color = color, radius = strokeWidth / 2f, center = p)
        return
    }
    points.zipWithNext().forEach { (from, to) ->
        drawLine(
            color = color,
            start = from.toOffset(contentRect),
            end = to.toOffset(contentRect),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun RemovalStrokePoint.toOffset(contentRect: WatermarkContentGeometry.ContentRect): Offset =
    Offset(
        x = contentRect.left + contentRect.width * (xPct / 100f),
        y = contentRect.top + contentRect.height * (yPct / 100f),
    )
