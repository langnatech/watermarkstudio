package com.watermarkstudio

import com.watermarkstudio.model.RemovalStroke
import com.watermarkstudio.model.RemovalStrokePoint
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.removal.RemovalInputValidator
import com.watermarkstudio.removal.mask.MaskGenerator
import com.watermarkstudio.util.RemovalRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun regionForConfig_ignoresEraserOnlyStrokes() {
        val config =
            WatermarkConfig(
                type = WatermarkType.REMOVE,
                removalStrokes =
                    listOf(
                        RemovalStroke(
                            points = listOf(RemovalStrokePoint(50f, 50f)),
                            radiusPct = 3f,
                            isEraser = true,
                        ),
                    ),
            )
        val region = RemovalRegion.fromConfig(800, 600, config)
        assertEquals(0, region.width)
        assertEquals(0, region.height)
    }

    @Test
    fun toInpaintCoreBytes_thresholdsSoftValues() {
        val soft = byteArrayOf(0, 20, 40, 200.toByte())
        val hard = MaskGenerator.toInpaintCoreBytes(soft)
        assertEquals(0, hard[0].toInt() and 0xFF)
        assertEquals(0, hard[1].toInt() and 0xFF)
        assertEquals(255, hard[2].toInt() and 0xFF)
        assertEquals(255, hard[3].toInt() and 0xFF)
    }

    @Test
    fun hasPaintedMask_requiresNonEraserStroke() {
        val eraserOnly =
            WatermarkConfig(
                type = WatermarkType.REMOVE,
                removalStrokes =
                    listOf(
                        RemovalStroke(
                            points = listOf(RemovalStrokePoint(10f, 10f)),
                            radiusPct = 2f,
                            isEraser = true,
                        ),
                    ),
            )
        assertFalse(RemovalInputValidator.hasPaintedMask(eraserOnly))
        val painted =
            eraserOnly.copy(
                removalStrokes =
                    listOf(
                        RemovalStroke(
                            points = listOf(RemovalStrokePoint(10f, 10f)),
                            radiusPct = 2f,
                            isEraser = false,
                        ),
                    ),
            )
        assertTrue(RemovalInputValidator.hasPaintedMask(painted))
    }
}
