package com.watermarkstudio.removal.mask

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.util.RemovalRegion
import org.opencv.core.Mat
import org.opencv.core.CvType
import org.opencv.android.Utils

object MaskGenerator {

    /** 8-bit single-channel mask for OpenCV inpaint (255 = repair region). */
    fun createMaskMat(width: Int, height: Int, config: WatermarkConfig): Mat {
        val region = RemovalRegion.fromConfig(width, height, config)
        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskBitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawRect(
            region.left.toFloat(),
            region.top.toFloat(),
            region.right.toFloat(),
            region.bottom.toFloat(),
            paint,
        )
        val maskMat = Mat()
        Utils.bitmapToMat(maskBitmap, maskMat)
        maskBitmap.recycle()
        val gray = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(maskMat, gray, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY)
        org.opencv.imgproc.Imgproc.threshold(gray, gray, 128.0, 255.0, org.opencv.imgproc.Imgproc.THRESH_BINARY)
        maskMat.release()
        return gray
    }

    fun regionForConfig(width: Int, height: Int, config: WatermarkConfig): RemovalRegion =
        RemovalRegion.fromConfig(width, height, config)

    /** Feathered mask for seamlessClone (255 = blend region). */
    fun createFeatheredMaskMat(width: Int, height: Int, config: WatermarkConfig, expandPx: Int = 6): Mat {
        val region = RemovalRegion.fromConfig(width, height, config)
        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskBitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        val left = (region.left - expandPx).coerceAtLeast(0).toFloat()
        val top = (region.top - expandPx).coerceAtLeast(0).toFloat()
        val right = (region.right + expandPx).coerceAtMost(width).toFloat()
        val bottom = (region.bottom + expandPx).coerceAtMost(height).toFloat()
        canvas.drawRect(left, top, right, bottom, paint)
        val maskMat = Mat()
        Utils.bitmapToMat(maskBitmap, maskMat)
        maskBitmap.recycle()
        val gray = Mat()
        org.opencv.imgproc.Imgproc.cvtColor(maskMat, gray, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY)
        org.opencv.imgproc.Imgproc.GaussianBlur(gray, gray, org.opencv.core.Size(9.0, 9.0), 0.0)
        org.opencv.imgproc.Imgproc.threshold(gray, gray, 32.0, 255.0, org.opencv.imgproc.Imgproc.THRESH_BINARY)
        maskMat.release()
        return gray
    }
}
