package com.watermarkstudio.removal

/** Sub-stage progress within a single remove item (0f..1f). */
fun interface RemovalProgress {
    fun report(fraction: Float)
}
