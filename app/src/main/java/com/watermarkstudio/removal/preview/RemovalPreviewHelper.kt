package com.watermarkstudio.removal.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.OpenCvBootstrap
import com.watermarkstudio.removal.RemovalQuality
import com.watermarkstudio.removal.video.VideoFrameExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo

/**
 * Low-resolution inpaint preview for the remove-region overlay (images + video first frame).
 */
object RemovalPreviewHelper {

    private const val PREVIEW_MAX_DIM = 480
    private const val INPAINT_RADIUS = 5.0

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
        val quality = if (isPremium) RemovalQuality.ADVANCED else RemovalQuality.STANDARD
        val src = Mat()
        val dst = Mat()
        val mask = com.watermarkstudio.removal.mask.MaskGenerator.createMaskMat(bitmap.width, bitmap.height, config)
        var result: Bitmap? = null
        try {
            Utils.bitmapToMat(bitmap, src)
            if (src.channels() == 4) {
                Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2BGR)
            }
            when (quality) {
                RemovalQuality.STANDARD -> {
                    Photo.inpaint(src, mask, dst, INPAINT_RADIUS, Photo.INPAINT_TELEA)
                }
                RemovalQuality.ADVANCED -> {
                    val tmp = Mat()
                    val feather =
                        com.watermarkstudio.removal.mask.MaskGenerator.createFeatheredMaskMat(
                            bitmap.width,
                            bitmap.height,
                            config,
                        )
                    try {
                        Photo.inpaint(src, mask, tmp, INPAINT_RADIUS, Photo.INPAINT_NS)
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
                Imgproc.cvtColor(dst, dst, Imgproc.COLOR_BGR2RGBA)
            }
            val out = Bitmap.createBitmap(dst.cols(), dst.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, out)
            if (bitmap !== out) bitmap.recycle()
            result = out
        } finally {
            src.release()
            dst.release()
            mask.release()
        }
        return result
    }
}
