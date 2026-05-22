package com.watermarkstudio.removal.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/** Streaming [VideoFrameSource] backed by incremental MediaCodec decode. */
class MediaCodecVideoFrameSource(
    context: Context,
    uri: Uri,
    clipDurationMs: Long,
    maxDimension: Int,
    targetFps: Int,
    maxFrames: Int,
) : VideoFrameSource {

    private val decoder =
        MediaCodecStreamDecoder(
            context,
            uri,
            clipDurationMs,
            maxDimension,
            targetFps,
            maxFrames,
        )

    override val fps: Float = decoder.fps
    override val width: Int get() = decoder.width
    override val height: Int get() = decoder.height

    override fun nextFrame(): Bitmap? = decoder.pollNextFrame()

    override fun close() {
        decoder.close()
    }
}
