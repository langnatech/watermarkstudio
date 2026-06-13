package com.watermarkstudio

import com.watermarkstudio.removal.PreviewScaleContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewScaleContextTest {

    @Test
    fun areaScale_squaresResolutionRatio() {
        val ctx = PreviewScaleContext(exportMaxDim = 1000, previewMaxDim = 500)
        assertEquals(0.25f, ctx.areaScale, 0.001f)
    }

    @Test
    fun forBitmap_usesShorterEdge() {
        val ctx = PreviewScaleContext.forBitmap(exportMaxDim = 1024, width = 1920, height = 720)
        assertEquals(720, ctx.previewMaxDim)
        assertTrue(ctx.areaScale < 1f)
    }
}
