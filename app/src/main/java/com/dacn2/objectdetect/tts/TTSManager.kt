package com.dacn2.objectdetect.tts

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import com.dacn2.objectdetect.ml.BoundingBox
import com.dacn2.objectdetect.ml.DistanceLevel
import java.util.Locale

class TTSManager(
    private val context: Context,
    private val onInitComplete: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    private var isReady = false
    var isEnabled: Boolean = true

    private var lastSpokenTime: Long = 0
    private var lastSpokenText: String = ""
    private val minIntervalMs: Long = 2500 // Giãn cách giữa các lần đọc

    init {
        textToSpeech = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val viLocale = Locale("vi", "VN")
            val result = textToSpeech?.setLanguage(viLocale)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback về ngôn ngữ mặc định nếu máy chưa tải gói tiếng Việt
                textToSpeech?.language = Locale.getDefault()
            }
            textToSpeech?.setSpeechRate(1.1f) // Tăng nhẹ tốc độ đọc để cảnh báo nhanh
            textToSpeech?.setPitch(1.0f)
            isReady = true
            onInitComplete?.invoke(true)
        } else {
            isReady = false
            onInitComplete?.invoke(false)
        }
    }

    /**
     * Phân tích danh sách vật thể và đọc cảnh báo thông minh
     * Ưu tiên vật thể GẦN NHẤT và Ở TRƯỚC MẶT
     */
    fun processAndSpeak(boxes: List<BoundingBox>, onSpoken: ((String) -> Unit)? = null) {
        if (!isEnabled || !isReady || boxes.isEmpty()) return

        val now = SystemClock.uptimeMillis()

        // Sắp xếp ưu tiên: NEAR -> MEDIUM -> FAR, diện tích lớn -> nhỏ
        val priorityBox = boxes.minWithOrNull(
            compareBy(
                { it.distanceLevel.ordinal },
                { -it.w * it.h }
            )
        ) ?: return

        // Chỉ cảnh báo vật thể NEAR hoặc MEDIUM để tránh làm phiền
        if (priorityBox.distanceLevel == DistanceLevel.FAR) return

        val message = "Có ${priorityBox.labelVi} ở ${priorityBox.direction.vietnameseName}, ${priorityBox.distanceLevel.vietnameseName}"

        // Bỏ qua nếu vừa đọc câu tương tự trong thời gian ngắn
        if (message == lastSpokenText && (now - lastSpokenTime) < (minIntervalMs * 2)) {
            return
        }

        if (now - lastSpokenTime >= minIntervalMs) {
            speak(message, isUrgent = priorityBox.distanceLevel == DistanceLevel.NEAR)
            lastSpokenTime = now
            lastSpokenText = message
            onSpoken?.invoke(message)
        }
    }

    fun speak(text: String, isUrgent: Boolean = false) {
        if (!isEnabled || !isReady) return
        val queueMode = if (isUrgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        textToSpeech?.speak(text, queueMode, null, "ALERT_${System.currentTimeMillis()}")
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
