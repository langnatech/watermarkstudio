package com.watermarkstudio.removal.video

/**
 * Shared caps for removal video pipeline (aligned with [com.watermarkstudio.util.MediaProcessor]).
 */
object VideoRemovalLimits {

    const val FREE_MAX_DURATION_MS = 15_000L
    const val PRO_MAX_DURATION_MS = 300_000L

    /** Hard cap on decoded frames to avoid OOM on long Pro exports. */
    const val MAX_FRAME_COUNT = 480

    data class SamplingPlan(
        val targetFps: Int,
        val maxFrames: Int,
        val clipDurationMs: Long,
    )

    fun clampClipDurationMs(requestedMs: Long, isPremium: Boolean): Long {
        val cap = if (isPremium) PRO_MAX_DURATION_MS else FREE_MAX_DURATION_MS
        return when {
            requestedMs <= 0L -> cap
            else -> minOf(requestedMs, cap)
        }
    }

    fun resolveSampling(targetFps: Int, clipDurationMs: Long): SamplingPlan {
        var fps = targetFps.coerceIn(4, 24)
        var maxFrames = frameCountFor(clipDurationMs, fps)
        while (maxFrames > MAX_FRAME_COUNT && fps > 4) {
            fps -= 1
            maxFrames = frameCountFor(clipDurationMs, fps)
        }
        if (maxFrames > MAX_FRAME_COUNT) {
            maxFrames = MAX_FRAME_COUNT
        }
        return SamplingPlan(fps, maxFrames, clipDurationMs)
    }

    fun videoDurationUs(frameCount: Int, fps: Float): Long {
        if (frameCount <= 0) return 0L
        val fpsInt = fps.coerceAtLeast(1f).toInt()
        return frameCount * 1_000_000L / fpsInt
    }

    private fun frameCountFor(durationMs: Long, fps: Int): Int {
        if (durationMs <= 0L) return 1
        return ((durationMs * fps) / 1000L).toInt().coerceAtLeast(1)
    }
}
