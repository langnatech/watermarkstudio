package com.watermarkstudio.util

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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

                val bitmap = if (rawBitmap.isMutable) rawBitmap else {
                    val copied = rawBitmap.copy(Bitmap.Config.ARGB_8888, true)
                    rawBitmap.recycle()
                    copied
                } ?: return@withContext null

                val canvas = Canvas(bitmap)
                
                configs.forEach { config ->
                    when (config.type) {
                        WatermarkType.TEXT -> {
                            val paint = Paint().apply {
                                color = if (config.color != -1) config.color else Color.WHITE
                                alpha = (config.opacity * 255).toInt()
                                textSize = bitmap.width * 0.05f * config.scale
                                isAntiAlias = true
                                setShadowLayer(2f, 1f, 1f, Color.BLACK)
                            }
                            // Calculate position based on percentages (defaulting to bottom right area for now)
                            val xPos = bitmap.width * (config.x / 100f)
                            val yPos = bitmap.height * (config.y / 100f)
                            canvas.drawText(config.text, xPos, yPos, paint)
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

                val savedUri = saveBitmapToGallery(context, bitmap, "wm_${System.currentTimeMillis()}.jpg")
                bitmap.recycle()
                savedUri
            } catch (t: Throwable) {
                t.printStackTrace()
                null
            }
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, filename: String): Uri? {
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val imageUri = context.contentResolver.insert(imageCollection, contentValues)
        imageUri?.let { uri ->
            var success = false
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    success = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, if (success) 0 else 1)
                    context.contentResolver.update(uri, contentValues, null, null)
                }
            }
        }
        return imageUri
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
            configs.forEach { config ->
                when (config.type) {
                    WatermarkType.TEXT -> {
                        if (config.text.isNotEmpty()) {
                            val paint = Paint().apply {
                                color = Color.WHITE
                                textSize = 64f
                                isAntiAlias = true
                            }
                            val width = paint.measureText(config.text).toInt() + 20
                            val height = 80
                            val textBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(textBitmap)
                            canvas.drawText(config.text, 10f, 60f, paint)

                            val xPos = (config.x / 100f) * 2f - 1f
                            val yPos = 1f - (config.y / 100f) * 2f

                            val textOverlay = BitmapOverlay.createStaticBitmapOverlay(
                                textBitmap,
                                OverlaySettings.Builder()
                                    .setAlphaScale(config.opacity)
                                    .setScale(config.scale, config.scale)
                                    .setOverlayFrameAnchor(0f, 0f)
                                    .setBackgroundFrameAnchor(xPos, yPos)
                                    .build()
                            )
                            overlays.add(textOverlay)
                            bitmapsToRecycle.add(textBitmap)
                        }
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
            throw IllegalStateException("Video watermarking is disabled on emulators because virtual environments lack the hardware GPU drivers required by Media3. Please test with images!")
        }

        if (!hasH264Encoder()) {
            throw IllegalStateException("No compatible H.264/AVC hardware video encoder found on this device/emulator. Video processing is disabled.")
        }

        val deferred = kotlinx.coroutines.CompletableDeferred<Unit>()
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
        bitmapsToRecycle.forEach { it.recycle() }

        return withContext(Dispatchers.IO) {
            try {
                val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "wm_${System.currentTimeMillis()}.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

                val savedUri = context.contentResolver.insert(videoCollection, contentValues)
                savedUri?.let { destUri ->
                    context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                        tempFile.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                        context.contentResolver.update(destUri, contentValues, null, null)
                    }
                    tempFile.delete()
                    destUri
                }
            } catch (e: Exception) {
                e.printStackTrace()
                tempFile.delete()
                null
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
