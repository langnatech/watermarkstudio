package com.watermarkstudio.removal

import android.content.Context

enum class RemovalQuality {
    /** Brush mask + temporal median prefill + PatchMatch refine; video keeps source audio. */
    STANDARD,
    /** 7-frame temporal fusion + optical-flow prefill + PatchMatch refine; video keeps source audio. */
    ADVANCED,
}

object RemovalQualityResolver {

    fun resolve(isPremium: Boolean, @Suppress("UNUSED_PARAMETER") context: Context): RemovalQuality {
        return if (isPremium) RemovalQuality.ADVANCED else RemovalQuality.STANDARD
    }
}
