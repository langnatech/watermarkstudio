package com.watermarkstudio.removal.video

import android.graphics.Bitmap
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.RemovalQuality
import com.watermarkstudio.removal.mask.MaskGenerator

/**
 * Fills masked pixels using neighboring frames before PatchMatch texture refinement.
 * Uses a sliding window (default 7) for temporal stability on static watermarks.
 */
object TemporalPrefillProcessor {

    const val WINDOW_SIZE: Int = 7

    fun prefill(
        current: Bitmap,
        window: List<Bitmap>,
        config: WatermarkConfig,
        quality: RemovalQuality,
        lookahead: Bitmap? = null,
    ): Bitmap {
        if (window.isEmpty()) return current
        return when (quality) {
            RemovalQuality.STANDARD ->
                RoiWindowMedianProcessor.recoverFrame(current, window, config)
            RemovalQuality.ADVANCED -> {
                val median = RoiWindowMedianProcessor.recoverFrame(current, window, config)
                val previous = if (window.size >= 2) window[window.size - 2] else null
                val flowed =
                    OpticalFlowRecoveryProcessor.recoverFrame(
                        current = current,
                        previous = previous,
                        next = lookahead,
                        config = config,
                        useOpticalFlow = true,
                    )
                val fused = fuseMedianAndOpticalFlow(current, median, flowed, config)
                if (median !== current && median !== fused && !median.isRecycled) {
                    median.recycle()
                }
                if (flowed !== current && flowed !== median && flowed !== fused && !flowed.isRecycled) {
                    flowed.recycle()
                }
                fused
            }
        }
    }

    /**
     * Average median and optical-flow prefill inside the inpaint core.
     * Stabilizes static watermarks while keeping motion-aware flow where it helps.
     */
    internal fun fuseMedianAndOpticalFlow(
        current: Bitmap,
        median: Bitmap,
        flowed: Bitmap,
        config: WatermarkConfig,
    ): Bitmap {
        if (median === flowed) return median
        val width = current.width
        val height = current.height
        if (median.width != width || median.height != height ||
            flowed.width != width || flowed.height != height
        ) {
            return flowed
        }
        val region = MaskGenerator.regionForConfig(width, height, config)
        if (region.width <= 0 || region.height <= 0) return flowed
        val mask = MaskGenerator.createMaskMat(width, height, config)
        val maskBytes = ByteArray(width * height)
        mask.get(0, 0, maskBytes)
        mask.release()

        val out = current.copy(Bitmap.Config.ARGB_8888, true)
        for (y in region.top until region.bottom) {
            for (x in region.left until region.right) {
                if ((maskBytes[y * width + x].toInt() and 0xFF) < MaskGenerator.INPAINT_CORE_THRESHOLD) {
                    continue
                }
                val m = median.getPixel(x, y)
                val f = flowed.getPixel(x, y)
                val a = (current.getPixel(x, y) ushr 24) and 0xFF
                val r = ((((m ushr 16) and 0xFF) + ((f ushr 16) and 0xFF)) / 2)
                val g = ((((m ushr 8) and 0xFF) + ((f ushr 8) and 0xFF)) / 2)
                val b = (((m and 0xFF) + (f and 0xFF)) / 2)
                out.setPixel(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return out
    }

    class SlidingWindow(private val maxSize: Int = WINDOW_SIZE) {
        private val frames = ArrayDeque<Bitmap>()

        fun push(frame: Bitmap) {
            frames.addLast(frame)
            while (frames.size > maxSize) {
                frames.removeFirst().recycle()
            }
        }

        fun snapshot(): List<Bitmap> = frames.toList()

        fun release() {
            frames.forEach { if (!it.isRecycled) it.recycle() }
            frames.clear()
        }
    }
}
