package com.watermarkstudio

import com.watermarkstudio.removal.video.VideoRemovalLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRemovalLimitsTest {

    @Test
    fun clamp_freeUser_cappedAt15s() {
        assertEquals(15_000L, VideoRemovalLimits.clampClipDurationMs(60_000L, false))
    }

    @Test
    fun clamp_proUser_cappedAt300s() {
        assertEquals(300_000L, VideoRemovalLimits.clampClipDurationMs(600_000L, true))
    }

    @Test
    fun resolveSamplingPro_keeps60FpsForLongClip() {
        val plan = VideoRemovalLimits.resolveSamplingPro(60, 300_000L, 60f)
        assertEquals(60, plan.targetFps)
        assertEquals(18_000, plan.maxFrames)
    }

    @Test
    fun resolveSamplingFree_keeps60FpsForFull15s() {
        val plan = VideoRemovalLimits.resolveSamplingFree(60, 15_000L, 60f)
        assertEquals(60, plan.targetFps)
        assertEquals(900, plan.maxFrames)
        assertEquals(15_000L, plan.clipDurationMs)
    }

    @Test
    fun resolveSamplingFree_clipsDurationTo15s() {
        val plan = VideoRemovalLimits.resolveSamplingFree(60, 60_000L, 60f)
        assertEquals(15_000L, plan.clipDurationMs)
        assertEquals(60, plan.targetFps)
        assertEquals(900, plan.maxFrames)
    }
}
