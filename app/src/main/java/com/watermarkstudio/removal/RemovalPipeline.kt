package com.watermarkstudio.removal

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.watermarkstudio.model.MediaItem
import com.watermarkstudio.model.MediaType
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.image.ImageRemovalEngine
import com.watermarkstudio.removal.video.VideoRemovalEngine
import com.watermarkstudio.removal.video.VideoRemovalLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RemovalPipeline {

    suspend fun processItem(
        context: Context,
        item: MediaItem,
        config: WatermarkConfig,
        maxDurationMs: Long,
        isPremium: Boolean,
        progress: RemovalProgress? = null,
    ): Uri? = withContext(Dispatchers.IO) {
        val quality = RemovalQualityResolver.resolve(isPremium, context)
        val maxDim = if (isPremium) 2500 else 1024
        when (item.type) {
            MediaType.IMAGE -> {
                val bitmap =
                    ImageRemovalEngine.removeRegion(context, item.uri, config, maxDim, quality)
                        ?: return@withContext null
                progress?.report(1f)
                saveBitmapToGallery(context, bitmap, "wm_remove_${System.currentTimeMillis()}.jpg").also {
                    bitmap.recycle()
                }
            }
            MediaType.VIDEO -> {
                if (!RemovalCapability.supportsVideoRemoval(context)) {
                    return@withContext null
                }
                VideoRemovalEngine.removeRegion(
                    context,
                    item.uri,
                    config,
                    VideoRemovalLimits.clampClipDurationMs(maxDurationMs, isPremium),
                    if (isPremium) 1080 else 720,
                    isPremium,
                    quality,
                    progress,
                )
            }
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, filename: String): Uri? {
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
        val uri = context.contentResolver.insert(collection, values) ?: return null
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
