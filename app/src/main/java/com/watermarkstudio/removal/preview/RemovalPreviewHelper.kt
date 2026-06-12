package com.watermarkstudio.removal.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.OpenCvBootstrap
import com.watermarkstudio.removal.PatchMatchInpainter
import com.watermarkstudio.removal.RemovalQuality
import com.watermarkstudio.removal.mask.MaskGenerator
import com.watermarkstudio.removal.video.VideoFrameExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Low-resolution inpaint preview for the remove-region overlay (images + video first frame).
 */
object RemovalPreviewHelper {

    private const val PREVIEW_MAX_DIM = 480
    const val PREVIEW_INPAINT_DEBOUNCE_MS = 400L

    suspend fun renderPreview(
        context: Context,
        uri: Uri,
        config: WatermarkConfig,
        isPremium: Boolean,
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (!OpenCvBootstrap.ensureLoaded(context)) return@withContext null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        var sample = 1
        while (
            options.outWidth / sample > PREVIEW_MAX_DIM ||
            options.outHeight / sample > PREVIEW_MAX_DIM
        ) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap =
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return@withContext null
        renderOnBitmap(bitmap, config, isPremium)
    }

    /** Video: decode one frame then run the same inpaint preview as images. */
    suspend fun renderVideoPreview(
        context: Context,
        uri: Uri,
        config: WatermarkConfig,
        isPremium: Boolean,
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (!OpenCvBootstrap.ensureLoaded(context)) return@withContext null
        val frame = VideoFrameExtractor.loadPreviewFrame(context, uri, PREVIEW_MAX_DIM) ?: return@withContext null
        try {
            renderOnBitmap(frame, config, isPremium)
        } catch (e: Exception) {
            e.printStackTrace()
            frame.recycle()
            null
        }
    }

    private fun renderOnBitmap(
        bitmap: Bitmap,
        config: WatermarkConfig,
        isPremium: Boolean,
    ): Bitmap? {
        if (config.removalStrokes.isEmpty()) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val quality = if (isPremium) RemovalQuality.ADVANCED else RemovalQuality.STANDARD
        val mask = MaskGenerator.createMaskMat(bitmap.width, bitmap.height, config)
        val region = MaskGenerator.regionForConfig(bitmap.width, bitmap.height, config)
        var result: Bitmap? = null
        try {
            val out = PatchMatchInpainter.inpaint(bitmap, mask, region, quality)
            if (bitmap !== out) bitmap.recycle()
            result = out
        } finally {
            mask.release()
        }
        return result
    }
}
