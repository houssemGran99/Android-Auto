package com.autoflow.platform.triggers

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoflow.core.model.FlipPosition
import com.autoflow.core.model.TriggerEvent
import com.autoflow.platform.triggers.source.MotionDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MotionDetectorTest {
    private val events = mutableListOf<TriggerEvent>()
    private val detector = MotionDetector { events += it }

    private fun still(z: Float, fromMs: Long, toMs: Long) {
        var t = fromMs
        while (t <= toMs) {
            detector.onSample(0f, 0f, z, t)
            t += 50
        }
    }

    @Test
    fun firstStablePositionIsBaselineThenFlipsFire() {
        still(9.8f, 0, 1_000) // face up at start: baseline, no event
        assertTrue(events.isEmpty())
        still(-9.8f, 1_050, 2_000)
        assertEquals(listOf(TriggerEvent.Flipped(FlipPosition.FACE_DOWN)), events)
        still(9.8f, 2_050, 3_000)
        assertEquals(TriggerEvent.Flipped(FlipPosition.FACE_UP), events.last())
    }

    @Test
    fun shortTiltDoesNotFlip() {
        still(9.8f, 0, 1_000)
        still(-9.8f, 1_050, 1_300) // less than the stable time
        still(9.8f, 1_350, 2_500)
        assertTrue(events.isEmpty())
    }

    @Test
    fun twoPeaksMakeOneShakeReportingTheWeakerPeak() {
        detector.onSample(0f, 25f, 0f, 10_000) // ~2.6 g
        detector.onSample(0f, 0f, 9.8f, 10_100)
        detector.onSample(0f, -20f, 0f, 10_300) // ~2.0 g
        detector.onSample(0f, 30f, 0f, 10_500) // cooldown: ignored
        val shakes = events.filterIsInstance<TriggerEvent.Shaken>()
        assertEquals(1, shakes.size)
        assertTrue(shakes.single().peakG in 1.9f..2.2f)
    }

    @Test
    fun singleBumpIsNotAShake() {
        detector.onSample(0f, 25f, 0f, 0)
        detector.onSample(0f, 0f, 9.8f, 100)
        detector.onSample(0f, 25f, 0f, 2_000) // outside the window: new first peak
        assertTrue(events.none { it is TriggerEvent.Shaken })
    }
}
