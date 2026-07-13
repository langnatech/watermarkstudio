package com.watermarkstudio.removal.video

import android.graphics.Bitmap
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.mask.MaskGenerator

/**
 * Streaming-friendly ROI temporal vector-median over a small sliding window (no full-video buffer).
 */
object RoiWindowMedianProcessor {

    private const val WINDOW = 7

    fun recoverFrame(
        current: Bitmap,
        window: List<Bitmap>,
        config: WatermarkConfig,
    ): Bitmap {
        if (window.size < 2) return current
        val width = current.width
        val height = current.height
        val region = MaskGenerator.regionForConfig(width, height, config)
        if (region.width <= 0 || region.height <= 0) return current
        val mask = MaskGenerator.createMaskMat(width, height, config)
        val maskBytes = ByteArray(width * height)
        mask.get(0, 0, maskBytes)
        mask.release()

        val out = current.copy(Bitmap.Config.ARGB_8888, true)
        val roiPixels = Array(window.size) { IntArray(region.width * region.height) }
        for (i in window.indices) {
            var idx = 0
            for (y in region.top until region.bottom) {
                for (x in region.left until region.right) {
                    roiPixels[i][idx++] = window[i].getPixel(x, y)
                }
            }
        }
        val sampleBuf = IntArray(window.size)
        var idx = 0
        for (y in region.top until region.bottom) {
            for (x in region.left until region.right) {
                if ((maskBytes[y * width + x].toInt() and 0xFF) >= MaskGenerator.INPAINT_CORE_THRESHOLD) {
                    for (i in window.indices) {
                        sampleBuf[i] = roiPixels[i][idx]
                    }
                    val a = (current.getPixel(x, y) ushr 24) and 0xFF
                    out.setPixel(x, y, TemporalVectorMedian.selectArgb(sampleBuf, window.size, a))
                }
                idx++
            }
        }
        return out
    }

    val windowSize: Int get() = WINDOW
}
