package com.watermarkstudio.removal.video

import android.graphics.Bitmap

data class DecodedVideoSequence(
    val bitmaps: List<Bitmap>,
    val fps: Float,
    val width: Int,
    val height: Int,
    val clipDurationUs: Long,
)
