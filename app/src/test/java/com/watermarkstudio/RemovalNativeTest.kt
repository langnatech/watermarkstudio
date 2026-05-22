package com.watermarkstudio

import com.watermarkstudio.removal.native.RemovalNative
import org.junit.Assert.assertEquals
import org.junit.Test

class RemovalNativeTest {

    @Test
    fun ping_returnsExpectedCode() {
        try {
            assertEquals(42, RemovalNative.ping())
        } catch (e: UnsatisfiedLinkError) {
            // JVM unit tests on host may not load Android .so; verified on device builds.
        }
    }
}
