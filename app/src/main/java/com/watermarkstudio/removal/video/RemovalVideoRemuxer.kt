package com.watermarkstudio.removal.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Phase 3: remux processed silent video with source audio via Media3 Transformer.
 */
@UnstableApi
object RemovalVideoRemuxer {

    suspend fun muxVideoWithSourceAudio(
        context: Context,
        silentVideoFile: File,
        sourceUri: Uri,
        outputFile: File,
        maxDurationMs: Long,
    ): Boolean {
        if (!silentVideoFile.exists() || silentVideoFile.length() == 0L) return false
        val videoItem =
            EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(silentVideoFile))).build()
        val videoSequence = EditedMediaItemSequence.Builder(videoItem).build()
        val audioMediaBuilder = MediaItem.Builder().setUri(sourceUri)
        if (maxDurationMs > 0L) {
            audioMediaBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setEndPositionMs(maxDurationMs)
                    .build(),
            )
        }
        val audioItem =
            EditedMediaItem.Builder(audioMediaBuilder.build())
                .setRemoveVideo(true)
                .build()
        val audioSequence = EditedMediaItemSequence.Builder(audioItem).build()
        val composition = Composition.Builder(videoSequence, audioSequence).build()
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { cont ->
                val transformer =
                    Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .addListener(
                            object : Transformer.Listener {
                                override fun onCompleted(
                                    composition: Composition,
                                    exportResult: androidx.media3.transformer.ExportResult,
                                ) {
                                    cont.resume(outputFile.exists() && outputFile.length() > 0L)
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: androidx.media3.transformer.ExportResult,
                                    exportException: ExportException,
                                ) {
                                    exportException.printStackTrace()
                                    cont.resume(false)
                                }
                            },
                        )
                        .build()
                cont.invokeOnCancellation { transformer.cancel() }
                transformer.start(composition, outputFile.absolutePath)
            }
        }
    }
}
