package com.watermarkstudio.removal.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * FFmpeg software frame extraction (no Android MediaCodec / Exynos CCodec).
 *
 * SAF virtual paths (`saf:N.mp4`) expire between FFmpeg sessions; always materialize
 * the source into a local cache file for multi-batch export.
 */
object FfmpegFrameExtractHelper {

    private const val TAG = "FfmpegFrameExtract"

    const val BATCH_SIZE = 48

    /**
     * Copies [uri] into [destDir]/input_src.mp4 so FFmpeg can reopen it for every batch.
     */
    fun materializeToCacheFile(
        context: Context,
        uri: Uri,
        destDir: File,
    ): File {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw IllegalStateException("Cannot create FFmpeg work directory")
        }
        val dest = File(destDir, "input_src.mp4")
        if (dest.exists()) dest.delete()
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open video URI for copy")
        if (!dest.exists() || dest.length() <= 0L) {
            dest.delete()
            throw IllegalStateException("Video copy to cache is empty")
        }
        Log.i(TAG, "Materialized video for FFmpeg (${dest.length()} bytes)")
        return dest
    }

    fun quotedAbsolutePath(file: File): String = "\"${file.absolutePath}\""

    fun probeDurationMs(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun extractFrameBatch(
        inputFile: File,
        batchDir: File,
        startFrameIndex: Int,
        batchFrameCount: Int,
        fps: Int,
        maxDimension: Int,
        clipDurationMs: Long,
    ): List<File> {
        require(batchFrameCount > 0) { "batchFrameCount must be positive" }
        val fpsFilter = VideoRemovalLimits.clampTargetFps(fps)
        val clipEndSec = clipDurationMs.coerceAtLeast(1L) / 1000.0
        val startSec = startFrameIndex.toDouble() / fpsFilter
        if (startSec >= clipEndSec - 0.001) {
            return emptyList()
        }
        val remainingSec = clipEndSec - startSec
        val durationSec = minOf(batchFrameCount.toDouble() / fpsFilter, remainingSec)
        val framesThisBatch =
            minOf(
                batchFrameCount,
                (durationSec * fpsFilter).toInt().coerceAtLeast(0),
            )
        if (framesThisBatch <= 0) {
            return emptyList()
        }
        if (batchDir.exists()) {
            batchDir.listFiles()?.forEach { it.delete() }
        } else {
            batchDir.mkdirs()
        }
        val scale =
            if (maxDimension > 0) {
                ",scale='min(iw\\,$maxDimension)':-2:flags=lanczos"
            } else {
                ""
            }
        val input = quotedAbsolutePath(inputFile)
        val pattern = File(batchDir, "frame_%05d.png").absolutePath
        val command =
            "-y -hwaccel none -threads 2 -ss $startSec -i $input -t $durationSec " +
                "-vf fps=$fpsFilter$scale -frames:v $framesThisBatch " +
                "-start_number ${startFrameIndex + 1} -fps_mode passthrough \"$pattern\""
        val session = FFmpegKit.execute(command)
        if (!ReturnCode.isSuccess(session.returnCode)) {
            batchDir.deleteRecursively()
            throw IllegalStateException(
                "FFmpeg batch extract failed: ${session.failStackTrace ?: session.output}",
            )
        }
        return batchDir
            .listFiles { f -> f.isFile && f.name.endsWith(".png", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
            .also { files ->
                if (files.isEmpty()) {
                    batchDir.deleteRecursively()
                    Log.i(TAG, "FFmpeg batch at ${startSec}s produced no frames (end of clip)")
                }
            }
    }

    fun extractSingleFrameBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int,
        timeUs: Long = 0L,
    ): Bitmap? {
        if (!FfmpegRemuxHelper.isAvailable()) return null
        val workDir = File(context.cacheDir, "ff_preview_${System.nanoTime()}")
        if (!workDir.mkdirs()) return null
        return try {
            val inputFile = materializeToCacheFile(context, uri, workDir)
            val timeSec = timeUs.coerceAtLeast(0L) / 1_000_000.0
            val out = File(workDir, "frame.png")
            val vf =
                if (maxDimension > 0) {
                    "scale='min(iw\\,$maxDimension)':-2:flags=lanczos"
                } else {
                    "null"
                }
            val command =
                "-y -hwaccel none -threads 2 -ss $timeSec -i ${quotedAbsolutePath(inputFile)} " +
                    "-frames:v 1 -vf \"$vf\" \"${out.absolutePath}\""
            val session = FFmpegKit.execute(command)
            if (!ReturnCode.isSuccess(session.returnCode) || !out.exists()) return null
            val decoded = BitmapFactory.decodeFile(out.absolutePath) ?: return null
            VideoFrameUtils.downscaleIfNeeded(decoded, maxDimension)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            workDir.deleteRecursively()
        }
    }
}
