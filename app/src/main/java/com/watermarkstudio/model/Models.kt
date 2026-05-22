package com.watermarkstudio.model

import android.net.Uri

enum class MediaType {
    IMAGE, VIDEO
}

data class MediaItem(
    val uri: Uri,
    val type: MediaType,
    val name: String,
    val size: Long = 0,
    val duration: Long = 0 // For videos
)

enum class WatermarkType {
    TEXT, IMAGE, REMOVE
}

data class WatermarkConfig(
    val type: WatermarkType,
    val text: String = "",
    val imageUri: Uri? = null,
    val opacity: Float = 0.5f,
    val x: Float = 50f, // Percentage 0-100
    val y: Float = 50f, // Percentage 0-100
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val color: Int = -1 // Color.WHITE in Int
)
