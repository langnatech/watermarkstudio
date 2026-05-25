package com.watermarkstudio.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.watermarkstudio.R
import com.watermarkstudio.model.MediaType
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.removal.video.VideoFrameExtractor
import kotlin.math.roundToInt

@Composable
fun InteractiveWatermarkPreview(
    mediaUri: Uri,
    config: WatermarkConfig,
    mediaType: MediaType? = null,
    previewBitmap: Bitmap? = null,
    showBackground: Boolean = true,
    isActiveLayer: Boolean = true,
    onConfigUpdate: (WatermarkConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    var mediaWidthPx by remember(mediaUri, previewBitmap) {
        mutableStateOf(previewBitmap?.width?.toFloat())
    }
    var mediaHeightPx by remember(mediaUri, previewBitmap) {
        mutableStateOf(previewBitmap?.height?.toFloat())
    }
    var videoFrameBitmap by remember(mediaUri, mediaType) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(mediaUri, previewBitmap, mediaType) {
        if (previewBitmap != null) {
            mediaWidthPx = previewBitmap.width.toFloat()
            mediaHeightPx = previewBitmap.height.toFloat()
            return@LaunchedEffect
        }
        if (mediaUri == Uri.EMPTY) {
            mediaWidthPx = null
            mediaHeightPx = null
            videoFrameBitmap?.recycle()
            videoFrameBitmap = null
            return@LaunchedEffect
        }
        val loaded =
            withContext(Dispatchers.IO) {
                if (mediaType == MediaType.VIDEO) {
                    val dims = VideoFrameExtractor.loadVideoDimensions(context, mediaUri)
                    val frame = VideoFrameExtractor.loadPreviewFrame(context, mediaUri)
                    Triple(dims?.first?.toFloat(), dims?.second?.toFloat(), frame)
                } else {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    try {
                        context.contentResolver.openInputStream(mediaUri)?.use {
                            BitmapFactory.decodeStream(it, null, opts)
                        }
                    } catch (_: Exception) {
                    }
                    val w = if (opts.outWidth > 0) opts.outWidth.toFloat() else null
                    val h = if (opts.outHeight > 0) opts.outHeight.toFloat() else null
                    Triple(w, h, null as Bitmap?)
                }
            }
        videoFrameBitmap?.recycle()
        videoFrameBitmap = loaded.third
        if (loaded.first != null && loaded.second != null) {
            mediaWidthPx = loaded.first
            mediaHeightPx = loaded.second
        } else if (loaded.third != null) {
            mediaWidthPx = loaded.third!!.width.toFloat()
            mediaHeightPx = loaded.third!!.height.toFloat()
        }
    }

    DisposableEffect(mediaUri) {
        onDispose {
            videoFrameBitmap?.recycle()
            videoFrameBitmap = null
        }
    }

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

        val contentRect =
            remember(canvasWidthPx, canvasHeightPx, mediaWidthPx, mediaHeightPx) {
                val cw = mediaWidthPx ?: canvasWidthPx
                val ch = mediaHeightPx ?: canvasHeightPx
                WatermarkContentGeometry.fittedContentRect(canvasWidthPx, canvasHeightPx, cw, ch)
            }
        val contentWidthDp = with(density) { contentRect.width.toDp() }
        val contentHeightDp = with(density) { contentRect.height.toDp() }

        val hint = stringResource(R.string.hint_enter_watermark)
        val previewFontSize = config.previewFontSizeSp(contentRect.width)
        val textSizePx =
            remember(config.text, previewFontSize, config.fontFamily, config.color, config.opacity) {
                val result =
                    textMeasurer.measure(
                        text = config.watermarkDisplayText(hint),
                        style =
                            androidx.compose.ui.text.TextStyle(
                                fontSize = previewFontSize,
                            ),
                        constraints =
                            Constraints(
                                maxWidth = contentRect.width.roundToInt().coerceAtLeast(1),
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
            remember(config.type, config.scale, canvasW, canvasH, textOverlayW, textOverlayH, contentWidthDp, contentHeightDp) {
                WatermarkDragGeometry.overlaySizeDp(
                    config,
                    canvasW,
                    canvasH,
                    textOverlayW,
                    textOverlayH,
                    contentWidthDp,
                    contentHeightDp,
                )
            }
        val overlayWidthPx = with(density) { overlayW.roundToPx().toFloat() }
        val overlayHeightPx = with(density) { overlayH.roundToPx().toFloat() }

        val dragMinX = contentRect.left
        val dragMinY = contentRect.top
        val dragMaxX = (contentRect.right - overlayWidthPx).coerceAtLeast(dragMinX)
        val dragMaxY = (contentRect.bottom - overlayHeightPx).coerceAtLeast(dragMinY)

        var isDragging by remember { mutableStateOf(false) }
        var dragTopLeftPx by remember { mutableStateOf<WatermarkDragGeometry.PxOffset?>(null) }

        val configTopLeft =
            remember(config.x, config.y, config.type, config.scale, config.text, contentRect) {
                WatermarkDragGeometry.topLeftPx(
                    config,
                    canvasWidthPx,
                    canvasHeightPx,
                    overlayWidthPx,
                    overlayHeightPx,
                    contentRect,
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
                when {
                    previewBitmap != null -> {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    videoFrameBitmap != null -> {
                        Image(
                            bitmap = videoFrameBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    mediaType != MediaType.VIDEO -> {
                        AsyncImage(
                            model = mediaUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
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
                    Modifier.pointerInput(contentRect, overlayWidthPx, overlayHeightPx) {
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
                                        x = (base.x + dragAmount.x).coerceIn(dragMinX, dragMaxX),
                                        y = (base.y + dragAmount.y).coerceIn(dragMinY, dragMaxY),
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
                                            contentRect,
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
                previewFontSize = previewFontSize,
                modifier = overlayModifier.then(gestureModifier),
                isActiveLayer = isActiveLayer,
            )
        }
    }
}

@Composable
private fun WatermarkOverlayChip(
    config: WatermarkConfig,
    previewFontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier,
    isActiveLayer: Boolean,
) {
    val chipBackground =
        when (config.type) {
            WatermarkType.REMOVE -> Color(0xFF10B981).copy(alpha = 0.25f)
            WatermarkType.TEXT -> Color.Transparent
            else -> Color.Black.copy(alpha = 0.2f)
        }
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
                .background(chipBackground, RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (config.type) {
            WatermarkType.TEXT ->
                WatermarkOutlinedText(
                    text = config.watermarkDisplayText(stringResource(R.string.hint_enter_watermark)),
                    config = config,
                    fontSize = previewFontSize,
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
    mediaUri: Uri,
    mediaType: MediaType,
    config: WatermarkConfig,
    isActiveLayer: Boolean,
    onConfigUpdate: (WatermarkConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    InteractiveWatermarkPreview(
        mediaUri = mediaUri,
        mediaType = mediaType,
        config = config,
        showBackground = false,
        isActiveLayer = isActiveLayer,
        onConfigUpdate = onConfigUpdate,
        modifier = modifier,
    )
}
