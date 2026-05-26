package com.watermarkstudio.removal.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaCodecList
import android.os.Build
import android.util.Log

/**
 * Picks an H.264 encoder; on Exynos devices avoids Samsung HW encoders when possible.
 */
object VideoAvcCodecSelector {

    private const val TAG = "VideoAvcCodecSelector"

    fun createAvcEncoder(): MediaCodec {
        if (VideoHardwareCompat.prefersSoftwareFrameDecode()) {
            findPreferredAvcEncoderName()?.let { name ->
                Log.i(TAG, "Using AVC encoder: $name")
                return MediaCodec.createByCodecName(name)
            }
            Log.w(TAG, "No non-Exynos AVC encoder found; using system default")
        }
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    }

    private fun findPreferredAvcEncoderName(): String? {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        var softwareCandidate: String? = null
        var googleCandidate: String? = null
        for (info in list.codecInfos) {
            if (!info.isEncoder) continue
            val supportsAvc =
                info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) }
            if (!supportsAvc) continue
            val name = info.name
            if (name.contains("exynos", ignoreCase = true)) continue
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !info.isHardwareAccelerated) {
                softwareCandidate = name
                break
            }
            if (
                name.contains("c2.android.avc", ignoreCase = true) ||
                    name.contains("omx.google", ignoreCase = true) ||
                    name.contains("android.avc", ignoreCase = true)
            ) {
                googleCandidate = name
            }
        }
        return softwareCandidate ?: googleCandidate
    }
}
