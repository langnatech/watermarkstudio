package com.watermarkstudio.removal

object RemovalExportLimits {
    const val PREVIEW_MAX_DIM = 720
    const val FREE_IMAGE_MAX_DIM = 1024
    const val PREMIUM_IMAGE_MAX_DIM = 2500
    const val FREE_VIDEO_MAX_DIM = 720
    const val PREMIUM_VIDEO_MAX_DIM = 1080

    fun imageExportMaxDim(isPremium: Boolean): Int =
        if (isPremium) PREMIUM_IMAGE_MAX_DIM else FREE_IMAGE_MAX_DIM
}
