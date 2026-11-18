package com.dacn2.objectdetect.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.dacn2.objectdetect.databinding.ActivityMainBinding
import com.dacn2.objectdetect.ml.YoloDetector
import com.dacn2.objectdetect.sensors.FallDetector
import com.dacn2.objectdetect.tts.TTSManager
import com.dacn2.objectdetect.utils.ImageUtils
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    private var yoloDetector: YoloDetector? = null
    private var ttsManager: TTSManager? = null
    private var fallDetector: FallDetector? = null

    private var frameCount = 0
    private var lastFpsTimestamp = 0L

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Vui lòng cấp quyền Camera để sử dụng tính năng nhận diện!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // 1. Khởi tạo YOLO Detector
        yoloDetector = YoloDetector(this)

        // 2. Khởi tạo Giọng nói Tiếng Việt (TTS)
        ttsManager = TTSManager(this) { ready ->
            if (ready) {
                runOnUiThread {
                    binding.tvSpeechOutput.text = "Hệ thống cảnh báo bằng giọng nói tiếng Việt đã sẵn sàng."
                }
            }
        }

        // 3. Khởi tạo Cảm biến phát hiện té ngã
        fallDetector = FallDetector(this) {
            onFallDetected()
        }
        fallDetector?.start()

        // 4. Thiết lập UI & Sự kiện tương tác
        setupControls()

        // 5. Kiểm tra quyền Camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupControls() {
        binding.switchTTS.setOnCheckedChangeListener { _, isChecked ->
            ttsManager?.isEnabled = isChecked
            val status = if (isChecked) "Bật" else "Tắt"
            Toast.makeText(this, "Đã $status giọng nói cảnh báo", Toast.LENGTH_SHORT).show()
        }

        binding.switchFallDetection.setOnCheckedChangeListener { _, isChecked ->
            fallDetector?.isEnabled = isChecked
            val status = if (isChecked) "Bật" else "Tắt"
            Toast.makeText(this, "Đã $status phát hiện té ngã", Toast.LENGTH_SHORT).show()
        }

        binding.btnDismissFall.setOnClickListener {
            binding.cardFallAlert.visibility = View.GONE
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: androidx.camera.core.ImageProxy) {
        val bitmap = ImageUtils.imageProxyToBitmap(imageProxy)
        imageProxy.close()

        val result = yoloDetector?.detect(bitmap) ?: return

        // Tính FPS
        frameCount++
        val now = System.currentTimeMillis()
        if (lastFpsTimestamp == 0L) lastFpsTimestamp = now
        val elapsed = now - lastFpsTimestamp
        val currentFps = if (elapsed >= 1000) {
            val fps = (frameCount * 1000.0f) / elapsed
            frameCount = 0
            lastFpsTimestamp = now
            fps
        } else null

        // Xử lý phát giọng nói tiếng Việt
        ttsManager?.processAndSpeak(result.boundingBoxes) { spokenText ->
            runOnUiThread {
                binding.tvSpeechOutput.text = "🗣️ $spokenText"
            }
        }

        // Cập nhật giao diện Overlay và Dashboard
        runOnUiThread {
            binding.overlayView.setResults(result.boundingBoxes)
            binding.tvInference.text = "Xử lý: ${result.inferenceTime} ms"
            binding.tvCount.text = "Vật thể: ${result.boundingBoxes.size}"
            currentFps?.let {
                binding.tvFps.text = "FPS: %.1f".format(it)
            }
        }
    }

    private fun onFallDetected() {
        runOnUiThread {
            binding.cardFallAlert.visibility = View.VISIBLE

            // Rung điện thoại cảnh báo
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(1000)
            }

            // Đọc giọng nói khẩn cấp
            ttsManager?.speak("Cảnh báo khẩn cấp: Phát hiện người dùng có thể đã bị ngã!", isUrgent = true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        fallDetector?.stop()
        ttsManager?.shutdown()
        yoloDetector?.close()
    }
}
