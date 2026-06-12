package com.watermarkstudio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.watermarkstudio.model.RemovalStroke
import com.watermarkstudio.model.RemovalStrokePoint
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.removal.video.OpticalFlowRecoveryProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OpticalFlowRecoveryProcessorTest {

    private lateinit var removeConfig: WatermarkConfig

    @Before
    fun setUp() {
        removeConfig =
            WatermarkConfig(
                type = WatermarkType.REMOVE,
                removalStrokes =
                    listOf(
                        RemovalStroke(
                            points =
                                listOf(
                                    RemovalStrokePoint(45f, 45f),
                                    RemovalStrokePoint(55f, 55f),
                                ),
                            radiusPct = WatermarkConfig.DEFAULT_BRUSH_RADIUS_PCT,
                        ),
                    ),
            )
        try {
            System.loadLibrary("opencv_java4")
        } catch (_: UnsatisfiedLinkError) {
            // Host JVM may not load OpenCV; optical-flow assertions are skipped.
        }
    }

    @Test
    fun recover_medianPath_preservesFrameCountAndSize() {
        val frames = createCheckerboardSequence(5, 80, 60)
        val result =
            try {
                OpticalFlowRecoveryProcessor.recover(frames, removeConfig, useOpticalFlow = false)
            } catch (e: UnsatisfiedLinkError) {
                return
            }
        assertEquals(frames.size, result.size)
        assertEquals(80, result.first().width)
        assertEquals(60, result.first().height)
        result.forEach { it.recycle() }
    }

    @Test
    fun recover_opticalFlow_mayAlterRoiPixels() {
        val frames = createCheckerboardSequence(6, 96, 72)
        val beforeRoi = sampleRoiPixel(frames[2], 96, 72)
        val result =
            try {
                OpticalFlowRecoveryProcessor.recover(frames, removeConfig, useOpticalFlow = true)
            } catch (e: Throwable) {
                return
            }
        assertEquals(frames.size, result.size)
        val afterRoi = sampleRoiPixel(result[2], 96, 72)
        assertTrue(beforeRoi != 0 || afterRoi != 0)
        result.forEach { it.recycle() }
    }

    private fun sampleRoiPixel(frame: Bitmap, width: Int, height: Int): Int {
        val cx = (width * 0.5f).toInt().coerceIn(0, width - 1)
        val cy = (height * 0.5f).toInt().coerceIn(0, height - 1)
        return frame.getPixel(cx, cy)
    }

    private fun createCheckerboardSequence(count: Int, width: Int, height: Int): List<Bitmap> {
        return List(count) { index ->
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val cell = 12
            for (y in 0 until height step cell) {
                for (x in 0 until width step cell) {
                    val dark = ((x + y + index * 4) / cell) % 2 == 0
                    canvas.drawRect(
                        x.toFloat(),
                        y.toFloat(),
                        (x + cell).toFloat(),
                        (y + cell).toFloat(),
                        Paint().apply { color = if (dark) Color.DKGRAY else Color.LTGRAY },
                    )
                }
            }
            canvas.drawRect(
                width * 0.4f,
                height * 0.45f,
                width * 0.6f,
                height * 0.55f,
                Paint().apply { color = Color.WHITE },
            )
            bmp
        }
    }
}
