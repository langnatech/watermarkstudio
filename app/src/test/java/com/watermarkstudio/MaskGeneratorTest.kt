package com.watermarkstudio

import com.watermarkstudio.model.RemovalStroke
import com.watermarkstudio.model.RemovalStrokePoint
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.removal.mask.MaskGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskGeneratorTest {

    @Test
    fun regionForConfig_emptyWhenNoStrokes() {
        val config = WatermarkConfig(type = WatermarkType.REMOVE)
        val region = MaskGenerator.regionForConfig(800, 600, config)
        assertEquals(0, region.width)
        assertEquals(0, region.height)
    }

    @Test
    fun regionForConfig_wrapsStrokeBounds() {
        val config =
            WatermarkConfig(
                type = WatermarkType.REMOVE,
                removalStrokes =
                    listOf(
                        RemovalStroke(
                            points =
                                listOf(
                                    RemovalStrokePoint(40f, 50f),
                                    RemovalStrokePoint(60f, 55f),
                                ),
                            radiusPct = 2f,
                        ),
                    ),
            )
        val region = MaskGenerator.regionForConfig(1000, 800, config)
        assertTrue(region.left < 400)
        assertTrue(region.right > 600)
        assertTrue(region.top < 400)
        assertTrue(region.bottom > 440)
    }
}
