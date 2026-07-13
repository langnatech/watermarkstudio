package com.watermarkstudio

import com.watermarkstudio.model.RemovalStroke
import com.watermarkstudio.model.RemovalStrokePoint
import com.watermarkstudio.model.dropLastStrokeOrBatch
import org.junit.Assert.assertEquals
import org.junit.Test

class RemovalStrokeBatchUndoTest {

    @Test
    fun dropLastStrokeOrBatch_removesEntireSmartBatch() {
        val strokes =
            listOf(
                RemovalStroke(listOf(RemovalStrokePoint(1f, 1f)), 2f),
                RemovalStroke(listOf(RemovalStrokePoint(2f, 2f)), 0.5f, batchId = 99L),
                RemovalStroke(listOf(RemovalStrokePoint(3f, 3f)), 0.5f, batchId = 99L),
                RemovalStroke(listOf(RemovalStrokePoint(4f, 4f)), 0.5f, batchId = 99L),
            )
        val undone = strokes.dropLastStrokeOrBatch()
        assertEquals(1, undone.size)
        assertEquals(1f, undone.first().points.first().xPct)
    }

    @Test
    fun dropLastStrokeOrBatch_removesSingleManualStroke() {
        val strokes =
            listOf(
                RemovalStroke(listOf(RemovalStrokePoint(1f, 1f)), 2f),
                RemovalStroke(listOf(RemovalStrokePoint(2f, 2f)), 2f),
            )
        val undone = strokes.dropLastStrokeOrBatch()
        assertEquals(1, undone.size)
    }
}
