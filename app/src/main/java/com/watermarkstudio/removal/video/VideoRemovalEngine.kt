package com.watermarkstudio.removal.video

import android.content.Context
import android.net.Uri
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.removal.OpenCvBootstrap
import com.watermarkstudio.removal.RemovalCapability
import com.watermarkstudio.removal.RemovalInputValidator
import com.watermarkstudio.removal.RemovalProgress
import com.watermarkstudio.removal.RemovalQuality
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

object VideoRemovalEngine {

    private const val TAG = "VideoRemovalEngine"

    /** OpenCV native code is not safe under parallel calls; serialize all video removal work. */
    private val removalDispatcher =
        Executors
            .newSingleThreadExecutor { runnable ->
                Thread(runnable, "wm-video-removal").apply { isDaemon = true }
            }
            .asCoroutineDispatcher()

    suspend fun removeRegion(
        context: Context,
        uri: Uri,
        config: WatermarkConfig,
        maxDurationMs: Long,
        maxDimension: Int,
        isPremium: Boolean,
        quality: RemovalQuality,
        progress: RemovalProgress? = null,
    ): Uri? =
        withContext(removalDispatcher) {
            try {
                removeRegionImpl(
                    context,
                    uri,
                    config,
                    maxDurationMs,
                    maxDimension,
                    isPremium,
                    quality,
                    progress,
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Video removal crashed", t)
                null
            }
        }

    private suspend fun removeRegionImpl(
        context: Context,
        uri: Uri,
        config: WatermarkConfig,
        maxDurationMs: Long,
        maxDimension: Int,
        isPremium: Boolean,
        quality: RemovalQuality,
        progress: RemovalProgress?,
    ): Uri? {
        if (!RemovalInputValidator.hasPaintedMask(config)) return null
        if (!OpenCvBootstrap.ensureLoaded(context)) return null
        if (!RemovalCapability.supportsVideoRemoval(context)) return null
        if (
            VideoHardwareCompat.prefersSoftwareFrameDecode() &&
            !FfmpegRemuxHelper.isAvailable()
        ) {
            Log.e(TAG, "FFmpeg-kit required for video removal on Exynos")
            return null
        }

        val clipMs =
            if (maxDurationMs > 0L) {
                maxDurationMs
            } else {
                VideoRemovalLimits.PRO_MAX_DURATION_MS
            }
        val sampling = VideoRemovalLimits.resolveSampling(context, uri, clipMs, isPremium)

        fun report(stageStart: Float, stageEnd: Float, fraction: Float) {
            progress?.report(stageStart + (stageEnd - stageStart) * fraction.coerceIn(0f, 1f))
        }

        report(0f, 0.35f, 0f)
        val silentFile = File(context.cacheDir, "remove_stream_${System.currentTimeMillis()}.mp4")
        val streamResult =
            StreamingVideoRemovalEngine.process(
                context,
                uri,
                config,
                sampling,
                maxDimension,
                quality,
                silentFile,
                progress,
            )
        report(0f, 0.75f, 1f)

        if (streamResult == null) {
            silentFile.delete()
            // Exynos uses FFmpeg frame source; batch fallback would duplicate work and re-hit SAF limits.
            if (VideoHardwareCompat.prefersSoftwareFrameDecode()) {
                Log.e(TAG, "Streaming video removal failed on Exynos (FFmpeg path)")
                return null
            }
            return removeRegionBatch(
                context,
                uri,
                config,
                sampling,
                maxDimension,
                isPremium,
                quality,
                progress,
            )
        }

        val tempFile = File(context.cacheDir, "remove_${System.currentTimeMillis()}.mp4")
        var exported =
            exportWithSourceAudio(
                context,
                uri,
                streamResult.silentFile,
                sampling.clipDurationMs,
                tempFile,
            )
        if (!exported) {
            silentFile.delete()
            tempFile.delete()
            return null
        }
        silentFile.delete()
        report(1f, 1f, 1f)
        return saveVideoToGallery(context, tempFile)
    }

    private suspend fun exportWithSourceAudio(
        context: Context,
        sourceUri: Uri,
        silentVideoFile: File,
        clipDurationMs: Long,
        outputFile: File,
    ): Boolean {
        if (
            RemovalAudioExporter.muxWithSourceAudio(
                context,
                silentVideoFile,
                sourceUri,
                outputFile,
                clipDurationMs,
            )
        ) {
            return true
        }
        return RemovalAudioExporter.copySilentVideo(silentVideoFile, outputFile)
    }

    /** Fallback batch path when streaming decode fails. */
    private suspend fun removeRegionBatch(
        context: Context,
        uri: Uri,
        config: WatermarkConfig,
        sampling: VideoRemovalLimits.SamplingPlan,
        maxDimension: Int,
        isPremium: Boolean,
        quality: RemovalQuality,
        progress: RemovalProgress?,
    ): Uri? {
        fun report(stageStart: Float, stageEnd: Float, fraction: Float) {
            progress?.report(stageStart + (stageEnd - stageStart) * fraction.coerceIn(0f, 1f))
        }
        val decoded =
            VideoFrameExtractor.extract(
                context,
                uri,
                sampling.clipDurationMs,
                maxDimension,
                targetFps = sampling.targetFps,
                maxFrames = sampling.maxFrames,
            )?.let { ext ->
                DecodedVideoSequence(
                    bitmaps = ext.bitmaps,
                    fps = ext.fps,
                    width = ext.width,
                    height = ext.height,
                    clipDurationUs = sampling.clipDurationMs * 1000L,
                )
            } ?: return null

        val maskCache = FrameInpaintBlender.prepareMask(decoded.width, decoded.height, config)
        val blended =
            try {
                decoded.bitmaps.map { frame ->
                    FrameInpaintBlender.blendFrame(frame, config, quality, maskCache)
                }
            } finally {
                maskCache.release()
            }
        val videoDurationUs = VideoRemovalLimits.videoDurationUs(blended.size, decoded.fps)
        val tempFile = File(context.cacheDir, "remove_${System.currentTimeMillis()}.mp4")
        val silentFile = File(context.cacheDir, "remove_silent_${System.currentTimeMillis()}.mp4")
        var exported = SlideshowVideoEncoder.encode(blended, decoded.fps, silentFile)
        if (exported) {
            exported =
                exportWithSourceAudio(
                    context,
                    uri,
                    silentFile,
                    sampling.clipDurationMs,
                    tempFile,
                )
        }
        silentFile.delete()
        if (!exported) {
            exported = SlideshowVideoEncoder.encode(blended, decoded.fps, tempFile)
        }
        blended.forEach { if (!it.isRecycled) it.recycle() }
        if (!exported) {
            tempFile.delete()
            return null
        }
        return saveVideoToGallery(context, tempFile)
    }

    private fun saveVideoToGallery(context: Context, tempFile: File): Uri? {
        return try {
            com.watermarkstudio.util.MediaStoreSaveHelper.saveMp4FromFile(
                context,
                tempFile,
                "wm_remove_${System.currentTimeMillis()}.mp4",
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            tempFile.delete()
        }
    }
}
