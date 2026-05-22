package com.watermarkstudio.removal.video

import android.content.Context
import android.net.Uri

/**
 * Decodes video via MediaCodec; falls back to [VideoFrameExtractor] on failure.
 */
object MediaCodecFrameDecoder {

    fun decode(
        context: Context,
        uri: Uri,
        maxDurationMs: Long,
        maxDimension: Int,
        targetFps: Int = 12,
    ): DecodedVideoSequence? {
        val clipMs =
            if (maxDurationMs > 0L) {
                maxDurationMs
            } else {
                VideoRemovalLimits.PRO_MAX_DURATION_MS
            }
        val plan = VideoRemovalLimits.resolveSampling(targetFps, clipMs)
        return decodeWithMediaCodec(context, uri, plan.clipDurationMs, maxDimension, plan.targetFps, plan.maxFrames)
            ?: fallbackRetriever(context, uri, plan.clipDurationMs, maxDimension, plan.targetFps, plan.maxFrames)
    }

    private fun fallbackRetriever(
        context: Context,
        uri: Uri,
        maxDurationMs: Long,
        maxDimension: Int,
        targetFps: Int,
        maxFrames: Int,
    ): DecodedVideoSequence? {
        val extracted =
            VideoFrameExtractor.extract(context, uri, maxDurationMs, maxDimension, targetFps, maxFrames)
                ?: return null
        val clipUs =
            if (maxDurationMs > 0L) {
                maxDurationMs * 1000L
            } else {
                (extracted.bitmaps.size * 1_000_000L / targetFps.coerceAtLeast(1))
            }
        return DecodedVideoSequence(
            bitmaps = extracted.bitmaps,
            fps = extracted.fps,
            width = extracted.width,
            height = extracted.height,
            clipDurationUs = clipUs,
        )
    }

    private fun decodeWithMediaCodec(
        context: Context,
        uri: Uri,
        maxDurationMs: Long,
        maxDimension: Int,
        targetFps: Int,
        maxFrames: Int,
    ): DecodedVideoSequence? {
        return try {
            MediaCodecStreamDecoder(
                context,
                uri,
                maxDurationMs,
                maxDimension,
                targetFps,
                maxFrames,
            ).use { session ->
                val bitmaps = session.decodeAll()
                if (bitmaps.isEmpty()) return null
                DecodedVideoSequence(
                    bitmaps = bitmaps,
                    fps = session.fps,
                    width = bitmaps.first().width,
                    height = bitmaps.first().height,
                    clipDurationUs = session.clipDurationUs,
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
