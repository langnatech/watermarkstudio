package com.watermarkstudio.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.watermarkstudio.R
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType
import kotlin.math.roundToInt

@Composable
fun InteractiveWatermarkPreview(
    mediaUri: Uri,
    config: WatermarkConfig,
    previewBitmap: Bitmap? = null,
    showBackground: Boolean = true,
    isActiveLayer: Boolean = true,
    onConfigUpdate: (WatermarkConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val frameModifier =
        if (showBackground) {
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0B1220))
                .border(
                    1.dp,
                    if (isActiveLayer) Color(0xFF6366F1).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(16.dp),
                )
        } else {
            modifier.fillMaxSize()
        }

    BoxWithConstraints(modifier = frameModifier) {
        val canvasW = maxWidth
        val canvasH = maxHeight
        val canvasWidthPx = with(density) { canvasW.roundToPx().toFloat() }
        val canvasHeightPx = with(density) { canvasH.roundToPx().toFloat() }

        val textSizePx =
            remember(config.text, config.scale) {
                val result =
                    textMeasurer.measure(
                        text = config.text.ifBlank { " " },
                        style =
                            TextStyle(
                                fontSize = (14f * config.scale).sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        constraints =
                            Constraints(
                                maxWidth = with(density) { canvasW.roundToPx() },
                            ),
                    )
                result.size.width to result.size.height
            }

        val textOverlayW =
            with(density) {
                textSizePx.first.toDp().coerceAtLeast(24.dp)
            }
        val textOverlayH =
            with(density) {
                textSizePx.second.toDp().coerceAtLeast(20.dp)
            }
        val (overlayW, overlayH) =
            remember(config.type, config.scale, canvasW, canvasH, textOverlayW, textOverlayH) {
                WatermarkDragGeometry.overlaySizeDp(
                    config,
                    canvasW,
                    canvasH,
                    textOverlayW,
                    textOverlayH,
                )
            }
        val overlayWidthPx = with(density) { overlayW.roundToPx().toFloat() }
        val overlayHeightPx = with(density) { overlayH.roundToPx().toFloat() }

        var isDragging by remember { mutableStateOf(false) }
        var dragTopLeftPx by remember { mutableStateOf<WatermarkDragGeometry.PxOffset?>(null) }

        val configTopLeft =
            remember(config.x, config.y, config.type, config.scale, config.text, canvasW, canvasH) {
                WatermarkDragGeometry.topLeftPx(
                    config,
                    canvasWidthPx,
                    canvasHeightPx,
                    overlayWidthPx,
                    overlayHeightPx,
                )
            }

        LaunchedEffect(config.x, config.y, config.type, config.scale, config.text) {
            if (!isDragging) {
                dragTopLeftPx = null
            }
        }

        val displayTopLeft = dragTopLeftPx ?: configTopLeft

        Box(modifier = Modifier.fillMaxSize()) {
            if (showBackground) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    AsyncImage(
                        model = mediaUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            val overlayModifier =
                Modifier
                    .offset {
                        IntOffset(displayTopLeft.x.roundToInt(), displayTopLeft.y.roundToInt())
                    }
                    .size(overlayW, overlayH)
                    .alpha(if (isActiveLayer) 1f else 0.45f)

            val gestureModifier =
                if (isActiveLayer) {
                    Modifier.pointerInput(canvasWidthPx, canvasHeightPx, overlayWidthPx, overlayHeightPx) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragTopLeftPx = configTopLeft
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val base = dragTopLeftPx ?: configTopLeft
                                dragTopLeftPx =
                                    WatermarkDragGeometry.PxOffset(
                                        x = (base.x + dragAmount.x).coerceIn(
                                            0f,
                                            (canvasWidthPx - overlayWidthPx).coerceAtLeast(0f),
                                        ),
                                        y = (base.y + dragAmount.y).coerceIn(
                                            0f,
                                            (canvasHeightPx - overlayHeightPx).coerceAtLeast(0f),
                                        ),
                                    )
                            },
                            onDragEnd = {
                                isDragging = false
                                dragTopLeftPx?.let { topLeft ->
                                    onConfigUpdate(
                                        WatermarkDragGeometry.configFromTopLeftPx(
                                            config,
                                            topLeft,
                                            canvasWidthPx,
                                            canvasHeightPx,
                                            overlayWidthPx,
                                            overlayHeightPx,
                                        ),
                                    )
                                }
                                dragTopLeftPx = null
                            },
                            onDragCancel = {
                                isDragging = false
                                dragTopLeftPx = null
                            },
                        )
                    }
                } else {
                    Modifier
                }

            WatermarkOverlayChip(
                config = config,
                modifier = overlayModifier.then(gestureModifier),
                isActiveLayer = isActiveLayer,
            )
        }
    }
}

@Composable
private fun WatermarkOverlayChip(
    config: WatermarkConfig,
    modifier: Modifier,
    isActiveLayer: Boolean,
) {
    Box(
        modifier =
            modifier
                .border(
                    width = if (isActiveLayer) 2.dp else 1.dp,
                    color =
                        when (config.type) {
                            WatermarkType.REMOVE -> Color(0xFF10B981)
                            else -> Color(0xFF818CF8)
                        },
                    shape = RoundedCornerShape(8.dp),
                )
                .background(
                    when (config.type) {
                        WatermarkType.REMOVE -> Color(0xFF10B981).copy(alpha = 0.25f)
                        else -> Color.Black.copy(alpha = 0.35f)
                    },
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (config.type) {
            WatermarkType.TEXT ->
                Text(
                    text = config.text.ifBlank { stringResource(R.string.hint_enter_watermark) },
                    color = Color.White.copy(alpha = config.opacity),
                    fontSize = (14 * config.scale).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
            WatermarkType.IMAGE ->
                AsyncImage(
                    model = config.imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            WatermarkType.REMOVE ->
                Text(
                    text = stringResource(R.string.layer_type_remove),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
        }
    }
}

/** Overlay only (no background); use inside [PreviewContainer] with a shared image layer. */
@Composable
fun DraggableWatermarkOverlay(
    config: WatermarkConfig,
    isActiveLayer: Boolean,
    onConfigUpdate: (WatermarkConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    InteractiveWatermarkPreview(
        mediaUri = Uri.EMPTY,
        config = config,
        showBackground = false,
        isActiveLayer = isActiveLayer,
        onConfigUpdate = onConfigUpdate,
        modifier = modifier,
    )
}
