package com.watermarkstudio.removal.video

import android.graphics.Bitmap
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.mask.MaskGenerator

/**
 * Streaming-friendly ROI temporal median over a small sliding window (no full-video buffer).
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
        var idx = 0
        for (y in region.top until region.bottom) {
            for (x in region.left until region.right) {
                val rs = (0 until window.size).map { (roiPixels[it][idx] shr 16) and 0xFF }.sorted()
                val gs = (0 until window.size).map { (roiPixels[it][idx] shr 8) and 0xFF }.sorted()
                val bs = (0 until window.size).map { roiPixels[it][idx] and 0xFF }.sorted()
                val mid = window.size / 2
                val a = (current.getPixel(x, y) shr 24) and 0xFF
                val color = (a shl 24) or (rs[mid] shl 16) or (gs[mid] shl 8) or bs[mid]
                out.setPixel(x, y, color)
                idx++
            }
        }
        return out
    }

    val windowSize: Int get() = WINDOW
}
