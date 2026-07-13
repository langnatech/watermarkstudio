package com.watermarkstudio.removal.mask

import android.graphics.Bitmap
import android.graphics.Color
import com.watermarkstudio.model.RemovalStroke
import com.watermarkstudio.model.RemovalStrokePoint
import kotlin.math.abs
import kotlin.math.min

/**
 * Color-similarity flood fill → horizontal paint strokes for REMOVE smart selection.
 */
object RemovalSmartSelect {

    const val DEFAULT_COLOR_TOLERANCE = 36
    const val MAX_FILL_PIXELS = 80_000

    /**
     * Flood-fills from [seedX],[seedY] on [bitmap] and converts filled runs into paint strokes.
     * Returns empty when the seed is out of bounds or nothing expands.
     */
    fun floodFillToStrokes(
        bitmap: Bitmap,
        seedX: Int,
        seedY: Int,
        colorTolerance: Int = DEFAULT_COLOR_TOLERANCE,
        maxFillPixels: Int = MAX_FILL_PIXELS,
        batchId: Long? = null,
        isEraser: Boolean = false,
    ): List<RemovalStroke> {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return emptyList()
        if (seedX !in 0 until width || seedY !in 0 until height) return emptyList()

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val seedColor = pixels[seedY * width + seedX]
        val filled = BooleanArray(width * height)
        val queueX = IntArray(maxFillPixels.coerceAtLeast(1))
        val queueY = IntArray(maxFillPixels.coerceAtLeast(1))
        var head = 0
        var tail = 0
        var filledCount = 0

        fun enqueue(x: Int, y: Int) {
            if (x !in 0 until width || y !in 0 until height) return
            val index = y * width + x
            if (filled[index]) return
            if (!colorsSimilar(seedColor, pixels[index], colorTolerance)) return
            if (filledCount >= maxFillPixels || tail >= queueX.size) return
            filled[index] = true
            filledCount++
            queueX[tail] = x
            queueY[tail] = y
            tail++
        }

        enqueue(seedX, seedY)
        while (head < tail && filledCount < maxFillPixels) {
            val x = queueX[head]
            val y = queueY[head]
            head++
            enqueue(x - 1, y)
            enqueue(x + 1, y)
            enqueue(x, y - 1)
            enqueue(x, y + 1)
        }
        if (filledCount <= 1) return emptyList()

        val shortEdge = min(width, height).coerceAtLeast(1)
        val radiusPct = ((0.65f / shortEdge) * 100f).coerceIn(0.12f, 1.2f)
        val strokes = ArrayList<RemovalStroke>()
        for (y in 0 until height) {
            var x = 0
            while (x < width) {
                while (x < width && !filled[y * width + x]) x++
                if (x >= width) break
                val startX = x
                while (x < width && filled[y * width + x]) x++
                val endX = x - 1
                val yPct = (y + 0.5f) / height * 100f
                val points =
                    if (startX == endX) {
                        listOf(RemovalStrokePoint((startX + 0.5f) / width * 100f, yPct))
                    } else {
                        listOf(
                            RemovalStrokePoint((startX + 0.5f) / width * 100f, yPct),
                            RemovalStrokePoint((endX + 0.5f) / width * 100f, yPct),
                        )
                    }
                strokes.add(
                    RemovalStroke(
                        points = points,
                        radiusPct = radiusPct,
                        isEraser = isEraser,
                        batchId = batchId,
                    ),
                )
            }
        }
        return strokes
    }

    internal fun colorsSimilar(a: Int, b: Int, tolerance: Int): Boolean {
        val dr = abs(Color.red(a) - Color.red(b))
        val dg = abs(Color.green(a) - Color.green(b))
        val db = abs(Color.blue(a) - Color.blue(b))
        return dr <= tolerance && dg <= tolerance && db <= tolerance
    }
}
