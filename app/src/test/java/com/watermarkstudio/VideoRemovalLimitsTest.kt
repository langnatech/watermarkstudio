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
    fun resolveSampling_reducesFpsWhenTooManyFrames() {
        val plan = VideoRemovalLimits.resolveSampling(15, 300_000L)
        assertTrue(plan.maxFrames <= VideoRemovalLimits.MAX_FRAME_COUNT)
        assertTrue(plan.targetFps <= 15)
    }
}
