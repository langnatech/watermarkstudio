package com.watermarkstudio

import com.watermarkstudio.removal.MaskedBackgroundPropagator
import com.watermarkstudio.removal.MaskedBackgroundPropagator.Rgb
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MaskedBackgroundPropagatorTest {

    @Test
    fun propagateIntoMask_recoversSemiTransparentBlendTowardBackground() {
        val width = 40
        val height = 40
        val pixelCount = width * height
        val rgba = ByteArray(pixelCount * 4)
        val mask = ByteArray(pixelCount)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val base = index * 4
                val inMask = x in 12..27 && y in 12..27
                val background = 40
                val watermark = 220
                val alpha = if (inMask) 0.5f else 0f
                val channel =
                    (background * (1f - alpha) + watermark * alpha).toInt().coerceIn(0, 255)
                rgba[base] = channel.toByte()
                rgba[base + 1] = channel.toByte()
                rgba[base + 2] = channel.toByte()
                rgba[base + 3] = 255.toByte()
                if (inMask) mask[index] = 255.toByte()
            }
        }

        MaskedBackgroundPropagator.propagateIntoMask(rgba, mask, width, height)

        val centerIndex = (20 * width + 20) * 4
        val centerValue = rgba[centerIndex].toInt() and 0xFF
        assertTrue(centerValue < 120)
        assertTrue(abs(centerValue - 40) < 25)
    }

    @Test
    fun estimatePropagationDepth_matchesInteriorDistance() {
        val width = 10
        val height = 10
        val mask = ByteArray(width * height)
        for (y in 2..7) {
            for (x in 2..7) {
                mask[y * width + x] = 255.toByte()
            }
        }

        val depth = MaskedBackgroundPropagator.estimatePropagationDepth(mask, width, height)
        assertTrue(depth >= 3)
    }

    @Test
    fun inpaintRadiusForMaskArea_scalesWithMaskSize() {
        val small = MaskedBackgroundPropagator.inpaintRadiusForMaskArea(100)
        val large = MaskedBackgroundPropagator.inpaintRadiusForMaskArea(10_000)
        assertTrue(large > small)
    }

    @Test
    fun estimateAlpha_recoversKnownBlendWeight() {
        val background = Rgb(40f, 80f, 120f)
        val watermark = Rgb(200f, 180f, 60f)
        val alpha = 0.4f
        val observed =
            Rgb(
                background.r * (1f - alpha) + watermark.r * alpha,
                background.g * (1f - alpha) + watermark.g * alpha,
                background.b * (1f - alpha) + watermark.b * alpha,
            )
        val estimated = MaskedBackgroundPropagator.estimateAlpha(observed, background, watermark)
        assertTrue(abs(estimated - alpha) < 0.05f)
    }

    @Test
    fun applyInteriorAlphaUnmix_usesPropagatedBackgroundOnGradient() {
        val width = 40
        val height = 40
        val pixelCount = width * height
        val red = FloatArray(pixelCount)
        val green = FloatArray(pixelCount)
        val blue = FloatArray(pixelCount)
        val origRed = FloatArray(pixelCount)
        val origGreen = FloatArray(pixelCount)
        val origBlue = FloatArray(pixelCount)
        val masked = BooleanArray(pixelCount)
        val depth = IntArray(pixelCount) { -1 }

        for (x in 0 until width) {
            for (y in 0 until height) {
                val index = y * width + x
                val bg = 20f + x * 3f
                red[index] = bg
                green[index] = bg + 10f
                blue[index] = bg + 20f
            }
        }

        val wmR = 220f
        val wmG = 200f
        val wmB = 80f
        val alpha = 0.35f
        for (y in 12..27) {
            for (x in 12..27) {
                val index = y * width + x
                masked[index] = true
                depth[index] = if (x == 12 || x == 27 || y == 12 || y == 27) 0 else 2
                val bgR = red[index]
                val bgG = green[index]
                val bgB = blue[index]
                origRed[index] = bgR * (1f - alpha) + wmR * alpha
                origGreen[index] = bgG * (1f - alpha) + wmG * alpha
                origBlue[index] = bgB * (1f - alpha) + wmB * alpha
            }
        }

        val center = 20 * width + 20
        val tintGrid =
            MaskedBackgroundPropagator.LocalTintGrid(
                cols = 8,
                rows = 8,
                cellTints = Array(64) { Rgb(wmR, wmG, wmB) },
                globalFallback = Rgb(wmR, wmG, wmB),
            )
        MaskedBackgroundPropagator.applyInteriorAlphaUnmix(
            origRed,
            origGreen,
            origBlue,
            red,
            green,
            blue,
            masked,
            depth,
            width,
            height,
            tintGrid,
        )

        assertTrue(red[center] < origRed[center])
        assertTrue(abs(red[center] - (20f + 20 * 3f)) < 25f)
    }

    @Test
    fun localTintGrid_usesDistinctCellTints() {
        val grid =
            MaskedBackgroundPropagator.LocalTintGrid(
                cols = 2,
                rows = 2,
                cellTints = arrayOf(Rgb(10f, 10f, 10f), null, null, Rgb(200f, 20f, 20f)),
                globalFallback = Rgb(100f, 100f, 100f),
            )
        val topLeft = grid.tintAt(0, 0, width = 100, height = 100)
        val bottomRight = grid.tintAt(99, 99, width = 100, height = 100)
        assertTrue(topLeft.r < 50f)
        assertTrue(bottomRight.r > 150f)
    }

    @Test
    fun applyBoundaryAlphaUnmix_pullsBoundaryTowardBackground() {
        val width = 30
        val height = 30
        val pixelCount = width * height
        val red = FloatArray(pixelCount) { 30f }
        val green = FloatArray(pixelCount) { 60f }
        val blue = FloatArray(pixelCount) { 90f }
        val masked = BooleanArray(pixelCount)

        val bgR = 30f
        val bgG = 60f
        val bgB = 90f
        val wmR = 210f
        val wmG = 40f
        val wmB = 40f
        val alpha = 0.45f
        for (y in 10..19) {
            for (x in 10..19) {
                val index = y * width + x
                masked[index] = true
                red[index] = bgR * (1f - alpha) + wmR * alpha
                green[index] = bgG * (1f - alpha) + wmG * alpha
                blue[index] = bgB * (1f - alpha) + wmB * alpha
            }
        }

        MaskedBackgroundPropagator.applyBoundaryAlphaUnmix(red, green, blue, masked, width, height)

        val boundaryIndex = 10 * width + 15
        assertTrue(red[boundaryIndex] < 120f)
        assertTrue(abs(red[boundaryIndex] - bgR) < 35f)
    }
}
