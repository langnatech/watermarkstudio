package com.watermarkstudio.removal.video

import android.content.Context
import android.net.Uri

object VideoFrameSourceFactory {

    /**
     * Opens a streaming frame source. When [preferMediaCodec] is true, tries MediaCodec first
     * (better for H.265 / rotation metadata), then falls back to [RetrieverVideoFrameSource].
     */
    fun open(
        context: Context,
        uri: Uri,
        sampling: VideoRemovalLimits.SamplingPlan,
        maxDimension: Int,
        preferMediaCodec: Boolean,
    ): VideoFrameSource? {
        if (preferMediaCodec) {
            try {
                return MediaCodecVideoFrameSource(
                    context,
                    uri,
                    sampling.clipDurationMs,
                    maxDimension,
                    sampling.targetFps,
                    sampling.maxFrames,
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return try {
            RetrieverVideoFrameSource(
                context,
                uri,
                sampling.clipDurationMs,
                maxDimension,
                sampling.targetFps,
                sampling.maxFrames,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
