package com.watermarkstudio.removal.video

import android.graphics.Bitmap
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.RemovalQuality

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
                val previous = if (window.size >= 2) window[window.size - 2] else null
                OpticalFlowRecoveryProcessor.recoverFrame(
                    current = current,
                    previous = previous,
                    next = lookahead,
                    config = config,
                    useOpticalFlow = true,
                )
            }
        }
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
