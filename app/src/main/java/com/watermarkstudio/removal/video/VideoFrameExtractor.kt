package com.watermarkstudio.removal.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.media.MediaMetadataRetriever
import android.net.Uri

object VideoFrameExtractor {

    private const val PREVIEW_MAX_DIM = 720

    /**
     * Single frame for editor preview (Coil cannot decode video URIs).
     * Exynos: FFmpeg software decode; others: Retriever.
     */
    fun loadPreviewFrame(
        context: Context,
        uri: Uri,
        maxDimension: Int = PREVIEW_MAX_DIM,
        timeUs: Long = 0L,
    ): Bitmap? {
        if (VideoHardwareCompat.prefersSoftwareFrameDecode() && FfmpegRemuxHelper.isAvailable()) {
            return FfmpegFrameExtractHelper.extractSingleFrameBitmap(
                context,
                uri,
                maxDimension,
                timeUs,
            )
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val raw =
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            raw?.let { frame ->
                val copy = frame.copy(Config.ARGB_8888, false)
                if (copy !== frame) frame.recycle()
                VideoFrameUtils.downscaleIfNeeded(copy, maxDimension)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun loadVideoDimensions(context: Context, uri: Uri): Pair<Int, Int>? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val w =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
            val h =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
            if (w != null && h != null) w to h else null
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    data class ExtractedFrames(
        val bitmaps: List<Bitmap>,
        val fps: Float,
        val width: Int,
        val height: Int,
    )

    /**
     * Batch decode via [VideoFrameSourceFactory] so Exynos never uses Retriever frame loop.
     */
    fun extract(
        context: Context,
        uri: Uri,
        maxDurationMs: Long,
        maxDimension: Int,
        targetFps: Int = 12,
        maxFrames: Int = VideoRemovalLimits.MAX_FRAME_COUNT,
    ): ExtractedFrames? {
        val clipMs =
            if (maxDurationMs > 0L) {
                maxDurationMs
            } else {
                VideoRemovalLimits.FREE_MAX_DURATION_MS
            }
        val plan =
            VideoRemovalLimits.resolveSampling(
                targetFps,
                clipMs,
                maxFrames,
                targetFps.toFloat(),
            )
        val source =
            VideoFrameSourceFactory.open(
                context,
                uri,
                plan,
                maxDimension,
                preferMediaCodec = false,
            ) ?: return null
        val frames = mutableListOf<Bitmap>()
        var outputFps = plan.targetFps.toFloat()
        return try {
            source.use { src ->
                outputFps = src.fps
                while (frames.size < maxFrames) {
                    val frame = src.nextFrame() ?: break
                    frames.add(frame)
                }
            }
            if (frames.isEmpty()) return null
            ExtractedFrames(
                bitmaps = frames,
                fps = outputFps,
                width = frames.first().width,
                height = frames.first().height,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            frames.forEach { if (!it.isRecycled) it.recycle() }
            null
        }
    }
}
