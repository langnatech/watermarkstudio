package com.watermarkstudio.removal

import android.content.Context

enum class RemovalQuality {
    /** Brush mask + PatchMatch with conservative iterations; video keeps source audio. */
    STANDARD,
    /** Brush mask + PatchMatch with more iterations; video adds lightweight optical-flow recovery. */
    ADVANCED,
}

object RemovalQualityResolver {

    fun resolve(isPremium: Boolean, @Suppress("UNUSED_PARAMETER") context: Context): RemovalQuality {
        return if (isPremium) RemovalQuality.ADVANCED else RemovalQuality.STANDARD
    }
}
