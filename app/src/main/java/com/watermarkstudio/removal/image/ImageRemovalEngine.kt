package com.watermarkstudio.removal.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.OpenCvBootstrap
import com.watermarkstudio.removal.PatchMatchInpainter
import com.watermarkstudio.removal.RemovalInputValidator
import com.watermarkstudio.removal.RemovalQuality
import com.watermarkstudio.removal.mask.MaskGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageRemovalEngine {

    suspend fun removeRegion(
        context: Context,
        uri: Uri,
        config: WatermarkConfig,
        maxDimension: Int,
        quality: RemovalQuality = RemovalQuality.STANDARD,
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (!RemovalInputValidator.hasPaintedMask(config)) return@withContext null
        if (!OpenCvBootstrap.ensureLoaded(context)) return@withContext null

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        var sample = 1
        while (options.outWidth / sample > maxDimension || options.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap =
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return@withContext null

        val mask = MaskGenerator.createMaskMat(bitmap.width, bitmap.height, config)
        val region = MaskGenerator.regionForConfig(bitmap.width, bitmap.height, config)
        try {
            val out = PatchMatchInpainter.inpaint(bitmap, mask, region, quality)
            bitmap.recycle()
            out
        } finally {
            mask.release()
        }
    }
}
