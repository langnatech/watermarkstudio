package com.watermarkstudio

import com.watermarkstudio.removal.video.VideoRemovalLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingPipelineTest {

    @Test
    fun sampling_capsFrames_forLongClip() {
        val plan = VideoRemovalLimits.resolveSampling(15, 300_000L)
        assertTrue(plan.maxFrames <= VideoRemovalLimits.MAX_FRAME_COUNT)
        assertTrue(plan.targetFps <= 15)
    }

    @Test
    fun videoDurationUs_matchesFrameCount() {
        val us = VideoRemovalLimits.videoDurationUs(30, 15f)
        assertEquals(2_000_000L, us)
    }
}
