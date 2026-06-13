package com.watermarkstudio.removal.mask

import kotlin.math.min

/** Shared brush stroke sizing for mask rasterization and UI overlay. */
object BrushStrokeGeometry {

    private const val MIN_STROKE_RADIUS_PX = 2f

    fun mediaShortEdge(width: Int, height: Int): Int = min(width, height)

    fun mediaShortEdge(width: Float, height: Float): Float = min(width, height)

    /** Diameter in **media** pixels (radiusPct is % of the shorter media edge). */
    fun strokeDiameterPx(width: Int, height: Int, radiusPct: Float): Float {
        val shortEdge = mediaShortEdge(width, height)
        return (shortEdge * radiusPct / 100f * 2f).coerceAtLeast(MIN_STROKE_RADIUS_PX * 2f)
    }

    fun strokeDiameterPx(width: Float, height: Float, radiusPct: Float): Float {
        val shortEdge = mediaShortEdge(width, height)
        return (shortEdge * radiusPct / 100f * 2f).coerceAtLeast(MIN_STROKE_RADIUS_PX * 2f)
    }

    fun strokeRadiusPx(width: Int, height: Int, radiusPct: Float): Float =
        strokeDiameterPx(width, height, radiusPct) / 2f
}
