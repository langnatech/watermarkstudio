package com.watermarkstudio.removal

import android.content.Context
import android.net.Uri
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
                com.watermarkstudio.util.MediaStoreSaveHelper
                    .saveJpegBitmap(
                        context,
                        bitmap,
                        "wm_remove_${System.currentTimeMillis()}.jpg",
                    ).also {
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

}
