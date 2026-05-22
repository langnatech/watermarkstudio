package com.watermarkstudio

import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.removal.mask.MaskGenerator
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskGeneratorTest {

    @Test
    fun region_hasPositiveArea() {
        val config =
            WatermarkConfig(
                type = WatermarkType.REMOVE,
                x = 50f,
                y = 50f,
                scale = 1f,
            )
        val region = MaskGenerator.regionForConfig(800, 600, config)
        assertTrue(region.width > 0)
        assertTrue(region.height > 0)
    }
}
