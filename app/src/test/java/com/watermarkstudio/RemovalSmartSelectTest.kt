package com.watermarkstudio

import android.graphics.Bitmap
import android.graphics.Color
import com.watermarkstudio.removal.mask.RemovalSmartSelect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RemovalSmartSelectTest {

    @Test
    fun colorsSimilar_respectsTolerance() {
        val a = Color.rgb(100, 100, 100)
        val near = Color.rgb(120, 100, 100)
        val far = Color.rgb(160, 100, 100)
        assertTrue(RemovalSmartSelect.colorsSimilar(a, near, tolerance = 36))
        assertTrue(!RemovalSmartSelect.colorsSimilar(a, far, tolerance = 36))
    }

    @Test
    fun floodFillToStrokes_selectsConnectedBlock() {
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(10, 10, 10))
        for (y in 5 until 12) {
            for (x in 5 until 14) {
                bitmap.setPixel(x, y, Color.rgb(200, 40, 40))
            }
        }
        val strokes = RemovalSmartSelect.floodFillToStrokes(bitmap, seedX = 8, seedY = 8)
        assertTrue(strokes.isNotEmpty())
        assertTrue(strokes.all { !it.isEraser })
        assertTrue(strokes.size >= 7)
        bitmap.recycle()
    }

    @Test
    fun floodFillToStrokes_eraserFlagMarksSubtractStrokes() {
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(10, 10, 10))
        for (y in 5 until 12) {
            for (x in 5 until 14) {
                bitmap.setPixel(x, y, Color.rgb(200, 40, 40))
            }
        }
        val strokes =
            RemovalSmartSelect.floodFillToStrokes(
                bitmap,
                seedX = 8,
                seedY = 8,
                isEraser = true,
                batchId = 42L,
            )
        assertTrue(strokes.isNotEmpty())
        assertTrue(strokes.all { it.isEraser && it.batchId == 42L })
        bitmap.recycle()
    }

    @Test
    fun floodFillToStrokes_emptyWhenSinglePixelMismatchNeighborhood() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixel(3, 3, Color.BLACK)
        val strokes = RemovalSmartSelect.floodFillToStrokes(bitmap, seedX = 3, seedY = 3, colorTolerance = 5)
        assertEquals(0, strokes.size)
        bitmap.recycle()
    }
}
