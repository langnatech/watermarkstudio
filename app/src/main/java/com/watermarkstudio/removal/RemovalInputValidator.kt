package com.watermarkstudio.removal

import com.watermarkstudio.model.WatermarkConfig

object RemovalInputValidator {

    fun hasPaintedMask(config: WatermarkConfig): Boolean =
        config.removalStrokes.isNotEmpty()
}
