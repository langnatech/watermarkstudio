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
import androidx.compose.runtime.remember
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
import com.watermarkstudio.util.RemovalRegion
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

        val overlaySize =
            remember(config.type, config.text, config.scale, canvasW, canvasH) {
                when (config.type) {
                    WatermarkType.TEXT -> {
                        val fontSize = (14f * config.scale).sp
                        val result =
                            textMeasurer.measure(
                                text = config.text.ifBlank { " " },
                                style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Medium),
                                constraints =
                                    Constraints(
                                        maxWidth =
                                            with(density) {
                                                canvasW.roundToPx()
                                            },
                                    ),
                            )
                        with(density) {
                            result.size.width.toDp().coerceAtLeast(24.dp) to
                                result.size.height.toDp().coerceAtLeast(20.dp)
                        }
                    }
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

        val overlayW = overlaySize.first
        val overlayH = overlaySize.second

        val position =
            remember(config.x, config.y, config.type, overlayW, overlayH, canvasW, canvasH) {
                when (config.type) {
                    WatermarkType.REMOVE -> {
                        val cx = canvasW * (config.x / 100f)
                        val cy = canvasH * (config.y / 100f)
                        (cx - overlayW / 2) to (cy - overlayH / 2)
                    }
                    WatermarkType.IMAGE -> {
                        val rangeX = (canvasW - overlayW).coerceAtLeast(1.dp)
                        val rangeY = (canvasH - overlayH).coerceAtLeast(1.dp)
                        rangeX * (config.x / 100f) to rangeY * (config.y / 100f)
                    }
                    WatermarkType.TEXT -> {
                        val cx = canvasW * (config.x / 100f)
                        val cy = canvasH * (config.y / 100f)
                        (cx - overlayW / 4) to (cy - overlayH / 2)
                    }
                }
            }

        val clampedOffsetX = position.first.coerceIn(0.dp, (canvasW - overlayW).coerceAtLeast(0.dp))
        val clampedOffsetY = position.second.coerceIn(0.dp, (canvasH - overlayH).coerceAtLeast(0.dp))

        val dragModifier =
            if (isActiveLayer) {
                Modifier.pointerInput(config.type, canvasW, canvasH, overlayW, overlayH) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val dxPx = dragAmount.x
                        val dyPx = dragAmount.y
                        val newConfig =
                            when (config.type) {
                                WatermarkType.REMOVE -> {
                                    val centerX = size.width * (config.x / 100f) + dxPx
                                    val centerY = size.height * (config.y / 100f) + dyPx
                                    config.copy(
                                        x = (centerX / size.width.toFloat() * 100f).coerceIn(0f, 100f),
                                        y = (centerY / size.height.toFloat() * 100f).coerceIn(0f, 100f),
                                    )
                                }
                                WatermarkType.IMAGE -> {
                                    val rangeX = (size.width - overlayW.toPx()).coerceAtLeast(1f)
                                    val rangeY = (size.height - overlayH.toPx()).coerceAtLeast(1f)
                                    config.copy(
                                        x = (config.x + dxPx / rangeX * 100f).coerceIn(0f, 100f),
                                        y = (config.y + dyPx / rangeY * 100f).coerceIn(0f, 100f),
                                    )
                                }
                                WatermarkType.TEXT -> {
                                    config.copy(
                                        x = (config.x + dxPx / size.width.toFloat() * 100f).coerceIn(0f, 100f),
                                        y = (config.y + dyPx / size.height.toFloat() * 100f).coerceIn(0f, 100f),
                                    )
                                }
                            }
                        onConfigUpdate(newConfig)
                    }
                }
            } else {
                Modifier
            }

        Box(
            modifier = Modifier
                .offset { IntOffset(clampedOffsetX.roundToPx(), clampedOffsetY.roundToPx()) }
                .size(overlayW, overlayH)
                .alpha(if (isActiveLayer) 1f else 0.45f)
                .then(dragModifier)
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
}
