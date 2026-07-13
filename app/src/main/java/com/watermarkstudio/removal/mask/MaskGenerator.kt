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

    /**
     * Soft-mask pixels at or above this value are treated as the inpaint core
     * (PatchMatch / propagator / OpenCV NS). Lower fringe is only used as paste alpha.
     */
    const val INPAINT_CORE_THRESHOLD = 40

    /** Soft morphological expand before blur (replaces hard 3px dilate). */
    private const val SOFT_EXPAND_PX = 1

    /** Gaussian kernel for soft edges (odd). */
    private const val SOFT_BLUR_KERNEL = 7

    /**
     * 8-bit single-channel **soft** mask (0–255).
     * Paint strokes add; eraser strokes subtract (in order). Optional edge erode last.
     */
    fun createMaskMat(width: Int, height: Int, config: WatermarkConfig): Mat {
        if (width <= 0 || height <= 0 || config.removalStrokes.isEmpty()) {
            return Mat.zeros(height.coerceAtLeast(0), width.coerceAtLeast(0), CvType.CV_8UC1)
        }
        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskBitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        config.removalStrokes.forEach { stroke ->
            paint.color = if (stroke.isEraser) Color.BLACK else Color.WHITE
            paint.strokeWidth = BrushStrokeGeometry.strokeDiameterPx(width, height, stroke.radiusPct)
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
        maskMat.release()

        val expanded = Mat()
        val kernelSize = (SOFT_EXPAND_PX * 2 + 1).toDouble()
        val dilateKernel =
            Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE,
                Size(kernelSize, kernelSize),
            )
        Imgproc.dilate(gray, expanded, dilateKernel)
        dilateKernel.release()
        gray.release()

        val soft = Mat()
        Imgproc.GaussianBlur(
            expanded,
            soft,
            Size(SOFT_BLUR_KERNEL.toDouble(), SOFT_BLUR_KERNEL.toDouble()),
            0.0,
        )
        expanded.release()

        val erodePx = config.maskErodePx.coerceIn(0, WatermarkConfig.MAX_MASK_ERODE_PX)
        if (erodePx <= 0) {
            return soft
        }
        val eroded = Mat()
        val erodeKernel =
            Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE,
                Size((erodePx * 2 + 1).toDouble(), (erodePx * 2 + 1).toDouble()),
            )
        Imgproc.erode(soft, eroded, erodeKernel)
        erodeKernel.release()
        soft.release()
        return eroded
    }

    /** Binary core for algorithms that require a hard mask (255 = repair). */
    fun toInpaintCoreBytes(softMaskBytes: ByteArray): ByteArray {
        val hard = ByteArray(softMaskBytes.size)
        for (i in softMaskBytes.indices) {
            hard[i] =
                if ((softMaskBytes[i].toInt() and 0xFF) >= INPAINT_CORE_THRESHOLD) {
                    0xFF.toByte()
                } else {
                    0
                }
        }
        return hard
    }

    fun regionForConfig(width: Int, height: Int, config: WatermarkConfig): RemovalRegion =
        RemovalRegion.fromConfig(width, height, config)
}
