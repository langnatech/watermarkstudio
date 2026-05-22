package com.watermarkstudio.removal.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

/**
 * Encodes processed frames to H.264 and muxes source AAC audio when available.
 */
object VideoExportMuxer {

    fun export(
        context: Context,
        sourceUri: Uri,
        frames: List<android.graphics.Bitmap>,
        fps: Float,
        clipDurationUs: Long,
        outputFile: File,
        includeAudio: Boolean,
    ): Boolean {
        if (frames.isEmpty()) return false
        val width = frames.first().width
        val height = frames.first().height
        val bitRate = (width * height * fps * 0.15f).toInt().coerceIn(1_000_000, 8_000_000)
        val videoFormat =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps.toInt().coerceAtLeast(1))
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrack = -1
        var audioTrack = -1
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
                            val yuv = VideoFrameUtils.bitmapToYuv420(frames[frameIndex], width, height)
                            val inputBuffer = encoder.getInputBuffer(inputIndex)!!
                            inputBuffer.clear()
                            inputBuffer.put(yuv)
                            encoder.queueInputBuffer(inputIndex, 0, yuv.size, frameIndex * frameDurationUs, 0)
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
                            videoTrack = muxer.addTrack(encoder.outputFormat)
                        }
                    }
                    outputIndex >= 0 -> {
                        if (!muxerStarted) {
                            videoTrack = muxer.addTrack(encoder.outputFormat)
                            if (includeAudio) {
                                audioTrack = findAudioTrackIndex(context, sourceUri, muxer, clipDurationUs)
                            }
                            muxer.start()
                            muxerStarted = true
                        }
                        val encoded = encoder.getOutputBuffer(outputIndex)!!
                        if (bufferInfo.size > 0 && bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrack, encoded, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            break
                        }
                    }
                }
            }
            if (!muxerStarted) return false
            if (includeAudio && audioTrack >= 0) {
                copyAudioSamples(context, sourceUri, muxer, audioTrack, clipDurationUs)
            }
            return true
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

    private fun findAudioTrackIndex(
        context: Context,
        uri: Uri,
        muxer: MediaMuxer,
        @Suppress("UNUSED_PARAMETER") clipDurationUs: Long,
    ): Int {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    return muxer.addTrack(format)
                }
            }
            -1
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        } finally {
            extractor.release()
        }
    }

    private fun copyAudioSamples(
        context: Context,
        uri: Uri,
        muxer: MediaMuxer,
        audioTrackIndex: Int,
        clipDurationUs: Long,
    ) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrack = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    break
                }
            }
            if (audioTrack < 0) return
            extractor.selectTrack(audioTrack)
            val buffer = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()
            var firstPts = -1L
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val pts = extractor.sampleTime
                if (firstPts < 0) firstPts = pts
                if (pts - firstPts > clipDurationUs) break
                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = pts - firstPts
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(audioTrackIndex, buffer, info)
                extractor.advance()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            extractor.release()
        }
    }
}
