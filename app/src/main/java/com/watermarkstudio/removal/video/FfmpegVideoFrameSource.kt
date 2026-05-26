package com.watermarkstudio.removal.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlin.math.min

/**
 * Pull-based frame source: FFmpeg software decode in batches (bounded disk / memory).
 */
class FfmpegVideoFrameSource(
    context: Context,
    uri: Uri,
    clipDurationMs: Long,
    private val maxDimension: Int,
    targetFps: Int,
    private val maxFrames: Int,
) : VideoFrameSource {

    private val fpsInt = VideoRemovalLimits.clampTargetFps(targetFps)
    private val inputFile: File
    private val workDir: File
    private val boundedClipDurationMs: Long
    private val effectiveMaxFrames: Int
    private var emitted = 0
    private var batchFiles: List<File> = emptyList()
    private var batchIndex = 0
    private var currentBatchDir: File? = null
    private var _width = 0
    private var _height = 0

    override val fps: Float = fpsInt.toFloat()
    override val width: Int get() = _width
    override val height: Int get() = _height

    init {
        if (!FfmpegRemuxHelper.isAvailable()) {
            throw IllegalStateException("FFmpeg-kit not available")
        }
        if (clipDurationMs <= 0L || maxFrames <= 0) {
            throw IllegalArgumentException("Invalid clip or frame budget")
        }
        workDir =
            File(context.cacheDir, "ff_frames_${System.nanoTime()}").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }
        inputFile = FfmpegFrameExtractHelper.materializeToCacheFile(context, uri, workDir)
        val probedMs = FfmpegFrameExtractHelper.probeDurationMs(inputFile) ?: clipDurationMs
        boundedClipDurationMs = minOf(clipDurationMs, probedMs).coerceAtLeast(1L)
        effectiveMaxFrames =
            VideoRemovalLimits.capFrameCount(maxFrames, boundedClipDurationMs, fpsInt)
    }

    override fun nextFrame(): Bitmap? {
        if (emitted >= effectiveMaxFrames) return null
        if (batchIndex >= batchFiles.size) {
            purgeCurrentBatch()
            if (emitted >= effectiveMaxFrames) return null
            val batchSize = min(FfmpegFrameExtractHelper.BATCH_SIZE, effectiveMaxFrames - emitted)
            val batchDir = File(workDir, "batch_$emitted")
            batchFiles =
                FfmpegFrameExtractHelper.extractFrameBatch(
                    inputFile,
                    batchDir,
                    emitted,
                    batchSize,
                    fpsInt,
                    maxDimension,
                    boundedClipDurationMs,
                )
            currentBatchDir = batchDir
            batchIndex = 0
            if (batchFiles.isEmpty()) return null
        }
        val path = batchFiles[batchIndex++].absolutePath
        val decoded = BitmapFactory.decodeFile(path) ?: return nextFrame()
        val normalized = VideoFrameUtils.ensureDimensions(decoded, _width, _height)
        if (normalized !== decoded) decoded.recycle()
        if (_width == 0) {
            _width = normalized.width
            _height = normalized.height
        }
        emitted++
        return normalized
    }

    override fun close() {
        purgeCurrentBatch()
        try {
            workDir.deleteRecursively()
        } catch (_: Exception) {
        }
    }

    private fun purgeCurrentBatch() {
        batchFiles = emptyList()
        batchIndex = 0
        currentBatchDir?.let { dir ->
            try {
                dir.deleteRecursively()
            } catch (_: Exception) {
            }
        }
        currentBatchDir = null
    }
}
