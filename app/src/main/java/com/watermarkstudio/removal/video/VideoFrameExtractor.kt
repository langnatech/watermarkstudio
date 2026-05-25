package com.watermarkstudio.removal.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri

object VideoFrameExtractor {

    private const val PREVIEW_MAX_DIM = 720

    /**
     * Single frame for editor preview (Coil cannot decode video URIs).
     */
    fun loadPreviewFrame(
        context: Context,
        uri: Uri,
        maxDimension: Int = PREVIEW_MAX_DIM,
        timeUs: Long = 0L,
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame =
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            frame?.let { VideoFrameUtils.downscaleIfNeeded(it, maxDimension) }
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

    fun extract(
        context: Context,
        uri: Uri,
        maxDurationMs: Long,
        maxDimension: Int,
        targetFps: Int = 12,
        maxFrames: Int = VideoRemovalLimits.MAX_FRAME_COUNT,
    ): ExtractedFrames? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?: 0L
            val clipMs = if (maxDurationMs > 0L) minOf(durationMs, maxDurationMs) else durationMs
            if (clipMs <= 0L) return null

            val fps = VideoRemovalLimits.clampTargetFps(targetFps)
            val intervalUs = 1_000_000L / fps
            val frames = mutableListOf<Bitmap>()
            var tUs = intervalUs
            while (tUs <= clipMs * 1000L && frames.size < maxFrames) {
                val frame =
                    retriever.getFrameAtTime(
                        tUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    )
                if (frame != null) {
                    frames.add(VideoFrameUtils.downscaleIfNeeded(frame, maxDimension))
                }
                tUs += intervalUs
            }
            if (frames.isEmpty()) return null
            ExtractedFrames(
                bitmaps = frames,
                fps = fps.toFloat(),
                width = frames.first().width,
                height = frames.first().height,
            )
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
}
