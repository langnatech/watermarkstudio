package com.watermarkstudio.removal.video

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Ordered audio mux fallbacks for ADVANCED video export.
 */
object RemovalAudioExporter {

    suspend fun muxWithSourceAudio(
        context: Context,
        silentVideoFile: File,
        sourceUri: Uri,
        outputFile: File,
        clipDurationMs: Long,
    ): Boolean {
        if (
            RemovalVideoRemuxer.muxVideoWithSourceAudio(
                context,
                silentVideoFile,
                sourceUri,
                outputFile,
                clipDurationMs,
            )
        ) {
            return true
        }
        if (
            FfmpegRemuxHelper.muxVideoWithSourceAudio(
                context,
                silentVideoFile,
                sourceUri,
                outputFile,
                clipDurationMs,
            )
        ) {
            return true
        }
        return false
    }

    fun copySilentVideo(silentVideoFile: File, outputFile: File): Boolean =
        try {
            silentVideoFile.copyTo(outputFile, overwrite = true)
            outputFile.exists() && outputFile.length() > 0L
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
}
