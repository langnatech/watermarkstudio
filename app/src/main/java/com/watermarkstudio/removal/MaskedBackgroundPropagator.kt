package com.watermarkstudio.removal

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Extrapolates background colors into masked pixels before PatchMatch / inpaint.
 *
 * Semi-transparent watermarks satisfy observed ≈ (1 − α)·background + α·watermark.
 * Inpainting blended pixels as if they were opaque watermark causes color bleed.
 * This runs a Laplacian-style propagation from unmasked neighbors inward.
 */
object MaskedBackgroundPropagator {

    private const val MAX_ITERATIONS = 64
    private const val MIN_ITERATIONS = 6

    fun propagateIntoMask(
        rgba: ByteArray,
        mask: ByteArray,
        width: Int,
        height: Int,
    ) {
        if (width <= 0 || height <= 0) return
        val pixelCount = width * height
        if (rgba.size != pixelCount * 4 || mask.size != pixelCount) return
        if (!mask.any { it != 0.toByte() }) return

        val depthMap = computeMaskDepth(mask, width, height)
        val iterations = depthMap.maxOrNull()?.let { max(it * 4 + 1, MIN_ITERATIONS) } ?: MIN_ITERATIONS
        val propagationPasses = iterations.coerceIn(MIN_ITERATIONS, MAX_ITERATIONS)

        val red = FloatArray(pixelCount)
        val green = FloatArray(pixelCount)
        val blue = FloatArray(pixelCount)
        val origRed = FloatArray(pixelCount)
        val origGreen = FloatArray(pixelCount)
        val origBlue = FloatArray(pixelCount)
        val masked = BooleanArray(pixelCount)

        for (index in 0 until pixelCount) {
            val base = index * 4
            val r = (rgba[base].toInt() and 0xFF).toFloat()
            val g = (rgba[base + 1].toInt() and 0xFF).toFloat()
            val b = (rgba[base + 2].toInt() and 0xFF).toFloat()
            red[index] = r
            green[index] = g
            blue[index] = b
            origRed[index] = r
            origGreen[index] = g
            origBlue[index] = b
            masked[index] = mask[index] != 0.toByte()
        }

        val watermarkTint = applyBoundaryAlphaUnmix(red, green, blue, masked, width, height)

        repeat(propagationPasses) {
            val forwardChanged = relaxMaskedPixels(red, green, blue, masked, width, height, forward = true)
            val backwardChanged = relaxMaskedPixels(red, green, blue, masked, width, height, forward = false)
            if (!forwardChanged && !backwardChanged) return@repeat
        }

        if (watermarkTint != null) {
            applyInteriorAlphaUnmix(
                origRed,
                origGreen,
                origBlue,
                red,
                green,
                blue,
                masked,
                depthMap,
                watermarkTint,
            )
        }

        for (index in 0 until pixelCount) {
            if (!masked[index]) continue
            val base = index * 4
            rgba[base] = red[index].roundToInt().coerceIn(0, 255).toByte()
            rgba[base + 1] = green[index].roundToInt().coerceIn(0, 255).toByte()
            rgba[base + 2] = blue[index].roundToInt().coerceIn(0, 255).toByte()
        }
    }

