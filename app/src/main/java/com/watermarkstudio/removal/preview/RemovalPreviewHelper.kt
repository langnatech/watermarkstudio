package com.watermarkstudio.removal.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.OpenCvBootstrap
import com.watermarkstudio.removal.RemovalQuality
import com.watermarkstudio.removal.image.ImageRemovalEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Low-resolution inpaint preview for the remove-region overlay (phase 3b).
 */
object RemovalPreviewHelper {

    private const val PREVIEW_MAX_DIM = 480

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

        val quality = if (isPremium) RemovalQuality.ADVANCED else RemovalQuality.STANDARD
        val src = org.opencv.core.Mat()
        val dst = org.opencv.core.Mat()
        val mask = com.watermarkstudio.removal.mask.MaskGenerator.createMaskMat(bitmap.width, bitmap.height, config)
        try {
            org.opencv.android.Utils.bitmapToMat(bitmap, src)
            if (src.channels() == 4) {
                org.opencv.imgproc.Imgproc.cvtColor(src, src, org.opencv.imgproc.Imgproc.COLOR_RGBA2BGR)
            }
            when (quality) {
                RemovalQuality.STANDARD -> {
                    org.opencv.photo.Photo.inpaint(src, mask, dst, 5.0, org.opencv.photo.Photo.INPAINT_TELEA)
                }
                RemovalQuality.ADVANCED -> {
                    val tmp = org.opencv.core.Mat()
                    val feather =
                        com.watermarkstudio.removal.mask.MaskGenerator.createFeatheredMaskMat(
                            bitmap.width,
                            bitmap.height,
                            config,
                        )
                    try {
                        org.opencv.photo.Photo.inpaint(src, mask, tmp, 5.0, org.opencv.photo.Photo.INPAINT_NS)
                        com.watermarkstudio.removal.SeamlessBlendHelper.seamlessCloneInpaint(
                            src,
                            tmp,
                            feather,
                            bitmap.width,
                            bitmap.height,
                            config,
                            dst,
                        )
                    } finally {
                        tmp.release()
                        feather.release()
                    }
                }
            }
            if (dst.channels() == 3) {
                org.opencv.imgproc.Imgproc.cvtColor(dst, dst, org.opencv.imgproc.Imgproc.COLOR_BGR2RGBA)
            }
            val out = Bitmap.createBitmap(dst.cols(), dst.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(dst, out)
            bitmap.recycle()
            out
        } finally {
            src.release()
            dst.release()
            mask.release()
        }
    }
}
