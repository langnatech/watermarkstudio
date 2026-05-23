package com.watermarkstudio.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

/**
 * Shared MediaStore write helpers: only return [Uri] when bytes were written successfully.
 */
object MediaStoreSaveHelper {

    fun saveJpegBitmap(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
        val uri = context.contentResolver.insert(collection, values) ?: return null
        var success = false
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                success = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            success = false
        } finally {
            finalizePending(context, uri, values, success)
        }
        return if (success) uri else null
    }

    fun saveMp4FromFile(context: Context, tempFile: File, displayName: String): Uri? {
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        val values =
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
        val uri = context.contentResolver.insert(collection, values) ?: return null
        var success = false
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                tempFile.inputStream().use { input ->
                    input.copyTo(out)
                }
                success = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            success = false
        } finally {
            finalizePending(context, uri, values, success)
        }
        return if (success) uri else null
    }

    private fun finalizePending(
        context: Context,
        uri: Uri,
        values: ContentValues,
        success: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, if (success) 0 else 1)
            context.contentResolver.update(uri, values, null, null)
        }
    }
}
