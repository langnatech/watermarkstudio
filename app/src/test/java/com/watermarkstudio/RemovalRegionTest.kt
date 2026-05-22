package com.watermarkstudio

import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.util.RemovalRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovalRegionTest {

    @Test
    fun fromConfig_clampsToBitmapBounds() {
        val config = WatermarkConfig(
            type = WatermarkType.REMOVE,
            x = 0f,
            y = 0f,
            scale = 2f,
            opacity = 1f,
        )
        val region = RemovalRegion.fromConfig(1000, 800, config)
        assertTrue(region.left >= 0)
        assertTrue(region.top >= 0)
        assertTrue(region.right <= 1000)
        assertTrue(region.bottom <= 800)
        assertTrue(region.width > 0)
        assertTrue(region.height > 0)
    }

    @Test
    fun fromConfig_centeredRegion() {
        val config = WatermarkConfig(
            type = WatermarkType.REMOVE,
            x = 50f,
            y = 50f,
            scale = 1f,
            opacity = 1f,
        )
        val region = RemovalRegion.fromConfig(200, 200, config)
        val centerX = (region.left + region.right) / 2
        val centerY = (region.top + region.bottom) / 2
        assertTrue(kotlin.math.abs(centerX - 100) <= 15)
        assertTrue(kotlin.math.abs(centerY - 100) <= 15)
    }
}
