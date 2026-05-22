package com.watermarkstudio.removal.video

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.OpenCvBootstrap
import com.watermarkstudio.removal.RemovalCapability
import com.watermarkstudio.removal.RemovalProgress
import com.watermarkstudio.removal.RemovalQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoRemovalEngine {

    suspend fun removeRegion(
        context: Context,
        uri: Uri,
        config: WatermarkConfig,
        maxDurationMs: Long,
        maxDimension: Int,
        isPremium: Boolean,
        quality: RemovalQuality,
        progress: RemovalProgress? = null,
    ): Uri? = withContext(Dispatchers.Default) {
        if (!OpenCvBootstrap.ensureLoaded(context)) return@withContext null
        if (!RemovalCapability.supportsVideoRemoval(context)) return@withContext null

        val clipMs =
            if (maxDurationMs > 0L) {
                maxDurationMs
            } else {
                VideoRemovalLimits.PRO_MAX_DURATION_MS
            }
        val baseFps = if (isPremium) 15 else if (quality == RemovalQuality.ADVANCED) 12 else 10
        val sampling = VideoRemovalLimits.resolveSampling(baseFps, clipMs)

        fun report(stageStart: Float, stageEnd: Float, fraction: Float) {
            progress?.report(stageStart + (stageEnd - stageStart) * fraction.coerceIn(0f, 1f))
        }

        report(0f, 0.35f, 0f)
        val decoded =
            if (quality == RemovalQuality.ADVANCED) {
                MediaCodecFrameDecoder.decode(
                    context,
                    uri,
                    sampling.clipDurationMs,
                    maxDimension,
                    targetFps = sampling.targetFps,
                )
            } else {
                VideoFrameExtractor.extract(
                    context,
                    uri,
                    sampling.clipDurationMs,
                    maxDimension,
                    targetFps = sampling.targetFps,
                    maxFrames = sampling.maxFrames,
                )
                    ?.let { ext ->
                        DecodedVideoSequence(
                            bitmaps = ext.bitmaps,
                            fps = ext.fps,
                            width = ext.width,
                            height = ext.height,
                            clipDurationUs =
                                if (maxDurationMs > 0L) {
                                    maxDurationMs * 1000L
                                } else {
                                    (ext.bitmaps.size * 1_000_000L / ext.fps.toInt().coerceAtLeast(1))
                                },
                        )
                    }
            } ?: return@withContext null
        report(0f, 0.35f, 1f)

        report(0.35f, 0.75f, 0f)
        val recovered =
            when (quality) {
                RemovalQuality.ADVANCED ->
                    OpticalFlowRecoveryProcessor.recover(decoded.bitmaps, config, useOpticalFlow = true)
                RemovalQuality.STANDARD ->
                    TemporalMedianProcessor.apply(decoded.bitmaps, config)
            }
        report(0.35f, 0.75f, 0.5f)

        val blended =
            if (quality == RemovalQuality.ADVANCED) {
                recovered.mapIndexed { index, frame ->
                    report(0.35f, 0.75f, 0.5f + 0.5f * (index + 1) / recovered.size.coerceAtLeast(1))
                    FrameInpaintBlender.blendFrame(frame, config, quality)
                }
            } else {
                recovered
            }
        report(0.35f, 0.75f, 1f)

        val withTrial =
            if (isPremium) {
                blended
            } else {
                blended.map { frame ->
                    val badged = drawTrialBadge(frame)
                    if (badged !== frame) frame.recycle()
                    badged
                }
            }

        val videoDurationUs = VideoRemovalLimits.videoDurationUs(withTrial.size, decoded.fps)

        report(0.75f, 1f, 0f)
        val tempFile = File(context.cacheDir, "remove_${System.currentTimeMillis()}.mp4")
        var exported =
            when (quality) {
                RemovalQuality.ADVANCED ->
                    VideoExportMuxer.export(
                        context,
                        uri,
                        withTrial,
                        decoded.fps,
                        videoDurationUs,
                        tempFile,
                        includeAudio = true,
                    )
                RemovalQuality.STANDARD ->
                    SlideshowVideoEncoder.encode(withTrial, decoded.fps, tempFile)
            }
        if (!exported && quality == RemovalQuality.ADVANCED) {
            val silentFile = File(context.cacheDir, "remove_silent_${System.currentTimeMillis()}.mp4")
            if (SlideshowVideoEncoder.encode(withTrial, decoded.fps, silentFile)) {
                exported =
                    RemovalVideoRemuxer.muxVideoWithSourceAudio(
                        context,
                        silentFile,
                        uri,
                        tempFile,
                        sampling.clipDurationMs,
                    )
            }
            silentFile.delete()
            if (!exported) {
                exported = SlideshowVideoEncoder.encode(withTrial, decoded.fps, tempFile)
            }
        }
        withTrial.forEach { if (!it.isRecycled) it.recycle() }
        report(0.75f, 1f, 1f)
        if (!exported) {
            tempFile.delete()
            return@withContext null
        }
        saveVideoToGallery(context, tempFile)
    }

    private fun drawTrialBadge(source: Bitmap): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint =
            Paint().apply {
                color = Color.RED
                alpha = 150
                textSize = out.width * 0.035f
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
        canvas.drawText("Free Trial App", out.width - 20f, out.height - 25f, paint)
        return out
    }

    private fun saveVideoToGallery(context: Context, tempFile: File): Uri? {
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        val values =
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "wm_remove_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
        val dest = context.contentResolver.insert(collection, values) ?: return null
        return try {
            context.contentResolver.openOutputStream(dest)?.use { out ->
                tempFile.inputStream().use { it.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(dest, values, null, null)
            }
            tempFile.delete()
            dest
        } catch (e: Exception) {
            e.printStackTrace()
            tempFile.delete()
            null
        }
    }
}
