package com.dacn2.objectdetect.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

class FallDetector(
    private val context: Context,
    private val onFallDetected: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    var isEnabled: Boolean = true

    // Ngưỡng phát hiện
    private val freeFallThreshold = 3.0f // m/s^2 (Gia tốc giảm mạnh khi bắt đầu rơi tự do)
    private val impactThreshold = 24.0f  // m/s^2 (Xung chấn va chạm cực mạnh với mặt đất)

    private var inFreeFall = false
    private var freeFallTimestamp: Long = 0
    private var lastFallDetectedTime: Long = 0
    private val fallCooldownMs = 5000L // Tránh kích hoạt liên tiếp

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isEnabled || event == null) return

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]

            val totalAcc = sqrt(ax * ax + ay * ay + az * az)
            val currentTime = SystemClock.uptimeMillis()

            // 1. Kiểm tra giai đoạn rơi tự do (Free fall)
            if (totalAcc < freeFallThreshold) {
                inFreeFall = true
                freeFallTimestamp = currentTime
            }

            // 2. Kiểm tra va chạm mạnh sau rơi tự do trong khoảng 100ms - 800ms
            if (inFreeFall && (currentTime - freeFallTimestamp in 100..800)) {
                if (totalAcc > impactThreshold) {
                    if (currentTime - lastFallDetectedTime > fallCooldownMs) {
                        lastFallDetectedTime = currentTime
                        inFreeFall = false
                        onFallDetected.invoke()
                    }
                }
            }

            // Reset trạng thái nếu quá thời gian va chạm
            if (currentTime - freeFallTimestamp > 1000) {
                inFreeFall = false
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Không yêu cầu xử lý
    }
}
