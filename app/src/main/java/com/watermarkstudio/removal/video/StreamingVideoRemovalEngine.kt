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
        val preferMediaCodec = true
        val source =
            VideoFrameSourceFactory.open(
                context,
                uri,
                sampling,
                maxDimension,
                preferMediaCodec,
            ) ?: return null

        val flowAlgorithm =
            if (quality == RemovalQuality.ADVANCED) {
                OpticalFlowRecoveryProcessor.FlowAlgorithm.PYRAMID_LK
            } else {
                OpticalFlowRecoveryProcessor.FlowAlgorithm.FARNEBACK
            }
        val useFlow = true

        var prev: Bitmap? = null
        var curr: Bitmap? = null
        var frameCount = 0
        var outputFps = sampling.targetFps.toFloat()

        try {
            source.use { src ->
                outputFps = src.fps
                curr = src.nextFrame() ?: return null
                val encoder =
                    IncrementalVideoEncoder(silentOutputFile, curr!!.width, curr!!.height, src.fps)
                try {
                    while (true) {
                        val next = src.nextFrame()
                        val processed =
                            processFrame(
                                prev,
                                curr!!,
                                next ?: curr!!,
                                config,
                                quality,
                                useFlow,
                                flowAlgorithm,
                            )
                        if (!encoder.submitFrame(processed)) {
                            processed.recycle()
                            return null
                        }
                        processed.recycle()
                        frameCount++
                        progress?.report(
                            0.35f + 0.4f * frameCount / sampling.maxFrames.coerceAtLeast(1),
                        )
                        prev?.recycle()
                        if (next == null) {
                            curr?.recycle()
                            break
                        }
                        prev = curr
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
        } catch (e: Exception) {
            e.printStackTrace()
            prev?.recycle()
            curr?.recycle()
            return null
        }
    }

    private fun processFrame(
        prev: Bitmap?,
        curr: Bitmap,
        next: Bitmap,
        config: WatermarkConfig,
        quality: RemovalQuality,
        useFlow: Boolean,
        algorithm: OpticalFlowRecoveryProcessor.FlowAlgorithm,
    ): Bitmap {
        var recovered =
            OpticalFlowRecoveryProcessor.recoverFrame(curr, prev, next, config, useFlow, algorithm)
        val blended = FrameInpaintBlender.blendFrame(recovered, config, quality)
        if (blended !== recovered) recovered.recycle()
        recovered = blended
        if (recovered === curr) {
            return recovered.copy(Bitmap.Config.ARGB_8888, false)
        }
        return recovered
    }
}
