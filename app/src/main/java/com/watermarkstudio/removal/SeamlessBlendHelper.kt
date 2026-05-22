package com.watermarkstudio.removal

import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.mask.MaskGenerator
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.photo.Photo

object SeamlessBlendHelper {

    fun seamlessCloneInpaint(
        src: Mat,
        inpainted: Mat,
        featherMask: Mat,
        width: Int,
        height: Int,
        config: WatermarkConfig,
        dst: Mat,
    ) {
        val region = MaskGenerator.regionForConfig(width, height, config)
        val center =
            Point(
                (region.left + region.right) / 2.0,
                (region.top + region.bottom) / 2.0,
            )
        Photo.seamlessClone(inpainted, src, featherMask, center, dst, Photo.NORMAL_CLONE)
    }
}
