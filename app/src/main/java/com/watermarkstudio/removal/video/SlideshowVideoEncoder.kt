package com.watermarkstudio.removal.video

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
/**
 * Encodes a sequence of bitmaps into H.264 MP4 (no audio). Used after temporal recovery.
 */
object SlideshowVideoEncoder {

    fun encode(
        frames: List<Bitmap>,
        fps: Float,
        outputFile: File,
    ): Boolean {
        if (frames.isEmpty()) return false
        val width = frames.first().width
        val height = frames.first().height
        val bitRate = (width * height * fps * 0.15f).toInt().coerceIn(1_000_000, 8_000_000)
        val yuvFormat =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps.toInt().coerceAtLeast(1))
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

        val encoder = VideoAvcCodecSelector.createAvcEncoder()
        encoder.configure(yuvFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        val frameDurationUs = (1_000_000f / fps).toLong()

        try {
            var frameIndex = 0
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val inputIndex = encoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        if (frameIndex < frames.size) {
                            val bitmap = frames[frameIndex]
                            val yuv = VideoFrameUtils.bitmapToYuv420(bitmap, width, height)
                            val inputBuffer = encoder.getInputBuffer(inputIndex)!!
                            inputBuffer.clear()
                            inputBuffer.put(yuv)
                            val pts = frameIndex * frameDurationUs
                            encoder.queueInputBuffer(inputIndex, 0, yuv.size, pts, 0)
                            frameIndex++
                        } else {
                            encoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                frameIndex * frameDurationUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        }
                    }
                }

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
                            break
                        }
                    }
                }
            }
            return muxerStarted
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
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
}
