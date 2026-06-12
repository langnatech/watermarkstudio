package com.watermarkstudio.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Persists exported MediaStore URIs for the in-app library tab.
 * Files are written to system gallery via [MediaStoreSaveHelper]; this tracks URIs across sessions.
 */
object ProcessedMediaLibrary {

    private const val PREFS_NAME = "watermark_prefs"
    private const val KEY_URIS = "processed_media_uris"
    private const val MAX_ENTRIES = 20
    private const val URI_DELIMITER = "\n"

    fun load(context: Context): List<Uri> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_URIS, null) ?: return emptyList()
        return raw
            .split(URI_DELIMITER)
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) null else runCatching { Uri.parse(trimmed) }.getOrNull()
            }
            .filter { isReadable(context, it) }
            .takeLast(MAX_ENTRIES)
    }

    fun append(context: Context, newUris: List<Uri>): List<Uri> {
        if (newUris.isEmpty()) return load(context)
        val merged =
            (load(context) + newUris)
                .distinctBy { it.toString() }
                .takeLast(MAX_ENTRIES)
        persist(context, merged)
        return merged
    }

    fun replaceAll(context: Context, uris: List<Uri>) {
        persist(context, uris.takeLast(MAX_ENTRIES))
    }

    private fun persist(context: Context, uris: List<Uri>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URIS, uris.joinToString(URI_DELIMITER) { it.toString() })
            .apply()
    }

    private fun isReadable(context: Context, uri: Uri): Boolean =
        when (uri.scheme) {
            "file" ->
                try {
                    val decoded =
                        URLDecoder.decode(
                            uri.toString().removePrefix("file://"),
                            StandardCharsets.UTF_8.name(),
                        )
                    val file = File(decoded)
                    file.exists() && file.canRead()
                } catch (_: Exception) {
                    false
                }
            else ->
                try {
                    context.contentResolver.openInputStream(uri)?.use { true } ?: false
                } catch (_: Exception) {
                    false
                }
        }
}
