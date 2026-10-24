package com.jpcottin.lenslate.data.camera

import android.util.Size
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** The Read capture is bounded for OCR, never the sensor's full size. */
@RunWith(AndroidJUnit4::class)
class CameraXFrameCaptureTest {
    @Test
    fun readCapture_isBoundedForOcr_notTheSensorMaximum() {
        val strategy = CameraXFrameCapture.imageCapture().resolutionSelector?.resolutionStrategy
        assertNotNull("Read mode must bound the capture size", strategy)
        assertEquals(Size(1920, 1080), strategy!!.boundSize)
        assertEquals(ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER, strategy.fallbackRule)
    }
}
