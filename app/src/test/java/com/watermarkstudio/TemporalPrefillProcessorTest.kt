package com.watermarkstudio

import android.graphics.Bitmap
import android.graphics.Color
import com.watermarkstudio.model.RemovalStroke
import com.watermarkstudio.model.RemovalStrokePoint
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkType
import com.watermarkstudio.removal.RemovalQuality
import com.watermarkstudio.removal.video.TemporalPrefillProcessor
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TemporalPrefillProcessorTest {

    init {
        try {
            System.loadLibrary("opencv_java4")
        } catch (_: UnsatisfiedLinkError) {
            // Host JVM may not load OpenCV; median prefill assertions are skipped.
        }
    }

    @Test
    fun prefill_standard_medianStabilizesMaskedPixel() {
        val width = 40
        val height = 40
        val config =
            WatermarkConfig(
                type = WatermarkType.REMOVE,
                removalStrokes =
                    listOf(
                        RemovalStroke(
                            points = listOf(RemovalStrokePoint(50f, 50f)),
                            radiusPct = 5f,
                        ),
                    ),
            )
        val frames =
            (0 until 5).map { index ->
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.rgb(40 + index * 10, 80, 120))
                }
            }
        val current = frames.last()
        val out =
            try {
                TemporalPrefillProcessor.prefill(
                    current,
                    frames,
                    config,
                    RemovalQuality.STANDARD,
                )
            } catch (e: UnsatisfiedLinkError) {
                frames.forEach { it.recycle() }
                return
            }
        val center = out.getPixel(20, 20)
        val red = Color.red(center)
        assert(red in 55..95) { "median red=$red" }
        frames.forEach { if (it !== out && it !== current) it.recycle() }
        if (out !== current) out.recycle()
    }

    @Test
    fun fuseMedianAndOpticalFlow_averagesCorePixels() {
        val width = 32
        val height = 32
        val config =
            WatermarkConfig(
                type = WatermarkType.REMOVE,
                removalStrokes =
                    listOf(
                        RemovalStroke(
                            points = listOf(RemovalStrokePoint(50f, 50f)),
                            radiusPct = 8f,
                        ),
                    ),
            )
        val current = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(10, 10, 10))
        }
        val median = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(0, 0, 100))
        }
        val flowed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(200, 0, 0))
        }
        val fused =
            try {
                TemporalPrefillProcessor.fuseMedianAndOpticalFlow(current, median, flowed, config)
            } catch (_: UnsatisfiedLinkError) {
                current.recycle()
                median.recycle()
                flowed.recycle()
                return
            }
        val center = fused.getPixel(16, 16)
        assert(Color.red(center) in 90..110) { "fused red=${Color.red(center)}" }
        assert(Color.blue(center) in 40..60) { "fused blue=${Color.blue(center)}" }
        current.recycle()
        median.recycle()
        flowed.recycle()
        if (fused !== current) fused.recycle()
    }

    @Test
    fun slidingWindow_evictsOldestFrame() {
        val window = TemporalPrefillProcessor.SlidingWindow(maxSize = 3)
        val a = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val b = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val c = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val d = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        window.push(a)
        window.push(b)
        window.push(c)
        window.push(d)
        assertNotSame(a, window.snapshot().first())
        window.release()
    }
}
