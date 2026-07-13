package com.watermarkstudio

import com.watermarkstudio.removal.video.TemporalVectorMedian
import org.junit.Assert.assertEquals
import org.junit.Test

class TemporalVectorMedianTest {

    @Test
    fun selectArgb_picksExistingSampleNotChannelMix() {
        // Per-channel median would invent (100,0,0) from mixing; vector median keeps a real sample.
        val samples =
            intArrayOf(
                0xFF640000.toInt(), // (100,0,0)
                0xFF006400.toInt(), // (0,100,0)
                0xFF640000.toInt(), // (100,0,0)
            )
        val out = TemporalVectorMedian.selectArgb(samples, count = 3, alpha = 0xFF)
        assertEquals(0xFF, (out ushr 24) and 0xFF)
        assertEquals(100, (out ushr 16) and 0xFF)
        assertEquals(0, (out ushr 8) and 0xFF)
        assertEquals(0, out and 0xFF)
    }
}