    /** BFS depth from mask boundary; unmasked pixels stay at -1. */
    internal fun computeMaskDepth(mask: ByteArray, width: Int, height: Int): IntArray {
        val pixelCount = width * height
        val depth = IntArray(pixelCount) { -1 }
        val queue = ArrayDeque<Int>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (mask[index] == 0.toByte()) continue
                val touchesBackground =
                    (x == 0 || mask[index - 1] == 0.toByte()) ||
                        (x + 1 == width || mask[index + 1] == 0.toByte()) ||
                        (y == 0 || mask[index - width] == 0.toByte()) ||
                        (y + 1 == height || mask[index + width] == 0.toByte())
                if (touchesBackground) {
                    depth[index] = 0
                    queue.addLast(index)
                }
            }
        }

        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val nextDepth = depth[index] + 1
            val x = index % width
            val y = index / width

            fun visit(neighbor: Int) {
                if (neighbor !in 0 until pixelCount) return
                if (mask[neighbor] == 0.toByte() || depth[neighbor] >= 0) return
                depth[neighbor] = nextDepth
                queue.addLast(neighbor)
            }

            if (x > 0) visit(index - 1)
            if (x + 1 < width) visit(index + 1)
            if (y > 0) visit(index - width)
            if (y + 1 < height) visit(index + width)
        }

        return depth
    }

    internal fun estimatePropagationDepth(mask: ByteArray, width: Int, height: Int): Int {
        val depth = computeMaskDepth(mask, width, height)
        val maxDepth = depth.maxOrNull() ?: -1
        if (maxDepth < 0) return MIN_ITERATIONS
        return max(maxDepth * 4 + 1, MIN_ITERATIONS)
    }

    private fun relaxMaskedPixels(
        red: FloatArray,
        green: FloatArray,
        blue: FloatArray,
        masked: BooleanArray,
        width: Int,
        height: Int,
        forward: Boolean,
    ): Boolean {
        var changed = false
        val yRange = if (forward) 0 until height else height - 1 downTo 0
        val xRange = if (forward) 0 until width else width - 1 downTo 0
        for (y in yRange) {
            for (x in xRange) {
                val index = y * width + x
                if (!masked[index]) continue

                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var count = 0

                if (x > 0) {
                    val neighbor = index - 1
                    sumR += red[neighbor]
                    sumG += green[neighbor]
                    sumB += blue[neighbor]
                    count++
                }
                if (x + 1 < width) {
                    val neighbor = index + 1
                    sumR += red[neighbor]
                    sumG += green[neighbor]
                    sumB += blue[neighbor]
                    count++
                }
                if (y > 0) {
                    val neighbor = index - width
                    sumR += red[neighbor]
                    sumG += green[neighbor]
                    sumB += blue[neighbor]
                    count++
                }
                if (y + 1 < height) {
                    val neighbor = index + width
                    sumR += red[neighbor]
                    sumG += green[neighbor]
                    sumB += blue[neighbor]
                    count++
                }

                if (count == 0) continue

                val avgR = sumR / count
                val avgG = sumG / count
                val avgB = sumB / count
                if (avgR != red[index] || avgG != green[index] || avgB != blue[index]) {
                    changed = true
                }
                red[index] = avgR
                green[index] = avgG
                blue[index] = avgB
            }
        }
        return changed
    }

    internal fun inpaintRadiusForMaskArea(maskedPixelCount: Int): Double {
        if (maskedPixelCount <= 0) return TELEA_MIN_RADIUS
        val estimate = sqrt(maskedPixelCount.toDouble()) * TELEA_RADIUS_SCALE
        return estimate.coerceIn(TELEA_MIN_RADIUS, TELEA_MAX_RADIUS)
    }

    private const val TELEA_MIN_RADIUS = 3.0
    private const val TELEA_MAX_RADIUS = 12.0
    private const val TELEA_RADIUS_SCALE = 0.06

    private const val MIN_WM_DELTA = 6f
    private const val MIN_UNMIX_ALPHA = 0.08f
    private const val MAX_UNMIX_ALPHA = 0.92f

    /**
     * Estimates a global watermark tint from the mask boundary, then unmixes
     * `observed = (1 − α)·background + α·watermark` for boundary pixels.
     */
    internal fun applyInteriorAlphaUnmix(
        origRed: FloatArray,
        origGreen: FloatArray,
        origBlue: FloatArray,
        red: FloatArray,
        green: FloatArray,
        blue: FloatArray,
        masked: BooleanArray,
        depthMap: IntArray,
        watermark: Rgb,
    ) {
        for (index in masked.indices) {
            if (!masked[index] || depthMap[index] <= 0) continue
            val background = Rgb(red[index], green[index], blue[index])
            val observed = Rgb(origRed[index], origGreen[index], origBlue[index])
            val alpha = estimateAlpha(observed, background, watermark)
            if (alpha !in MIN_UNMIX_ALPHA..MAX_UNMIX_ALPHA) continue
            val recovered = unmix(observed, background, watermark, alpha)
            red[index] = recovered.r
            green[index] = recovered.g
            blue[index] = recovered.b
        }
    }

    internal fun applyBoundaryAlphaUnmix(
        red: FloatArray,
        green: FloatArray,
        blue: FloatArray,
        masked: BooleanArray,
        width: Int,
        height: Int,
    ): Rgb? {
        val boundaryIndices = mutableListOf<Int>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (!masked[index] || !touchesBackground(masked, width, height, x, y)) continue
                boundaryIndices.add(index)
            }
        }
        if (boundaryIndices.isEmpty()) return null

        val watermarkRed = mutableListOf<Float>()
        val watermarkGreen = mutableListOf<Float>()
        val watermarkBlue = mutableListOf<Float>()
        for (index in boundaryIndices) {
            val background = averageUnmaskedNeighbors(red, green, blue, masked, width, height, index) ?: continue
            val observed = Rgb(red[index], green[index], blue[index])
            if (colorDistance(observed, background) < MIN_WM_DELTA) continue
            watermarkRed.add((observed.r * 2f - background.r).coerceIn(0f, 255f))
            watermarkGreen.add((observed.g * 2f - background.g).coerceIn(0f, 255f))
            watermarkBlue.add((observed.b * 2f - background.b).coerceIn(0f, 255f))
        }
        if (watermarkRed.isEmpty()) return null

        val watermark =
            Rgb(
                median(watermarkRed),
                median(watermarkGreen),
                median(watermarkBlue),
            )

        for (index in boundaryIndices) {
            val background = averageUnmaskedNeighbors(red, green, blue, masked, width, height, index) ?: continue
            val observed = Rgb(red[index], green[index], blue[index])
            val alpha = estimateAlpha(observed, background, watermark)
            if (alpha !in MIN_UNMIX_ALPHA..MAX_UNMIX_ALPHA) continue
            val recovered = unmix(observed, background, watermark, alpha)
            red[index] = recovered.r
            green[index] = recovered.g
            blue[index] = recovered.b
        }
        return watermark
    }

    internal data class Rgb(val r: Float, val g: Float, val b: Float)

    private fun touchesBackground(
        masked: BooleanArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
    ): Boolean {
        val index = y * width + x
        return (x == 0 || !masked[index - 1]) ||
            (x + 1 == width || !masked[index + 1]) ||
            (y == 0 || !masked[index - width]) ||
            (y + 1 == height || !masked[index + width])
    }

    private fun averageUnmaskedNeighbors(
        red: FloatArray,
        green: FloatArray,
        blue: FloatArray,
        masked: BooleanArray,
        width: Int,
        height: Int,
        index: Int,
    ): Rgb? {
        val x = index % width
        val y = index / width
        var sumR = 0f
        var sumG = 0f
        var sumB = 0f
        var count = 0

        fun sample(neighbor: Int) {
            if (masked[neighbor]) return
            sumR += red[neighbor]
            sumG += green[neighbor]
            sumB += blue[neighbor]
            count++
        }

        if (x > 0) sample(index - 1)
        if (x + 1 < width) sample(index + 1)
        if (y > 0) sample(index - width)
        if (y + 1 < height) sample(index + width)
        if (count == 0) return null
        return Rgb(sumR / count, sumG / count, sumB / count)
    }

    internal fun estimateAlpha(observed: Rgb, background: Rgb, watermark: Rgb): Float {
        val dr = watermark.r - background.r
        val dg = watermark.g - background.g
        val db = watermark.b - background.b
        val denom = dr * dr + dg * dg + db * db
        if (denom < 1f) return 0f
        val or_ = observed.r - background.r
        val og = observed.g - background.g
        val ob = observed.b - background.b
        return ((or_ * dr + og * dg + ob * db) / denom).coerceIn(0f, 1f)
    }

    internal fun unmix(observed: Rgb, background: Rgb, watermark: Rgb, alpha: Float): Rgb {
        val inv = (1f - alpha).coerceAtLeast(0.05f)
        return Rgb(
            ((observed.r - alpha * watermark.r) / inv).coerceIn(0f, 255f),
            ((observed.g - alpha * watermark.g) / inv).coerceIn(0f, 255f),
            ((observed.b - alpha * watermark.b) / inv).coerceIn(0f, 255f),
        )
    }

    private fun colorDistance(a: Rgb, b: Rgb): Float {
        val dr = a.r - b.r
        val dg = a.g - b.g
        val db = a.b - b.b
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
