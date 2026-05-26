package com.watermarkstudio.removal.video

import android.content.Context
import android.net.Uri
import android.util.Log

object VideoFrameSourceFactory {

    private const val TAG = "VideoFrameSource"

    /**
     * Opens a streaming frame source.
     *
     * - Samsung Exynos: **requires** FFmpeg software frames (no Retriever / app MediaCodec decode).
     * - Other devices: [RetrieverVideoFrameSource] unless [preferMediaCodec] is true.
     */
    fun open(
        context: Context,
        uri: Uri,
        sampling: VideoRemovalLimits.SamplingPlan,
        maxDimension: Int,
        preferMediaCodec: Boolean,
    ): VideoFrameSource? {
        val exynosSafe = VideoHardwareCompat.prefersSoftwareFrameDecode()

        if (exynosSafe) {
            if (!FfmpegRemuxHelper.isAvailable()) {
                Log.e(TAG, "FFmpeg-kit required on Exynos for video decode but unavailable")
                return null
            }
            return try {
                Log.i(
                    TAG,
                    "Opening FfmpegVideoFrameSource (Exynos-safe, fps=${sampling.targetFps}, maxFrames=${sampling.maxFrames})",
                )
                FfmpegVideoFrameSource(
                    context,
                    uri,
                    sampling.clipDurationMs,
                    maxDimension,
                    sampling.targetFps,
                    sampling.maxFrames,
                )
            } catch (e: Exception) {
                Log.e(TAG, "FFmpeg frame source failed on Exynos (no Retriever fallback)", e)
                null
            }
        }

        if (preferMediaCodec) {
            try {
                Log.i(TAG, "Opening MediaCodecVideoFrameSource")
                return MediaCodecVideoFrameSource(
                    context,
                    uri,
                    sampling.clipDurationMs,
                    maxDimension,
                    sampling.targetFps,
                    sampling.maxFrames,
                )
            } catch (e: Exception) {
                Log.w(TAG, "MediaCodec frame source failed", e)
            }
        }

        return try {
            Log.i(TAG, "Opening RetrieverVideoFrameSource")
            RetrieverVideoFrameSource(
                context,
                uri,
                sampling.clipDurationMs,
                maxDimension,
                sampling.targetFps,
                sampling.maxFrames,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Retriever frame source failed", e)
            null
        }
    }
}
