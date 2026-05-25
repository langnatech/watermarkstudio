package com.watermarkstudio.removal

import android.content.Context

enum class RemovalQuality {
    /** TELEA inpaint; video: optical-flow ROI + TELEA per frame + source audio mux. */
    STANDARD,
    /** NS inpaint + seamlessClone; video: pyramid LK flow + NS/seamless per frame + source audio mux. */
    ADVANCED,
}

object RemovalQualityResolver {

    fun resolve(isPremium: Boolean, @Suppress("UNUSED_PARAMETER") context: Context): RemovalQuality {
        return if (isPremium) RemovalQuality.ADVANCED else RemovalQuality.STANDARD
    }
}
