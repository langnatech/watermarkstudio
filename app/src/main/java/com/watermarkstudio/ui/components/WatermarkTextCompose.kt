package com.watermarkstudio.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.watermarkstudio.model.WatermarkConfig
import com.watermarkstudio.model.WatermarkFontFamily

fun WatermarkConfig.watermarkDisplayText(hint: String): String =
    if (text.isBlank()) hint else text

fun WatermarkConfig.watermarkTextStyle(): TextStyle {
    val (family, weight) =
        when (fontFamily) {
            WatermarkFontFamily.SANS -> FontFamily.SansSerif to FontWeight.Normal
            WatermarkFontFamily.SERIF -> FontFamily.Serif to FontWeight.Normal
            WatermarkFontFamily.MONOSPACE -> FontFamily.Monospace to FontWeight.Normal
            WatermarkFontFamily.BOLD -> FontFamily.Default to FontWeight.Bold
        }
    val rgb = color and 0x00FFFFFF
    return TextStyle(
        color = Color(rgb or 0xFF000000.toInt()).copy(alpha = opacity),
        fontSize = textSizeSp.sp,
        fontFamily = family,
        fontWeight = weight,
    )
}
