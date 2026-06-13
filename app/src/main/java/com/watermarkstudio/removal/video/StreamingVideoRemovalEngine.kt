package com.watermarkstudio.removal.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.RemovalProgress
import com.watermarkstudio.removal.RemovalQuality

/**
 * Decode → temporal prefill → PatchMatch refine → encode one frame at a time.
 */
object StreamingVideoRemovalEngine {

    data class StreamingResult(
        val silentFile: java.io.File,
        val frameCount: Int,
        val fps: Float,
    )

    private const val MAX_FAILURE_RATIO = 0.05f
    private const val MIN_FAILURE_COUNT = 3

    fun process(
        context: Context,
        uri: Uri,
        config: WatermarkConfig,
        sampling: VideoRemovalLimits.SamplingPlan,
        maxDimension: Int,
        quality: RemovalQuality,
        silentOutputFile: java.io.File,
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
        var failedFrames = 0
        var outputFps = sampling.targetFps.toFloat()
        var maskCache: FrameInpaintBlender.CachedMask? = null
        val frameWindow = TemporalPrefillProcessor.SlidingWindow()

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
                        val processResult =
                            processFrame(
                                curr!!,
                                config,
                                quality,
                                maskCache!!,
                                frameWindow,
                            )
                        val processed =
                            processResult.bitmap
                                ?: run {
                                    failedFrames++
                                    if (shouldAbortExport(frameCount, failedFrames)) {
                                        return null
                                    }
                                    VideoFrameUtils.prepareForVideoEncode(
                                        curr!!.copy(Bitmap.Config.ARGB_8888, false),
                                    )
                                }
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
            if (shouldAbortExport(frameCount, failedFrames)) return null
            progress?.report(0.75f)
            return StreamingResult(silentOutputFile, frameCount, outputFps)
        } catch (t: Throwable) {
            t.printStackTrace()
            curr?.recycle()
            return null
        } finally {
            frameWindow.release()
            maskCache?.release()
        }
    }

    private data class FrameProcessResult(val bitmap: Bitmap?)

    private fun processFrame(
        curr: Bitmap,
        config: WatermarkConfig,
        quality: RemovalQuality,
        maskCache: FrameInpaintBlender.CachedMask,
        frameWindow: TemporalPrefillProcessor.SlidingWindow,
    ): FrameProcessResult {
        val base = VideoFrameUtils.prepareForVideoEncode(curr)
        return try {
            val windowCopy = base.copy(Bitmap.Config.ARGB_8888, false)
            frameWindow.push(windowCopy)
            val prefilled =
                TemporalPrefillProcessor.prefill(
                    base,
                    frameWindow.snapshot(),
                    config,
                    quality,
                )
            val refined = FrameInpaintBlender.refineFrame(prefilled, quality, maskCache)
            if (prefilled !== base && prefilled !== refined && !prefilled.isRecycled) {
                prefilled.recycle()
            }
            val encoded = VideoFrameUtils.prepareForVideoEncode(refined)
            if (encoded !== refined) refined.recycle()
            FrameProcessResult(encoded)
        } catch (t: Throwable) {
            t.printStackTrace()
            FrameProcessResult(null)
        } finally {
            if (base !== curr) base.recycle()
        }
    }

    private fun shouldAbortExport(processedFrames: Int, failedFrames: Int): Boolean {
        val threshold = maxOf(MIN_FAILURE_COUNT, (processedFrames * MAX_FAILURE_RATIO).toInt())
        return failedFrames > threshold
    }
}
