package com.watermarkstudio.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkFontFamily

/**
 * Shared text watermark drawing for image export, video overlay bitmaps, and size measurement.
 * Mainstream style: bright fill + thin dark outline (readable on light/dark backgrounds).
 */
object TextWatermarkRenderer {

    private const val DEFAULT_RGB = 0xFFFFFF
    private const val OUTLINE_ALPHA_SCALE = 0.72f

    fun typeface(family: WatermarkFontFamily): Typeface =
        when (family) {
            WatermarkFontFamily.SANS -> Typeface.SANS_SERIF
            WatermarkFontFamily.SERIF -> Typeface.SERIF
            WatermarkFontFamily.MONOSPACE -> Typeface.MONOSPACE
            WatermarkFontFamily.BOLD -> Typeface.DEFAULT_BOLD
        }

    fun resolveFillRgb(config: WatermarkConfig): Int {
        val rgb = config.color and 0x00FFFFFF
        return if (rgb == 0) DEFAULT_RGB else rgb
    }

    /** Fill alpha from [WatermarkConfig.opacity] (mainstream: high opacity white). */
    fun resolveFillArgb(config: WatermarkConfig): Int {
        val alpha = (config.opacity * 255f).toInt().coerceIn(0, 255)
        return (alpha shl 24) or resolveFillRgb(config)
    }

    fun textSizePx(context: Context, config: WatermarkConfig, referenceWidthPx: Int): Float {
        val density = context.resources.displayMetrics
        val spPx = config.textSizeSp * density.scaledDensity
        val refScale = (referenceWidthPx / 1080f).coerceIn(0.5f, 3f)
        return spPx * refScale
    }

    private fun buildPaints(
        context: Context,
        config: WatermarkConfig,
        referenceWidthPx: Int,
    ): Pair<Paint, Paint> {
        val textSize = textSizePx(context, config, referenceWidthPx)
        val typeface = typeface(config.fontFamily)
        val outlineStrokeWidth = (textSize * 0.1f).coerceIn(2f, 12f)
        val outlineAlpha = (config.opacity * 255f * OUTLINE_ALPHA_SCALE).toInt().coerceIn(0, 255)

        val outlinePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = outlineStrokeWidth
                color = Color.argb(outlineAlpha, 0, 0, 0)
                this.textSize = textSize
                this.typeface = typeface
            }
        val fillPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = resolveFillArgb(config)
                this.textSize = textSize
                this.typeface = typeface
            }
        return outlinePaint to fillPaint
    }

    private fun drawTextWithOutline(
        canvas: Canvas,
        text: String,
        x: Float,
        baselineY: Float,
        paints: Pair<Paint, Paint>,
    ) {
        val (outline, fill) = paints
        canvas.drawText(text, x, baselineY, outline)
        canvas.drawText(text, x, baselineY, fill)
    }

    fun drawOnCanvas(
        canvas: Canvas,
        config: WatermarkConfig,
        context: Context,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ) {
        if (config.text.isBlank()) return
        val paints = buildPaints(context, config, bitmapWidth)
        val fillPaint = paints.second
        val xPos = bitmapWidth * (config.x / 100f)
        val topLeftY = bitmapHeight * (config.y / 100f)
        val baselineY = topLeftY - fillPaint.fontMetrics.ascent
        drawTextWithOutline(canvas, config.text, xPos, baselineY, paints)
    }

    /**
     * Renders user [WatermarkConfig.text] into a bitmap for video [androidx.media3.effect.BitmapOverlay].
     */
    fun renderTextBitmap(
        context: Context,
        config: WatermarkConfig,
        referenceWidthPx: Int = 1080,
    ): Bitmap? {
        if (config.text.isBlank()) return null
        val paints = buildPaints(context, config, referenceWidthPx)
        val outlinePaint = paints.first
        val fillPaint = paints.second
        val bounds = Rect()
        fillPaint.getTextBounds(config.text, 0, config.text.length, bounds)
        val pad = (fillPaint.textSize * 0.25f).toInt().coerceAtLeast(4)
        val strokePad = outlinePaint.strokeWidth.toInt()
        val width = (bounds.width() + pad * 2 + strokePad * 2).coerceAtLeast(1)
        val height =
            (bounds.height() + pad * 2 + strokePad * 2)
                .coerceAtLeast((fillPaint.textSize * 1.3f).toInt())
                .coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val baselineY = pad - bounds.top.toFloat() + outlinePaint.strokeWidth
        drawTextWithOutline(canvas, config.text, pad.toFloat(), baselineY, paints)
        return bitmap
    }
}
