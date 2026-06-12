package com.watermarkstudio.removal.mask

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.util.RemovalRegion
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object MaskGenerator {

    private const val MASK_THRESHOLD = 128.0
    private const val MASK_DILATE_PX = 3
    private const val MIN_STROKE_RADIUS_PX = 2f

    /** 8-bit single-channel mask for OpenCV inpaint (255 = repair region). */
    fun createMaskMat(width: Int, height: Int, config: WatermarkConfig): Mat {
        if (width <= 0 || height <= 0 || config.removalStrokes.isEmpty()) {
            return Mat.zeros(height.coerceAtLeast(0), width.coerceAtLeast(0), CvType.CV_8UC1)
        }
        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskBitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        config.removalStrokes.forEach { stroke ->
            paint.strokeWidth = (width * stroke.radiusPct / 100f * 2f).coerceAtLeast(MIN_STROKE_RADIUS_PX * 2f)
            val points = stroke.points
            when (points.size) {
                0 -> Unit
                1 -> {
                    val point = points.first()
                    canvas.drawCircle(
                        width * (point.xPct / 100f),
                        height * (point.yPct / 100f),
                        paint.strokeWidth / 2f,
                        paint,
                    )
                }
                else -> {
                    val path = Path()
                    points.first().let { first ->
                        path.moveTo(width * (first.xPct / 100f), height * (first.yPct / 100f))
                    }
                    points.drop(1).forEach { point ->
                        path.lineTo(width * (point.xPct / 100f), height * (point.yPct / 100f))
                    }
                    canvas.drawPath(path, paint)
                }
            }
        }
        val maskMat = Mat()
        Utils.bitmapToMat(maskBitmap, maskMat)
        maskBitmap.recycle()
        val gray = Mat()
        Imgproc.cvtColor(maskMat, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.threshold(gray, gray, MASK_THRESHOLD, 255.0, Imgproc.THRESH_BINARY)
        val kernel =
            Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE,
                Size((MASK_DILATE_PX * 2 + 1).toDouble(), (MASK_DILATE_PX * 2 + 1).toDouble()),
            )
        Imgproc.dilate(gray, gray, kernel)
        kernel.release()
        maskMat.release()
        return gray
    }

    fun regionForConfig(width: Int, height: Int, config: WatermarkConfig): RemovalRegion =
        RemovalRegion.fromConfig(width, height, config)
}
