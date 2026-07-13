package com.watermarkstudio.removal.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.InpaintTarget
import com.watermarkstudio.removal.OpenCvBootstrap
import com.watermarkstudio.removal.PatchMatchInpainter
import com.watermarkstudio.removal.PreviewScaleContext
import com.watermarkstudio.removal.RemovalExportLimits
import com.watermarkstudio.removal.RemovalQuality
import com.watermarkstudio.removal.mask.MaskGenerator
import com.watermarkstudio.removal.video.VideoFrameExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Inpaint preview for the remove-region overlay (images + video first frame).
 * Uses the same [InpaintTarget] and scaled tuning thresholds as export.
 */
object RemovalPreviewHelper {

    const val PREVIEW_INPAINT_DEBOUNCE_MS = 400L

    /** Undecorated preview-sized frame for display + smart-select color sampling. */
    suspend fun loadBasePreview(
        context: Context,
        uri: Uri,
        isVideo: Boolean,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (isVideo) {
            VideoFrameExtractor.loadPreviewFrame(
                context,
                uri,
                RemovalExportLimits.PREVIEW_MAX_DIM,
            )
        } else {
            decodeImagePreview(context, uri)
        }
    }

    suspend fun renderPreview(
        context: Context,
        uri: Uri,
        config: WatermarkConfig,
        isPremium: Boolean,
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (!OpenCvBootstrap.ensureLoaded(context)) return@withContext null
        val exportMaxDim = RemovalExportLimits.imageExportMaxDim(isPremium)
        val bitmap = decodeImagePreview(context, uri) ?: return@withContext null
        renderOnBitmap(
            context = context,
            bitmap = bitmap,
            config = config,
            isPremium = isPremium,
            target = InpaintTarget.IMAGE,
            exportMaxDim = exportMaxDim,
        )
    }

    /** Video: decode one frame then run the same inpaint preview as images. */
    suspend fun renderVideoPreview(
        context: Context,
        uri: Uri,
        config: WatermarkConfig,
        isPremium: Boolean,
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (!OpenCvBootstrap.ensureLoaded(context)) return@withContext null
        val exportMaxDim =
            if (isPremium) RemovalExportLimits.PREMIUM_VIDEO_MAX_DIM
            else RemovalExportLimits.FREE_VIDEO_MAX_DIM
        val frame =
            VideoFrameExtractor.loadPreviewFrame(
                context,
                uri,
                RemovalExportLimits.PREVIEW_MAX_DIM,
            ) ?: return@withContext null
        try {
            renderOnBitmap(
                context = context,
                bitmap = frame,
                config = config,
                isPremium = isPremium,
                target = InpaintTarget.VIDEO,
                exportMaxDim = exportMaxDim,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            frame.recycle()
            null
        }
    }

    /**
     * @param recycleInput when true (default), [bitmap] is recycled if a different output is returned.
     */
    internal fun renderOnBitmap(
        context: Context? = null,
        bitmap: Bitmap,
        config: WatermarkConfig,
        isPremium: Boolean,
        target: InpaintTarget,
        exportMaxDim: Int,
        recycleInput: Boolean = true,
    ): Bitmap? {
        if (config.removalStrokes.isEmpty()) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val quality = if (isPremium) RemovalQuality.ADVANCED else RemovalQuality.STANDARD
        val previewScale = PreviewScaleContext.forBitmap(exportMaxDim, bitmap.width, bitmap.height)
        val mask = MaskGenerator.createMaskMat(bitmap.width, bitmap.height, config)
        val region = MaskGenerator.regionForConfig(bitmap.width, bitmap.height, config)
        var result: Bitmap? = null
        try {
            val out =
                PatchMatchInpainter.inpaint(
                    bitmap,
                    mask,
                    region,
                    quality,
                    target,
                    previewScale,
                    context = context,
                )
            if (recycleInput && bitmap !== out) bitmap.recycle()
            result = out
        } finally {
            mask.release()
        }
        return result
    }

    private fun decodeImagePreview(context: Context, uri: Uri): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        var sample = 1
        while (
            options.outWidth / sample > RemovalExportLimits.PREVIEW_MAX_DIM ||
            options.outHeight / sample > RemovalExportLimits.PREVIEW_MAX_DIM
        ) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        }
    }
}
