package com.watermarkstudio.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import com.watermarkstudio.model.RemovalStroke
import com.watermarkstudio.model.RemovalStrokePoint
import com.watermarkstudio.model.WatermarkConfig

@Composable
fun RemovalBrushOverlay(
    config: WatermarkConfig,
    previewBitmap: Bitmap?,
    mediaWidthPx: Float?,
    mediaHeightPx: Float?,
    brushEnabled: Boolean,
    isActiveLayer: Boolean,
    onConfigUpdate: (WatermarkConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var currentPoints by remember(config.removalStrokes.size) { mutableStateOf<List<RemovalStrokePoint>>(emptyList()) }

    BoxWithConstraints(modifier = modifier) {
        val canvasWidthPx = with(density) { maxWidth.toPx() }
        val canvasHeightPx = with(density) { maxHeight.toPx() }
        val contentSourceWidth =
            previewBitmap?.width?.toFloat()
                ?: mediaWidthPx
                ?: return@BoxWithConstraints
        val contentSourceHeight =
            previewBitmap?.height?.toFloat()
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
        val gestureModifier =
            if (canPaint) {
                Modifier.pointerInput(contentRect, config.brushRadiusPct) {
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
            } else {
                Modifier
            }

        Canvas(modifier = Modifier.fillMaxSize().then(gestureModifier)) {
            val strokeColor =
                Color(0xFF10B981).copy(
                    alpha = when {
                        !brushEnabled -> 0.2f
                        isActiveLayer -> 0.72f
                        else -> 0.36f
                    },
                )
            config.removalStrokes.forEach { stroke ->
                drawStroke(stroke.points, stroke.radiusPct, contentRect, strokeColor)
            }
            if (canPaint) {
                drawStroke(
                    currentPoints,
                    config.brushRadiusPct,
                    contentRect,
                    Color(0xFF34D399).copy(alpha = 0.86f),
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
    color: Color,
) {
    if (points.isEmpty()) return
    val strokeWidth = (contentRect.width * radiusPct / 100f * 2f).coerceAtLeast(4f)
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
