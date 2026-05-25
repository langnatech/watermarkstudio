package com.watermarkstudio.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkFontFamily
import com.watermarkstudio.util.TextWatermarkRenderer

fun WatermarkConfig.watermarkDisplayText(hint: String): String =
    if (text.isBlank()) hint else text

@Composable
fun WatermarkOutlinedText(
    text: String,
    config: WatermarkConfig,
    fontSize: TextUnit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val (family, weight) =
        when (config.fontFamily) {
            WatermarkFontFamily.SANS -> FontFamily.SansSerif to FontWeight.Normal
            WatermarkFontFamily.SERIF -> FontFamily.Serif to FontWeight.Normal
            WatermarkFontFamily.MONOSPACE -> FontFamily.Monospace to FontWeight.Normal
            WatermarkFontFamily.BOLD -> FontFamily.Default to FontWeight.Bold
        }
    val fillRgb = TextWatermarkRenderer.resolveFillRgb(config)
    val fillColor = Color(fillRgb or 0xFF000000.toInt()).copy(alpha = config.opacity)
    val outlineAlpha = (config.opacity * 0.72f).coerceIn(0f, 1f)
    val strokeWidth = (fontSize.value * 0.12f).coerceIn(1f, 6f)
    val outlineStyle =
        TextStyle(
            color = Color.Black.copy(alpha = outlineAlpha),
            fontSize = fontSize,
            fontFamily = family,
            fontWeight = weight,
            drawStyle =
                Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
        )
    val fillStyle =
        TextStyle(
            color = fillColor,
            fontSize = fontSize,
            fontFamily = family,
            fontWeight = weight,
        )
    Box(modifier = modifier) {
        androidx.compose.material3.Text(text = text, style = outlineStyle, maxLines = 3)
        androidx.compose.material3.Text(text = text, style = fillStyle, maxLines = 3)
    }
}

/** Matches [TextWatermarkRenderer.textSizePx] when reference width is fitted content width. */
fun WatermarkConfig.previewFontSizeSp(contentWidthPx: Float): TextUnit {
    if (contentWidthPx <= 0f) return textSizeSp.sp
    val scale = (contentWidthPx / 1080f).coerceIn(0.5f, 3f)
    return (textSizeSp * scale).sp
}
