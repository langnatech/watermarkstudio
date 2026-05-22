package com.watermarkstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.watermarkstudio.removal.video.VideoExportMuxer
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VideoExportMuxerInstrumentedTest {

    @Test
    fun export_emptyFrames_returnsFalse() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val out = File(context.cacheDir, "empty_mux_test.mp4")
        val ok =
            VideoExportMuxer.export(
                context = context,
                sourceUri = android.net.Uri.EMPTY,
                frames = emptyList(),
                fps = 12f,
                clipDurationUs = 1_000_000L,
                outputFile = out,
                includeAudio = false,
            )
        assertFalse(ok)
        out.delete()
    }
}
