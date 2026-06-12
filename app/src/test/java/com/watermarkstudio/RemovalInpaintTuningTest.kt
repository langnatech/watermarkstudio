package com.watermarkstudio

import com.watermarkstudio.removal.InpaintTarget
import com.watermarkstudio.removal.RemovalInpaintTuning
import com.watermarkstudio.removal.RemovalQuality
import com.watermarkstudio.util.RemovalRegion
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovalInpaintTuningTest {

    @Test
    fun contextMarginPx_scalesWithRegionSpan() {
        val small = RemovalRegion(10, 10, 60, 60)
        val large = RemovalRegion(0, 0, 400, 300)
        val smallMargin = RemovalInpaintTuning.contextMarginPx(small, InpaintTarget.IMAGE)
        val largeMargin = RemovalInpaintTuning.contextMarginPx(large, InpaintTarget.IMAGE)
        assertTrue(smallMargin in 48..160)
        assertTrue(largeMargin in 48..160)
        assertTrue(largeMargin > smallMargin)
    }

    @Test
    fun resolve_boostsLargeImageRegionIterations() {
        val region = RemovalRegion(0, 0, 320, 240)
        val standard =
            RemovalInpaintTuning.resolve(
                region = region,
                maskedPixelCount = 20_000,
                quality = RemovalQuality.STANDARD,
                target = InpaintTarget.IMAGE,
            )
        val compact =
            RemovalInpaintTuning.resolve(
                region = RemovalRegion(100, 100, 140, 130),
                maskedPixelCount = 500,
                quality = RemovalQuality.STANDARD,
                target = InpaintTarget.IMAGE,
            )
        assertTrue(standard.pmIterations > compact.pmIterations)
    }

    @Test
    fun resolve_keepsVideoIterationsLight() {
        val region = RemovalRegion(0, 0, 320, 240)
        val video =
            RemovalInpaintTuning.resolve(
                region = region,
                maskedPixelCount = 20_000,
                quality = RemovalQuality.ADVANCED,
                target = InpaintTarget.VIDEO,
            )
        val image =
            RemovalInpaintTuning.resolve(
                region = region,
                maskedPixelCount = 20_000,
                quality = RemovalQuality.ADVANCED,
                target = InpaintTarget.IMAGE,
            )
        assertTrue(image.pmIterations > video.pmIterations)
    }
}
