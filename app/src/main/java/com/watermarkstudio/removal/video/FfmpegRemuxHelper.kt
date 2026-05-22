package com.watermarkstudio.removal.video

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Phase 3c: remux silent H.264 with source audio via FFmpeg-kit when Media3 mux fails
 * (exotic codecs, odd container layouts).
 */
object FfmpegRemuxHelper {

    fun isAvailable(): Boolean =
        try {
            FFmpegKitConfig.getVersion()
            true
        } catch (_: Throwable) {
            false
        }

    suspend fun muxVideoWithSourceAudio(
        context: Context,
        silentVideoFile: File,
        sourceUri: Uri,
        outputFile: File,
        maxDurationMs: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (!isAvailable()) return@withContext false
            if (!silentVideoFile.exists() || silentVideoFile.length() == 0L) return@withContext false
            val videoPath = silentVideoFile.absolutePath
            val audioInput =
                try {
                    FFmpegKitConfig.getSafParameterForRead(context, sourceUri)
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@withContext false
                }
            val outPath = outputFile.absolutePath
            val durationArg =
                if (maxDurationMs > 0L) {
                    " -t ${maxDurationMs / 1000.0}"
                } else {
                    ""
                }
            val command =
                "-y -i \"$videoPath\" -i $audioInput -map 0:v:0 -map 1:a? -c copy -shortest$durationArg \"$outPath\""
            val session = FFmpegKit.execute(command)
            ReturnCode.isSuccess(session.returnCode) &&
                outputFile.exists() &&
                outputFile.length() > 0L
        }
}
