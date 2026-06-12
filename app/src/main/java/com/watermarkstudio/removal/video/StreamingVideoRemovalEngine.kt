package com.watermarkstudio.removal.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.RemovalProgress
import com.watermarkstudio.removal.RemovalQuality
import java.io.File

/**
 * Phase 3b: decode → process → encode one frame at a time (bounded neighbor memory).
 */
object StreamingVideoRemovalEngine {

    data class StreamingResult(
        val silentFile: File,
        val frameCount: Int,
        val fps: Float,
    )

    fun process(
        context: Context,
        uri: Uri,
        config: WatermarkConfig,
        sampling: VideoRemovalLimits.SamplingPlan,
        maxDimension: Int,
        quality: RemovalQuality,
        silentOutputFile: File,
        progress: RemovalProgress?,
    ): StreamingResult? {
        val preferMediaCodec = false
        val source =
            VideoFrameSourceFactory.open(
                context,
                uri,
                sampling,
                maxDimension,
                preferMediaCodec,
            ) ?: return null

        var curr: Bitmap? = null
        var frameCount = 0
        var outputFps = sampling.targetFps.toFloat()
        var maskCache: FrameInpaintBlender.CachedMask? = null

        try {
            source.use { src ->
                outputFps = src.fps
                val first = src.nextFrame() ?: return null
                val preparedFirst = VideoFrameUtils.prepareForVideoEncode(first)
                if (preparedFirst !== first) first.recycle()
                curr = preparedFirst
                maskCache = FrameInpaintBlender.prepareMask(preparedFirst.width, preparedFirst.height, config)
                val encoder =
                    IncrementalVideoEncoder(silentOutputFile, curr.width, curr.height, src.fps)
                try {
                    while (true) {
                        val processed = processFrame(curr!!, config, quality, maskCache!!)
                        if (!encoder.submitFrame(processed)) {
                            processed.recycle()
                            return null
                        }
                        processed.recycle()
                        frameCount++
                        progress?.report(
                            0.35f + 0.4f * frameCount / sampling.maxFrames.coerceAtLeast(1),
                        )
                        curr?.recycle()
                        val next = src.nextFrame()
                        if (next == null) {
                            break
                        }
                        curr = next
                    }
                    if (!encoder.finish()) return null
                } finally {
                    encoder.close()
                }
            }
            if (frameCount == 0) return null
            progress?.report(0.75f)
            return StreamingResult(silentOutputFile, frameCount, outputFps)
        } catch (t: Throwable) {
            t.printStackTrace()
            curr?.recycle()
            return null
        } finally {
            maskCache?.release()
        }
    }

    private fun processFrame(
        curr: Bitmap,
        config: WatermarkConfig,
        quality: RemovalQuality,
        maskCache: FrameInpaintBlender.CachedMask,
    ): Bitmap {
        val base = VideoFrameUtils.prepareForVideoEncode(curr)
        return try {
            val blended = FrameInpaintBlender.blendFrame(base, config, quality, maskCache)
            val encoded = VideoFrameUtils.prepareForVideoEncode(blended)
            if (encoded !== blended) blended.recycle()
            encoded
        } catch (t: Throwable) {
            t.printStackTrace()
            VideoFrameUtils.prepareForVideoEncode(base.copy(Bitmap.Config.ARGB_8888, false))
        } finally {
            if (base !== curr) base.recycle()
        }
    }
}
