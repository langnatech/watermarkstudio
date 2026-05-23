package com.watermarkstudio.removal.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.OpenCvBootstrap
import com.watermarkstudio.removal.RemovalQuality
import com.watermarkstudio.removal.SeamlessBlendHelper
import com.watermarkstudio.removal.mask.MaskGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo

object ImageRemovalEngine {

    private const val INPAINT_RADIUS = 5.0

    suspend fun removeRegion(
        context: Context,
        uri: Uri,
        config: WatermarkConfig,
        maxDimension: Int,
        quality: RemovalQuality = RemovalQuality.STANDARD,
    ): Bitmap? = withContext(Dispatchers.Default) {
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

        val src = Mat()
        val dst = Mat()
        val mask = MaskGenerator.createMaskMat(bitmap.width, bitmap.height, config)
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
                        MaskGenerator.createFeatheredMaskMat(bitmap.width, bitmap.height, config)
                    try {
                        Photo.inpaint(src, mask, tmp, INPAINT_RADIUS, Photo.INPAINT_NS)
                        SeamlessBlendHelper.seamlessCloneInpaint(
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
            bitmap.recycle()
            out
        } finally {
            src.release()
            dst.release()
            mask.release()
        }
    }
}
