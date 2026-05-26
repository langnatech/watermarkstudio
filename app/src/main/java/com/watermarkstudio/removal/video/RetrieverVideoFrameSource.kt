package com.watermarkstudio.removal.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri

class RetrieverVideoFrameSource(
    context: Context,
    uri: Uri,
    clipDurationMs: Long,
    private val maxDimension: Int,
    targetFps: Int,
    private val maxFrames: Int,
) : VideoFrameSource {

    private val retriever = MediaMetadataRetriever()
    private val intervalUs = 1_000_000L / VideoRemovalLimits.clampTargetFps(targetFps)
    private val clipUs = clipDurationMs * 1000L
    private var tUs = 0L
    private var emitted = 0
    private var _width = 0
    private var _height = 0

    override val fps: Float = targetFps.toFloat()
    override val width: Int get() = _width
    override val height: Int get() = _height

    init {
        retriever.setDataSource(context, uri)
        val durationMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 0L
        if (clipDurationMs <= 0L || durationMs <= 0L) {
            throw IllegalArgumentException("Invalid clip duration")
        }
    }

    override fun nextFrame(): Bitmap? {
        while (tUs <= clipUs && emitted < maxFrames) {
            val raw = retriever.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST)
            tUs += intervalUs
            if (raw != null) {
                val frame =
                    raw.copy(Bitmap.Config.ARGB_8888, false).also {
                        if (it !== raw) raw.recycle()
                    }
                val scaled = VideoFrameUtils.downscaleIfNeeded(frame, maxDimension)
                val normalized = VideoFrameUtils.ensureDimensions(scaled, _width, _height)
                if (normalized !== scaled) scaled.recycle()
                if (_width == 0) {
                    _width = normalized.width
                    _height = normalized.height
                }
                emitted++
                return normalized
            }
        }
        return null
    }

    override fun close() {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }
}
