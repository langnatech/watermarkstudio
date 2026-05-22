package com.watermarkstudio

import com.watermarkstudio.removal.video.FfmpegRemuxHelper
import org.junit.Assert.assertNotNull
import org.junit.Test

class FfmpegRemuxHelperTest {

    @Test
    fun isAvailable_doesNotThrow() {
        val available = FfmpegRemuxHelper.isAvailable()
        assertNotNull(available)
    }
}
