package com.watermarkstudio

import com.watermarkstudio.removal.mask.BrushStrokeGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushStrokeGeometryTest {

    @Test
    fun strokeDiameter_usesShorterEdge() {
        val portrait = BrushStrokeGeometry.strokeDiameterPx(1080, 1920, radiusPct = 2f)
        val landscape = BrushStrokeGeometry.strokeDiameterPx(1920, 1080, radiusPct = 2f)
        assertEquals(portrait, landscape, 0.01f)
        assertTrue(portrait > 0f)
    }
}
