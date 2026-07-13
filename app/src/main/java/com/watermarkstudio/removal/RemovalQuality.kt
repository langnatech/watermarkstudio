package com.watermarkstudio.removal

import android.content.Context

enum class RemovalQuality {
    /** Brush mask + temporal median prefill + PatchMatch refine; video keeps source audio. */
    STANDARD,
    /** Optical-flow prefill (prev+next) with median fallback + PatchMatch refine; video keeps source audio. */
    ADVANCED,
}

object RemovalQualityResolver {

    fun resolve(isPremium: Boolean, @Suppress("UNUSED_PARAMETER") context: Context): RemovalQuality {
        return if (isPremium) RemovalQuality.ADVANCED else RemovalQuality.STANDARD
    }
}
