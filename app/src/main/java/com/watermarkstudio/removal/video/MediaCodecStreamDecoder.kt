package com.watermarkstudio.removal.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

/**
 * Incremental MediaCodec decode session shared by [MediaCodecVideoFrameSource] and batch decode.
 */
internal class MediaCodecStreamDecoder(
    context: Context,
    uri: Uri,
    clipDurationMs: Long,
    private val maxDimension: Int,
    targetFps: Int,
    private val     maxFrames: Int,
) : AutoCloseable {

    private val extractor = MediaExtractor()
    private var decoder: MediaCodec? = null
    private var imageReader: ImageReader? = null

    private val frameIntervalUs = 1_000_000L / targetFps.coerceIn(4, 24)
    private var nextSampleUs = 0L
    var clipDurationUs: Long = 0L
        private set
    private var rotation = 0
    private var emitted = 0

    private var inputDone = false
    private var outputDone = false
    private val bufferInfo = MediaCodec.BufferInfo()

    private var _width = 0
    private var _height = 0

    val fps: Float = targetFps.toFloat()
    val width: Int get() = _width
    val height: Int get() = _height

    init {
        extractor.setDataSource(context, uri, null)
        var videoTrack = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) {
                videoTrack = i
                format = f
                break
            }
        }
        if (videoTrack < 0 || format == null) {
            throw IllegalStateException("No video track")
        }
        extractor.selectTrack(videoTrack)

        val width = format.getInteger(MediaFormat.KEY_WIDTH)
        val height = format.getInteger(MediaFormat.KEY_HEIGHT)
        rotation =
            if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                format.getInteger(MediaFormat.KEY_ROTATION)
            } else {
                0
            }
        val durationUs =
            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
        clipDurationUs =
            if (clipDurationMs > 0L) {
                minOf(durationUs, clipDurationMs * 1000L)
            } else {
                durationUs
            }
        if (clipDurationUs <= 0L) {
            throw IllegalStateException("Invalid clip duration")
        }

        val readerWidth = if (rotation == 90 || rotation == 270) height else width
        val readerHeight = if (rotation == 90 || rotation == 270) width else height
        imageReader = ImageReader.newInstance(readerWidth, readerHeight, ImageFormat.YUV_420_888, 8)
        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalStateException("Missing MIME")
        decoder = MediaCodec.createDecoderByType(mime)
        decoder!!.configure(format, imageReader!!.surface, null, 0)
        decoder!!.start()
    }

    /** Advances decode until the next sampled frame is ready, or null at EOF. */
    fun pollNextFrame(): Bitmap? {
        if (outputDone || emitted >= maxFrames) return null
        val codec = decoder ?: return null
        val reader = imageReader ?: return null

        decodeLoop@ while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val sampleSize = extractor.readSampleData(codec.getInputBuffer(inIndex)!!, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        val pts = extractor.sampleTime
                        if (pts > clipDurationUs) {
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outIndex >= 0 -> {
                    val eos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (bufferInfo.size > 0 && bufferInfo.presentationTimeUs <= clipDurationUs) {
                        while (nextSampleUs <= bufferInfo.presentationTimeUs) {
                            val image = reader.acquireLatestImage()
                            if (image != null) {
                                val bmp = imageToBitmap(image, rotation)
                                image.close()
                                if (bmp != null) {
                                    val scaled = VideoFrameUtils.downscaleIfNeeded(bmp, maxDimension)
                                    if (_width == 0) {
                                        _width = scaled.width
                                        _height = scaled.height
                                    }
                                    emitted++
                                    nextSampleUs += frameIntervalUs
                                    codec.releaseOutputBuffer(outIndex, true)
                                    return scaled
                                }
                            }
                            nextSampleUs += frameIntervalUs
                            if (emitted >= maxFrames) {
                                outputDone = true
                                codec.releaseOutputBuffer(outIndex, true)
                                return null
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, true)
                    if (eos) outputDone = true
                }
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (inputDone) {
                        val img = reader.acquireLatestImage()
                        if (img == null && emitted > 0) {
                            outputDone = true
                            break@decodeLoop
                        }
                        img?.close()
                    }
                }
            }
        }
        return null
    }

    fun decodeAll(): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        while (true) {
            val frame = pollNextFrame() ?: break
            bitmaps.add(frame)
        }
        return bitmaps
    }

    override fun close() {
        outputDone = true
        try {
            decoder?.stop()
            decoder?.release()
        } catch (_: Exception) {
        }
        decoder = null
        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null
        try {
            extractor.release()
        } catch (_: Exception) {
        }
    }

    private fun imageToBitmap(image: Image, rotation: Int): Bitmap? {
        var bmp = VideoFrameUtils.imageYuv420888ToBitmap(image) ?: return null
        if (rotation != 0) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated =
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (rotated != bmp) bmp.recycle()
            bmp = rotated
        }
        return bmp
    }
}
