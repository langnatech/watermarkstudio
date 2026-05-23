package com.watermarkstudio.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkFontFamily

/**
 * Shared text watermark drawing for image export, video overlay bitmaps, and size measurement.
 */
object TextWatermarkRenderer {

    fun typeface(family: WatermarkFontFamily): Typeface =
        when (family) {
            WatermarkFontFamily.SANS -> Typeface.SANS_SERIF
            WatermarkFontFamily.SERIF -> Typeface.SERIF
            WatermarkFontFamily.MONOSPACE -> Typeface.MONOSPACE
            WatermarkFontFamily.BOLD -> Typeface.DEFAULT_BOLD
        }

    /** Full ARGB including [WatermarkConfig.opacity]. */
    fun resolveArgbColor(config: WatermarkConfig): Int {
        val rgb = config.color or 0xFF000000.toInt()
        val alpha = (config.opacity * 255f).toInt().coerceIn(0, 255)
        return (rgb and 0x00FFFFFF) or (alpha shl 24)
    }

    fun textSizePx(context: Context, config: WatermarkConfig, referenceWidthPx: Int): Float {
        val density = context.resources.displayMetrics
        val spPx = config.textSizeSp * density.scaledDensity
        val refScale = (referenceWidthPx / 1080f).coerceIn(0.5f, 3f)
        return spPx * refScale
    }

    fun createPaint(context: Context, config: WatermarkConfig, referenceWidthPx: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = resolveArgbColor(config)
            textSize = textSizePx(context, config, referenceWidthPx)
            typeface = typeface(config.fontFamily)
            setShadowLayer(2f, 1f, 1f, Color.argb(96, 0, 0, 0))
        }

    fun drawOnCanvas(
        canvas: Canvas,
        config: WatermarkConfig,
        context: Context,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ) {
        if (config.text.isBlank()) return
        val paint = createPaint(context, config, bitmapWidth)
        val xPos = bitmapWidth * (config.x / 100f)
        val topLeftY = bitmapHeight * (config.y / 100f)
        val baselineY = topLeftY - paint.fontMetrics.ascent
        canvas.drawText(config.text, xPos, baselineY, paint)
    }

    /**
     * Renders user [WatermarkConfig.text] into a bitmap for video [BitmapOverlay].
     * @param referenceWidthPx output video width (or 1080 if unknown) for proportional sizing.
     */
    fun renderTextBitmap(
        context: Context,
        config: WatermarkConfig,
        referenceWidthPx: Int = 1080,
    ): Bitmap? {
        if (config.text.isBlank()) return null
        val paint = createPaint(context, config, referenceWidthPx)
        val bounds = Rect()
        paint.getTextBounds(config.text, 0, config.text.length, bounds)
        val pad = (paint.textSize * 0.25f).toInt().coerceAtLeast(4)
        val width = (bounds.width() + pad * 2).coerceAtLeast(1)
        val height =
            (bounds.height() + pad * 2)
                .coerceAtLeast((paint.textSize * 1.3f).toInt())
                .coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val baselineY = pad - bounds.top.toFloat()
        canvas.drawText(config.text, pad.toFloat(), baselineY, paint)
        return bitmap
    }
}
