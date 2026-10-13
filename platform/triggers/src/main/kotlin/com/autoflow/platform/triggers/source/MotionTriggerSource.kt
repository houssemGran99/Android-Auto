package com.autoflow.platform.triggers.source

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.autoflow.core.engine.trigger.TriggerSource
import com.autoflow.core.model.FlipPosition
import com.autoflow.core.model.TriggerEvent
import com.autoflow.core.model.TriggerFamily

/**
 * Shake and face down / face up gestures from the accelerometer (no permission needed).
 * The detection logic lives in [MotionDetector] so it can be unit tested.
 */
class MotionTriggerSource(context: Context) : TriggerSource {
    override val family = TriggerFamily.SENSORS

    private val sensorManager = context.applicationContext.getSystemService(SensorManager::class.java)
    private var listener: SensorEventListener? = null

    override fun register(onEvent: (TriggerEvent) -> Unit) {
        unregister()
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val detector = MotionDetector(onEvent)
        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                detector.onSample(event.values[0], event.values[1], event.values[2], event.timestamp / 1_000_000)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = newListener
        sensorManager.registerListener(newListener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun unregister() {
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
    }
}

/**
 * Pure gesture detection on accelerometer samples (m/s², timestamps in ms).
 * - Shake: two acceleration peaks above [MIN_SHAKE_G] within [SHAKE_WINDOW_MS]; the weaker peak is
 *   reported so each trigger can apply its own sensitivity. At most one shake per [SHAKE_COOLDOWN_MS].
 * - Flip: gravity mostly along -Z (face down) or +Z (face up) for [FLIP_STABLE_MS]. The first stable
 *   position is only a baseline, so starting the monitor never fires.
 */
class MotionDetector(private val onEvent: (TriggerEvent) -> Unit) {
    private var firstPeakAt = -1L
    private var firstPeakG = 0f
    private var lastShakeAt = Long.MIN_VALUE / 2
    private var candidate: FlipPosition? = null
    private var candidateSince = 0L
    private var reported: FlipPosition? = null
    private var baselineSet = false

    fun onSample(x: Float, y: Float, z: Float, timeMs: Long) {
        detectShake(x, y, z, timeMs)
        detectFlip(x, y, z, timeMs)
    }

    private fun detectShake(x: Float, y: Float, z: Float, timeMs: Long) {
        val g = kotlin.math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (g < MIN_SHAKE_G || timeMs - lastShakeAt < SHAKE_COOLDOWN_MS) return
        when {
            firstPeakAt < 0 || timeMs - firstPeakAt > SHAKE_WINDOW_MS -> {
                firstPeakAt = timeMs
                firstPeakG = g
            }
            timeMs - firstPeakAt >= MIN_PEAK_GAP_MS -> {
                lastShakeAt = timeMs
                firstPeakAt = -1
                onEvent(TriggerEvent.Shaken(minOf(firstPeakG, g)))
            }
            else -> firstPeakG = maxOf(firstPeakG, g) // same peak, still rising
        }
    }

    private fun detectFlip(x: Float, y: Float, z: Float, timeMs: Long) {
        val horizontal = kotlin.math.abs(x) < FLAT_TOLERANCE && kotlin.math.abs(y) < FLAT_TOLERANCE
        val position = when {
            horizontal && z < -FLAT_Z -> FlipPosition.FACE_DOWN
            horizontal && z > FLAT_Z -> FlipPosition.FACE_UP
            else -> null
        }
        if (position != candidate) {
            candidate = position
            candidateSince = timeMs
            return
        }
        if (position == null || position == reported || timeMs - candidateSince < FLIP_STABLE_MS) return
        reported = position
        if (baselineSet) onEvent(TriggerEvent.Flipped(position)) else baselineSet = true
    }

    companion object {
        /** The most sensitive trigger setting; weaker shakes are ignored. */
        const val MIN_SHAKE_G = 1.8f
        const val SHAKE_WINDOW_MS = 600L
        const val MIN_PEAK_GAP_MS = 120L
        const val SHAKE_COOLDOWN_MS = 1_500L
        const val FLIP_STABLE_MS = 800L
        private const val FLAT_Z = 8.5f
        private const val FLAT_TOLERANCE = 3.0f
    }
}
