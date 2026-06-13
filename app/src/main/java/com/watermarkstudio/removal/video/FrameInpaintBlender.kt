package com.watermarkstudio.removal.video

import android.graphics.Bitmap
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.InpaintTarget
import com.watermarkstudio.removal.PatchMatchInpainter
import com.watermarkstudio.removal.RemovalQuality
import com.watermarkstudio.removal.mask.MaskGenerator
import com.watermarkstudio.util.RemovalRegion
import org.opencv.core.Mat

object FrameInpaintBlender {

    class CachedMask internal constructor(
        val mask: Mat,
        val region: RemovalRegion,
    ) {
        fun release() {
            mask.release()
        }
    }

    fun prepareMask(width: Int, height: Int, config: WatermarkConfig): CachedMask {
        val mask = MaskGenerator.createMaskMat(width, height, config)
        val region = MaskGenerator.regionForConfig(width, height, config)
        return CachedMask(mask, region)
    }

    /** PatchMatch refine after temporal prefill (video export path). */
    fun refineFrame(
        frame: Bitmap,
        quality: RemovalQuality,
        cachedMask: CachedMask,
    ): Bitmap =
        PatchMatchInpainter.inpaint(
            frame,
            cachedMask.mask,
            cachedMask.region,
            quality,
            InpaintTarget.VIDEO,
        )

    fun blendFrame(
        frame: Bitmap,
        config: WatermarkConfig,
        quality: RemovalQuality,
        cachedMask: CachedMask? = null,
    ): Bitmap {
        if (cachedMask != null) {
            return refineFrame(frame, quality, cachedMask)
        }
        val mask = MaskGenerator.createMaskMat(frame.width, frame.height, config)
        val region = MaskGenerator.regionForConfig(frame.width, frame.height, config)
        try {
            return PatchMatchInpainter.inpaint(
                frame,
                mask,
                region,
                quality,
                InpaintTarget.VIDEO,
            )
        } finally {
            mask.release()
        }
    }
}
