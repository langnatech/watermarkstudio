package com.watermarkstudio.removal.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
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
        return decodeWithMediaCodec(context, uri, maxDurationMs, maxDimension, targetFps)
            ?: fallbackRetriever(context, uri, maxDurationMs, maxDimension, targetFps)
    }

    private fun fallbackRetriever(
        context: Context,
        uri: Uri,
        maxDurationMs: Long,
        maxDimension: Int,
        targetFps: Int,
    ): DecodedVideoSequence? {
        val extracted =
            VideoFrameExtractor.extract(context, uri, maxDurationMs, maxDimension, targetFps)
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
    ): DecodedVideoSequence? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var imageReader: ImageReader? = null
        return try {
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
            if (videoTrack < 0 || format == null) return null
            extractor.selectTrack(videoTrack)

            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            val rotation =
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
            val clipUs =
                if (maxDurationMs > 0L) {
                    minOf(durationUs, maxDurationMs * 1000L)
                } else {
                    durationUs
                }
            if (clipUs <= 0L) return null

            imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 8)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, imageReader.surface, null, 0)
            decoder.start()

            val frameIntervalUs = 1_000_000L / targetFps.coerceIn(4, 24)
            val bitmaps = mutableListOf<Bitmap>()
            var nextSampleUs = 0L
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val sampleSize = extractor.readSampleData(decoder.getInputBuffer(inIndex)!!, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            val pts = extractor.sampleTime
                            if (pts > clipUs) {
                                decoder.queueInputBuffer(
                                    inIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex >= 0 -> {
                        val eos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (bufferInfo.size > 0 && bufferInfo.presentationTimeUs <= clipUs) {
                            while (nextSampleUs <= bufferInfo.presentationTimeUs) {
                                val image = imageReader.acquireLatestImage()
                                if (image != null) {
                                    val bmp = imageToBitmap(image, rotation)
                                    image.close()
                                    if (bmp != null) {
                                        bitmaps.add(VideoFrameUtils.downscaleIfNeeded(bmp, maxDimension))
                                    }
                                }
                                nextSampleUs += frameIntervalUs
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, true)
                        if (eos) outputDone = true
                    }
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputDone) {
                            val img = imageReader.acquireLatestImage()
                            if (img == null && bitmaps.isNotEmpty()) outputDone = true
                            else img?.close()
                        }
                    }
                }
            }

            if (bitmaps.isEmpty()) return null
            DecodedVideoSequence(
                bitmaps = bitmaps,
                fps = targetFps.toFloat(),
                width = bitmaps.first().width,
                height = bitmaps.first().height,
                clipDurationUs = clipUs,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                decoder?.stop()
                decoder?.release()
            } catch (_: Exception) {
            }
            imageReader?.close()
            extractor.release()
        }
    }

    private fun imageToBitmap(image: Image, rotation: Int): Bitmap? {
        val planes = image.planes
        if (planes.size < 3) return null
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, image.width, image.height), 95, out)
        val jpeg = out.toByteArray()
        var bmp = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        if (rotation != 0) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated =
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (rotated != bmp) bmp.recycle()
            bmp = rotated
        }
        return bmp.copy(Bitmap.Config.ARGB_8888, false)
    }
}
