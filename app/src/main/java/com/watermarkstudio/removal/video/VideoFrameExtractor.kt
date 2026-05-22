package com.watermarkstudio.removal.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri

object VideoFrameExtractor {

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
    ): ExtractedFrames? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?: 0L
            val clipMs = if (maxDurationMs > 0L) minOf(durationMs, maxDurationMs) else durationMs
            if (clipMs <= 0L) return null

            val intervalUs = 1_000_000L / targetFps.coerceIn(4, 24)
            val frames = mutableListOf<Bitmap>()
            var tUs = 0L
            while (tUs <= clipMs * 1000L) {
                val frame = retriever.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    frames.add(downscaleIfNeeded(frame, maxDimension))
                }
                tUs += intervalUs
            }
            if (frames.isEmpty()) return null
            ExtractedFrames(
                bitmaps = frames,
                fps = targetFps.toFloat(),
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

    private fun downscaleIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val maxSide = maxOf(w, h)
        if (maxSide <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxSide
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, nw, nh, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }
}
