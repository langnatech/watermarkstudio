package com.watermarkstudio.removal.video

import android.graphics.Bitmap

/** Pulls decoded frames one at a time; caller must not retain more than a small neighbor window. */
interface VideoFrameSource : AutoCloseable {
    val fps: Float
    val width: Int
    val height: Int

    /** Returns the next frame, or null when finished. */
    fun nextFrame(): Bitmap?
}
