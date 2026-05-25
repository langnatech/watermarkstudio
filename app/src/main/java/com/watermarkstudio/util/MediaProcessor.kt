package com.watermarkstudio.util

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.media.MediaCodecList
import android.media.MediaFormat
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.*
import androidx.media3.transformer.*
import com.watermarkstudio.model.MediaType
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

@UnstableApi
object MediaProcessor {

    suspend fun processImage(
        context: Context,
        uri: Uri,
        configs: List<WatermarkConfig>,
        isPremium: Boolean = false
    ): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                // Decode with sample size if necessary to avoid OOM
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use { 
                    BitmapFactory.decodeStream(it, null, options)
                }

                // Aim for roughly 2500px max dimension for Premium and 1024px for Free to differentiate benefits & save memory
                val maxDim = if (isPremium) 2500 else 1024
                var inSampleSize = 1
                if (options.outHeight > maxDim || options.outWidth > maxDim) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while (halfHeight / inSampleSize >= maxDim && halfWidth / inSampleSize >= maxDim) {
                        inSampleSize *= 2
                    }
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inMutable = true
                }

                val rawBitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                } ?: return@withContext null

                val bitmap =
                    try {
                        if (rawBitmap.isMutable) {
                            rawBitmap
                        } else {
                            val copied = rawBitmap.copy(Bitmap.Config.ARGB_8888, true)
                            copied
                        }
                    } finally {
                        if (!rawBitmap.isMutable) {
                            rawBitmap.recycle()
                        }
                    } ?: return@withContext null

                val canvas = Canvas(bitmap)
                
                configs.forEach { config ->
                    when (config.type) {
                        WatermarkType.TEXT -> {
                            TextWatermarkRenderer.drawOnCanvas(
                                canvas,
                                config,
                                context,
                                bitmap.width,
                                bitmap.height,
                            )
                        }
                        WatermarkType.IMAGE -> {
                            config.imageUri?.let { imgUri ->
                                context.contentResolver.openInputStream(imgUri)?.use { 
                                    val watermarkBitmap = BitmapFactory.decodeStream(it) ?: return@use
                                    val scaledWidth = (bitmap.width * 0.2f * config.scale).toInt()
                                    val scaledHeight = (watermarkBitmap.height * (scaledWidth.toFloat() / watermarkBitmap.width)).toInt()
                                    val scaledWatermark = Bitmap.createScaledBitmap(watermarkBitmap, scaledWidth, scaledHeight, true)
                                    
                                    val paint = Paint().apply {
                                        alpha = (config.opacity * 255).toInt()
                                    }
                                    val xPos = (bitmap.width - scaledWidth) * (config.x / 100f)
                                    val yPos = (bitmap.height - scaledHeight) * (config.y / 100f)
                                    canvas.drawBitmap(scaledWatermark, xPos, yPos, paint)
                                    
                                    watermarkBitmap.recycle()
                                    scaledWatermark.recycle()
                                }
                            }
                        }
                        WatermarkType.REMOVE -> {
                            // Handled by RemovalPipeline when remove-only session
                        }
                    }
                }

                val savedUri =
                    MediaStoreSaveHelper.saveJpegBitmap(
                        context,
                        bitmap,
                        "wm_${System.currentTimeMillis()}.jpg",
                    )
                bitmap.recycle()
                savedUri
            } catch (t: Throwable) {
                t.printStackTrace()
                null
            }
        }
    }

    suspend fun processVideo(
        context: Context,
        uri: Uri,
        configs: List<WatermarkConfig>,
        maxDurationMs: Long = 0L,
        isPremium: Boolean = false
    ): Uri? {
        val activeMaxDurationMs =
            com.watermarkstudio.removal.video.VideoRemovalLimits.clampClipDurationMs(maxDurationMs, isPremium)

        val editRequestWithOutputPath = withContext(Dispatchers.IO) {
            val overlays = mutableListOf<TextureOverlay>()
            val bitmapsToRecycle = mutableListOf<Bitmap>()
            var videoWidthPx = 1080
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                videoWidthPx =
                    retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 }
                        ?: 1080
                retriever.release()
            } catch (_: Exception) {
                // keep default reference width
            }
            configs.forEach { config ->
                when (config.type) {
                    WatermarkType.TEXT -> {
                        val textBitmap =
                            TextWatermarkRenderer.renderTextBitmap(context, config, videoWidthPx)
                                ?: return@forEach

                            // Top-left anchor: same semantics as [TextWatermarkRenderer.drawOnCanvas] (x/y % of frame).
                            val bgX = (config.x / 100f) * 2f - 1f
                            val bgY = 1f - (config.y / 100f) * 2f

                            val textOverlay = BitmapOverlay.createStaticBitmapOverlay(
                                textBitmap,
                                OverlaySettings.Builder()
                                    .setAlphaScale(1f)
                                    .setScale(1f, 1f)
                                    .setOverlayFrameAnchor(-1f, 1f)
                                    .setBackgroundFrameAnchor(bgX, bgY)
                                    .build()
                            )
                            overlays.add(textOverlay)
                            bitmapsToRecycle.add(textBitmap)
                    }
                    WatermarkType.IMAGE -> {
                        config.imageUri?.let { imgUri ->
                            val bitmap = context.contentResolver.openInputStream(imgUri)?.use {
                                BitmapFactory.decodeStream(it)
                            } ?: return@let

                            val xPos = (config.x / 100f) * 2f - 1f
                            val yPos = 1f - (config.y / 100f) * 2f

                            val bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(
                                bitmap,
                                OverlaySettings.Builder()
                                    .setAlphaScale(config.opacity)
                                    .setScale(config.scale, config.scale)
                                    .setOverlayFrameAnchor(0f, 0f)
                                    .setBackgroundFrameAnchor(xPos, yPos)
                                    .build()
                            )
                            overlays.add(bitmapOverlay)
                            bitmapsToRecycle.add(bitmap)
                        }
                    }
                    WatermarkType.REMOVE -> {
                        // Video removal uses RemovalPipeline (temporal median + re-encode)
                    }
                    else -> {}
                }
            }

            val overlayEffect = OverlayEffect(com.google.common.collect.ImmutableList.copyOf(overlays))
            val videoEffects = mutableListOf<Effect>()
            videoEffects.add(overlayEffect)

            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val heightStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val widthStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val height = heightStr?.toInt() ?: 0
                val width = widthStr?.toInt() ?: 0
                retriever.release()

                // Resolution bounds: PRO up to 1080p, Free strictly up to 720p (saves batch rendering memory)
                val targetCeiling = if (isPremium) 1080 else 720
                if (height > targetCeiling || width > targetCeiling) {
                    videoEffects.add(Presentation.createForHeight(targetCeiling))
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            val mediaItemBuilder = MediaItem.fromUri(uri).buildUpon()
            if (activeMaxDurationMs > 0L) {
                mediaItemBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setEndPositionMs(activeMaxDurationMs)
                        .build()
                )
            }

            val editRequest = EditedMediaItem.Builder(mediaItemBuilder.build())
                .setEffects(Effects(listOf(), videoEffects))
                .build()

            val tempFile = File(context.cacheDir, "temp_wm_${System.currentTimeMillis()}.mp4")
            val outputPath = tempFile.absolutePath
            Triple(editRequest, tempFile, bitmapsToRecycle)
        }

    val editRequest = editRequestWithOutputPath.first
    val tempFile = editRequestWithOutputPath.second
    val bitmapsToRecycle = editRequestWithOutputPath.third
    val outputPath = tempFile.absolutePath

    if (isEmulator()) {
        bitmapsToRecycle.forEach { it.recycle() }
        throw IllegalStateException("Video watermarking is disabled on emulators because virtual environments lack the hardware GPU drivers required by Media3. Please test with images!")
    }

    if (!hasH264Encoder()) {
        bitmapsToRecycle.forEach { it.recycle() }
        throw IllegalStateException("No compatible H.264/AVC hardware video encoder found on this device/emulator. Video processing is disabled.")
    }

    val deferred = kotlinx.coroutines.CompletableDeferred<Unit>()
    try {
        withContext(Dispatchers.Main) {
            try {
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setEnableFallback(true)
                    .build()

                val transformer = Transformer.Builder(context)
                    .setEncoderFactory(encoderFactory)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .build()

                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        deferred.complete(Unit)
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        deferred.completeExceptionally(exportException)
                    }
                }
                transformer.addListener(listener)
                transformer.start(editRequest, outputPath)
            } catch (t: Throwable) {
                deferred.completeExceptionally(t)
            }
        }
        deferred.await()
    } finally {
        bitmapsToRecycle.forEach { it.recycle() }
    }

    return withContext(Dispatchers.IO) {
        try {
            MediaStoreSaveHelper.saveMp4FromFile(
                context,
                tempFile,
                "wm_${System.currentTimeMillis()}.mp4",
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            tempFile.delete()
        }
    }
    }

    private fun isEmulator(): Boolean {
        val brand = Build.BRAND ?: ""
        val device = Build.DEVICE ?: ""
        val model = Build.MODEL ?: ""
        val hardware = Build.HARDWARE ?: ""
        val product = Build.PRODUCT ?: ""
        val fingerprint = Build.FINGERPRINT ?: ""
        return brand.startsWith("generic", ignoreCase = true) ||
                device.startsWith("generic", ignoreCase = true) ||
                model.contains("google_sdk", ignoreCase = true) ||
                model.contains("Emulator", ignoreCase = true) ||
                model.contains("Android SDK built for x86", ignoreCase = true) ||
                hardware.contains("goldfish", ignoreCase = true) ||
                hardware.contains("ranchu", ignoreCase = true) ||
                product.contains("sdk", ignoreCase = true) ||
                product.contains("google_sdk", ignoreCase = true) ||
                product.contains("sdk_x86", ignoreCase = true) ||
                fingerprint.startsWith("generic", ignoreCase = true)
    }

    private fun hasH264Encoder(): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            codecList.codecInfos.any { info ->
                info.isEncoder && info.supportedTypes.any { type ->
                    type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)
                }
            }
        } catch (t: Throwable) {
            false
        }
    }
}
