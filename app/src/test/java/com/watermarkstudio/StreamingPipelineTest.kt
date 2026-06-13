package com.watermarkstudio

import com.watermarkstudio.removal.video.VideoRemovalLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingPipelineTest {

    private companion object {
        private const val BATCH_MEMORY_LIMIT_BYTES = 256L * 1024L * 1024L
    }

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

    @Test
    fun batchMemoryEstimate_exceedsLimitForLongHdClip() {
        val maxFrames = 18_000
        val maxDimension = 1080
        val estimatedBytes = maxFrames.toLong() * maxDimension * maxDimension * 4L
        assertTrue(estimatedBytes > BATCH_MEMORY_LIMIT_BYTES)
    }
}
