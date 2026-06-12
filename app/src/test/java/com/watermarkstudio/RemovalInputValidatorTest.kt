package com.watermarkstudio

import com.watermarkstudio.model.RemovalStroke
import com.watermarkstudio.model.RemovalStrokePoint
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.removal.RemovalInputValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemovalInputValidatorTest {

    @Test
    fun hasPaintedMask_falseWhenNoStrokes() {
        val config = WatermarkConfig(type = WatermarkType.REMOVE)
        assertFalse(RemovalInputValidator.hasPaintedMask(config))
    }

    @Test
    fun hasPaintedMask_trueWhenStrokesPresent() {
        val config =
            WatermarkConfig(
                type = WatermarkType.REMOVE,
                removalStrokes =
                    listOf(
                        RemovalStroke(
                            points = listOf(RemovalStrokePoint(10f, 20f)),
                            radiusPct = WatermarkConfig.DEFAULT_BRUSH_RADIUS_PCT,
                        ),
                    ),
            )
        assertTrue(RemovalInputValidator.hasPaintedMask(config))
    }
}
