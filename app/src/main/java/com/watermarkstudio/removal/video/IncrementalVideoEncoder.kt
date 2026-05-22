package com.watermarkstudio.removal.video

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Encodes frames one-by-one into H.264 MP4 (no audio). Used by the streaming removal pipeline.
 */
class IncrementalVideoEncoder(
    outputFile: File,
    width: Int,
    height: Int,
    fps: Float,
) : AutoCloseable {

    private val bitRate = (width * height * fps * 0.15f).toInt().coerceIn(1_000_000, 8_000_000)
    private val frameDurationUs = (1_000_000f / fps.coerceAtLeast(1f)).toLong()
    private val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val bufferInfo = MediaCodec.BufferInfo()
    private var trackIndex = -1
    private var muxerStarted = false
    private var frameIndex = 0
    private var inputDone = false
    private var encodeSuccess = false

    init {
        val yuvFormat =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps.toInt().coerceAtLeast(1))
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
        encoder.configure(yuvFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
    }

    fun submitFrame(bitmap: Bitmap): Boolean {
        if (inputDone) return false
        queueInput(bitmap)
        return drainOutputs(endOfStream = false)
    }

    fun finish(): Boolean {
        if (!inputDone) {
            queueEndOfStream()
            drainOutputs(endOfStream = true)
        }
        return encodeSuccess
    }

    private fun queueInput(bitmap: Bitmap) {
        var queued = false
        while (!queued) {
            val inputIndex = encoder.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                val yuv = VideoFrameUtils.bitmapToYuv420(bitmap, bitmap.width, bitmap.height)
                val inputBuffer = encoder.getInputBuffer(inputIndex)!!
                inputBuffer.clear()
                inputBuffer.put(yuv)
                val pts = frameIndex * frameDurationUs
                encoder.queueInputBuffer(inputIndex, 0, yuv.size, pts, 0)
                frameIndex++
                queued = true
            } else {
                if (!drainOutputs(endOfStream = false)) return
            }
        }
    }

    private fun queueEndOfStream() {
        while (!inputDone) {
            val inputIndex = encoder.dequeueInputBuffer(10_000)
            if (inputIndex >= 0) {
                encoder.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    frameIndex * frameDurationUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                inputDone = true
            } else {
                drainOutputs(endOfStream = false)
            }
        }
    }

    private fun drainOutputs(endOfStream: Boolean): Boolean {
        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                outputIndex >= 0 -> {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    val encoded = encoder.getOutputBuffer(outputIndex)!!
                    if (bufferInfo.size > 0 && bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        encodeSuccess = muxerStarted && frameIndex > 0
                        return encodeSuccess
                    }
                }
                else -> {
                    if (endOfStream) return encodeSuccess
                    return muxerStarted
                }
            }
        }
    }

    override fun close() {
        try {
            encoder.stop()
            encoder.release()
        } catch (_: Exception) {
        }
        try {
            if (muxerStarted) muxer.stop()
            muxer.release()
        } catch (_: Exception) {
        }
    }
}
