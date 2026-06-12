package com.watermarkstudio.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.watermarkstudio.model.MediaType
import com.watermarkstudio.removal.video.VideoFrameExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class MediaPreviewDimensions(
    val widthPx: Float,
    val heightPx: Float,
)

internal suspend fun loadMediaPreviewDimensions(
    context: Context,
    uri: Uri,
    mediaType: MediaType,
): MediaPreviewDimensions? =
    withContext(Dispatchers.IO) {
        when (mediaType) {
            MediaType.VIDEO -> {
                val dims = VideoFrameExtractor.loadVideoDimensions(context, uri) ?: return@withContext null
                MediaPreviewDimensions(dims.first.toFloat(), dims.second.toFloat())
            }
            MediaType.IMAGE -> {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    null
                } else {
                    MediaPreviewDimensions(options.outWidth.toFloat(), options.outHeight.toFloat())
                }
            }
        }
    }
