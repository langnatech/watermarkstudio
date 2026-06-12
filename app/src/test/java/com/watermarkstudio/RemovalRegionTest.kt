package com.watermarkstudio

import com.watermarkstudio.model.RemovalStroke
import com.watermarkstudio.model.RemovalStrokePoint
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.util.RemovalRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovalRegionTest {

    @Test
    fun fromConfig_emptyWhenNoStrokes() {
        val config =
            WatermarkConfig(
                type = WatermarkType.REMOVE,
                x = 50f,
                y = 50f,
                scale = 2f,
            )
        val region = RemovalRegion.fromConfig(1000, 800, config)
        assertEquals(0, region.width)
        assertEquals(0, region.height)
    }

    @Test
    fun fromStrokes_usesStrokeBoundsAndRadiusPadding() {
        val config =
            WatermarkConfig(
                type = WatermarkType.REMOVE,
                removalStrokes =
                    listOf(
                        RemovalStroke(
                            points =
                                listOf(
                                    RemovalStrokePoint(25f, 30f),
                                    RemovalStrokePoint(40f, 45f),
                                ),
                            radiusPct = 2f,
                        ),
                    ),
            )

        val region = RemovalRegion.fromConfig(1000, 800, config)

        assertTrue(region.left < 250)
        assertTrue(region.top < 240)
        assertTrue(region.right > 400)
        assertTrue(region.bottom > 360)
    }

    @Test
    fun fromStrokes_clampsToBitmapBounds() {
        val config =
            WatermarkConfig(
                type = WatermarkType.REMOVE,
                removalStrokes =
                    listOf(
                        RemovalStroke(
                            points =
                                listOf(
                                    RemovalStrokePoint(0f, 0f),
                                    RemovalStrokePoint(100f, 100f),
                                ),
                            radiusPct = 5f,
                        ),
                    ),
            )

        val region = RemovalRegion.fromConfig(320, 180, config)

        assertTrue(region.left >= 0)
        assertTrue(region.top >= 0)
        assertTrue(region.right <= 320)
        assertTrue(region.bottom <= 180)
    }
}
