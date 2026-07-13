package com.watermarkstudio.removal.video

/**
 * Selects a temporally stable RGB sample that exists in the window (vector median),
 * avoiding false colors from independent per-channel medians.
 */
internal object TemporalVectorMedian {

    fun selectArgb(samples: IntArray, count: Int, alpha: Int): Int {
        require(count > 0 && count <= samples.size)
        if (count == 1) {
            return (alpha shl 24) or (samples[0] and 0x00FFFFFF)
        }
        var bestIndex = 0
        var bestScore = Long.MAX_VALUE
        for (i in 0 until count) {
            val ci = samples[i]
            val ri = (ci ushr 16) and 0xFF
            val gi = (ci ushr 8) and 0xFF
            val bi = ci and 0xFF
            var score = 0L
            for (j in 0 until count) {
                val cj = samples[j]
                score += kotlin.math.abs(ri - ((cj ushr 16) and 0xFF))
                score += kotlin.math.abs(gi - ((cj ushr 8) and 0xFF))
                score += kotlin.math.abs(bi - (cj and 0xFF))
            }
            if (score < bestScore) {
                bestScore = score
                bestIndex = i
            }
        }
        return (alpha shl 24) or (samples[bestIndex] and 0x00FFFFFF)
    }
}
