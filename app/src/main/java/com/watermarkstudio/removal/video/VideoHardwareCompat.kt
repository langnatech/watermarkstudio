package com.watermarkstudio.removal.video

import android.os.Build

/**
 * Detects devices where framework video decode (MediaMetadataRetriever / Exynos CCodec)
 * is prone to native SIGSEGV during export.
 */
object VideoHardwareCompat {

    /**
     * When true, prefer FFmpeg software frame extraction instead of
     * [android.media.MediaMetadataRetriever] (which still allocates Exynos HW decoders on Samsung).
     */
    fun prefersSoftwareFrameDecode(): Boolean {
        if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return false
        val hardware = Build.HARDWARE.orEmpty().lowercase()
        if (hardware.contains("exynos")) return true
        val board = Build.BOARD.orEmpty().lowercase()
        if (board.contains("exynos") || board.startsWith("s5e")) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val soc = Build.SOC_MODEL.orEmpty().lowercase()
            if (soc.startsWith("s5e") || soc.contains("exynos")) return true
        }
        return false
    }
}
