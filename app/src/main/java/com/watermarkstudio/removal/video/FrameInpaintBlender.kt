package com.watermarkstudio.removal.video

import android.graphics.Bitmap
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.RemovalQuality
import com.watermarkstudio.removal.SeamlessBlendHelper
import com.watermarkstudio.removal.mask.MaskGenerator
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.photo.Photo

object FrameInpaintBlender {

    private const val INPAINT_RADIUS = 5.0

    fun blendFrame(
        frame: Bitmap,
        config: WatermarkConfig,
        quality: RemovalQuality,
    ): Bitmap {
        if (quality == RemovalQuality.STANDARD) {
            return blendTelea(frame, config)
        }
        val src = Mat()
        Utils.bitmapToMat(frame, src)
        val mask = MaskGenerator.createMaskMat(frame.width, frame.height, config)
        val tmp = Mat()
        Photo.inpaint(src, mask, tmp, INPAINT_RADIUS, Photo.INPAINT_NS)
        val feather = MaskGenerator.createFeatheredMaskMat(frame.width, frame.height, config)
        val dst = Mat()
        SeamlessBlendHelper.seamlessCloneInpaint(src, tmp, feather, frame.width, frame.height, config, dst)
        val out = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dst, out)
        src.release()
        mask.release()
        tmp.release()
        feather.release()
        dst.release()
        if (out != frame) frame.recycle()
        return out
    }

    /** Per-frame TELEA inpaint (STANDARD video — matches image removal). */
    fun blendTelea(frame: Bitmap, config: WatermarkConfig): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(frame, src)
        val mask = MaskGenerator.createMaskMat(frame.width, frame.height, config)
        val dst = Mat()
        Photo.inpaint(src, mask, dst, INPAINT_RADIUS, Photo.INPAINT_TELEA)
        val out = Bitmap.createBitmap(dst.cols(), dst.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dst, out)
        src.release()
        mask.release()
        dst.release()
        if (out != frame) frame.recycle()
        return out
    }
}
