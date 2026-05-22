package com.watermarkstudio.removal

import android.content.Context

enum class RemovalQuality {
    /** TELEA inpaint (image); temporal median JNI (video); slideshow export without audio. */
    STANDARD,
    /** NS inpaint + seamlessClone (image); optical flow + inpaint/clone + mux with source audio (video). */
    ADVANCED,
}

object RemovalQualityResolver {

    fun resolve(isPremium: Boolean, @Suppress("UNUSED_PARAMETER") context: Context): RemovalQuality {
        return if (isPremium) RemovalQuality.ADVANCED else RemovalQuality.STANDARD
    }
}
