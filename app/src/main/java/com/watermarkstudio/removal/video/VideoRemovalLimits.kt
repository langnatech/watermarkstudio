package com.watermarkstudio.removal.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlin.math.roundToInt

/**
 * Shared caps for removal video pipeline (aligned with [com.watermarkstudio.util.MediaProcessor]).
 */
object VideoRemovalLimits {

    /** Free: 15s @ 60fps = 900 processed frames. */
    const val FREE_MAX_DURATION_MS = 15_000L
    const val PRO_MAX_DURATION_MS = 300_000L

    /** Maximum export/decode fps (matches common phone video). */
    const val MAX_EXPORT_FPS = 60

    /** Free tier frame budget: 15s × 60fps (no fps downgrade within this window). */
    const val MAX_FRAME_COUNT_FREE = 900

    /** @deprecated Use [MAX_FRAME_COUNT_FREE] */
    const val MAX_FRAME_COUNT = MAX_FRAME_COUNT_FREE

    data class SamplingPlan(
        /** Export/decode fps (may be lower than [sourceFps] when frame budget requires). */
        val targetFps: Int,
        val maxFrames: Int,
        val clipDurationMs: Long,
        val sourceFps: Float,
    )

    fun clampClipDurationMs(requestedMs: Long, isPremium: Boolean): Long {
        val cap = if (isPremium) PRO_MAX_DURATION_MS else FREE_MAX_DURATION_MS
        return when {
            requestedMs <= 0L -> cap
            else -> minOf(requestedMs, cap)
        }
    }

    fun detectSourceFps(context: Context, uri: Uri): Float {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val rate =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?.toFloatOrNull()
            val durationMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
            when {
                rate != null && rate > 1f -> rate
                durationMs != null && durationMs > 0L -> {
                    val frameCount =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                            ?.toLongOrNull()
                    if (frameCount != null && frameCount > 1L) {
                        frameCount * 1000f / durationMs
                    } else {
                        30f
                    }
                }
                else -> 30f
            }
        } catch (_: Exception) {
            30f
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun clampTargetFps(fps: Int): Int = fps.coerceIn(1, MAX_EXPORT_FPS)

    /**
     * Free: source fps up to 60 within 15s; lowers fps only if frame count exceeds [MAX_FRAME_COUNT_FREE].
     * Pro: always keeps source fps (up to 60); longer exports only take more time (streaming path).
     */
    fun resolveSampling(
        context: Context,
        uri: Uri,
        clipDurationMs: Long,
        isPremium: Boolean,
    ): SamplingPlan {
        val sourceFps = detectSourceFps(context, uri)
        val fps = clampTargetFps(sourceFps.roundToInt())
        if (isPremium) {
            return resolveSamplingPro(fps, clipDurationMs, sourceFps)
        }
        return resolveSamplingFree(fps, clipDurationMs, sourceFps)
    }

    fun resolveSampling(targetFps: Int, clipDurationMs: Long): SamplingPlan =
        resolveSamplingFree(clampTargetFps(targetFps), clipDurationMs, targetFps.toFloat())

    /** Pro: never reduce fps for clip length; [maxFrames] = full duration × fps. */
    fun resolveSamplingPro(
        targetFps: Int,
        clipDurationMs: Long,
        sourceFps: Float = targetFps.toFloat(),
    ): SamplingPlan {
        val fps = clampTargetFps(targetFps)
        val maxFrames = frameCountFor(clipDurationMs, fps)
        return SamplingPlan(fps, maxFrames, clipDurationMs, sourceFps)
    }

    /** Free: 15s cap + up to 900 frames (60fps × 15s). */
    fun resolveSamplingFree(
        targetFps: Int,
        clipDurationMs: Long,
        sourceFps: Float = targetFps.toFloat(),
    ): SamplingPlan {
        val clippedMs = minOf(clipDurationMs, FREE_MAX_DURATION_MS)
        var fps = clampTargetFps(targetFps)
        var maxFrames = frameCountFor(clippedMs, fps)
        while (maxFrames > MAX_FRAME_COUNT_FREE && fps > 1) {
            fps -= 1
            maxFrames = frameCountFor(clippedMs, fps)
        }
        if (maxFrames > MAX_FRAME_COUNT_FREE) {
            maxFrames = MAX_FRAME_COUNT_FREE
        }
        return SamplingPlan(fps, maxFrames, clippedMs, sourceFps)
    }

    fun resolveSampling(
        targetFps: Int,
        clipDurationMs: Long,
        maxFrameCount: Int,
        sourceFps: Float = targetFps.toFloat(),
    ): SamplingPlan {
        if (maxFrameCount >= Int.MAX_VALUE / 2) {
            return resolveSamplingPro(targetFps, clipDurationMs, sourceFps)
        }
        var fps = clampTargetFps(targetFps)
        var maxFrames = frameCountFor(clipDurationMs, fps)
        while (maxFrames > maxFrameCount && fps > 1) {
            fps -= 1
            maxFrames = frameCountFor(clipDurationMs, fps)
        }
        if (maxFrames > maxFrameCount) {
            maxFrames = maxFrameCount
        }
        return SamplingPlan(fps, maxFrames, clipDurationMs, sourceFps)
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
