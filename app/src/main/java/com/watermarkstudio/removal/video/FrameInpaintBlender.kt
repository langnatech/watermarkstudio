package com.watermarkstudio.removal.video

import android.graphics.Bitmap
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.RemovalQuality
import com.watermarkstudio.removal.SeamlessBlendHelper
import com.watermarkstudio.removal.mask.MaskGenerator
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
        val src = VideoFrameUtils.bitmapToBgrMat(frame)
        val mask = MaskGenerator.createMaskMat(frame.width, frame.height, config)
        val tmp = Mat()
        val dst = Mat()
        try {
            Photo.inpaint(src, mask, tmp, INPAINT_RADIUS, Photo.INPAINT_NS)
            val feather = MaskGenerator.createFeatheredMaskMat(frame.width, frame.height, config)
            try {
                SeamlessBlendHelper.seamlessCloneInpaint(
                    src,
                    tmp,
                    feather,
                    frame.width,
                    frame.height,
                    config,
                    dst,
                )
            } finally {
                feather.release()
            }
            val out = VideoFrameUtils.bgrMatToArgbBitmap(dst)
            return out
        } finally {
            src.release()
            mask.release()
            tmp.release()
            dst.release()
        }
    }

    /** Per-frame TELEA inpaint (STANDARD video — matches image removal). */
    fun blendTelea(frame: Bitmap, config: WatermarkConfig): Bitmap {
        val src = VideoFrameUtils.bitmapToBgrMat(frame)
        val mask = MaskGenerator.createMaskMat(frame.width, frame.height, config)
        val dst = Mat()
        try {
            Photo.inpaint(src, mask, dst, INPAINT_RADIUS, Photo.INPAINT_TELEA)
            val out = VideoFrameUtils.bgrMatToArgbBitmap(dst)
            return out
        } finally {
            src.release()
            mask.release()
            dst.release()
        }
    }
}
