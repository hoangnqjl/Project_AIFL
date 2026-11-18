package com.dacn2.objectdetect.ml

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.FloatBuffer
import java.util.Collections
import kotlin.math.max
import kotlin.math.min

class YoloDetector(
    private val context: Context,
    private val modelPath: String = "yolov8n.onnx",
    private val labelPath: String = "labels.txt",
    private val confidenceThreshold: Float = 0.35f,
    private val iouThreshold: Float = 0.45f
) {
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var labels = mutableListOf<String>()

    private val inputWidth = 640
    private val inputHeight = 640
    private var isModelLoaded = false

    data class DetectionResult(
        val boundingBoxes: List<BoundingBox>,
        val inferenceTime: Long
    )

    init {
        setupDetector()
    }

    private fun setupDetector() {
        try {
            // 1. Đọc danh sách 80 nhãn COCO
            labels.clear()
            val inputStream = context.assets.open(labelPath)
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String? = reader.readLine()
            while (line != null) {
                if (line.isNotBlank()) {
                    labels.add(line.trim())
                }
                line = reader.readLine()
            }
            reader.close()

            // 2. Khởi tạo ONNX Runtime
            ortEnv = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            val modelBytes = context.assets.open(modelPath).readBytes()
            ortSession = ortEnv?.createSession(modelBytes, sessionOptions)

            isModelLoaded = true
        } catch (e: Exception) {
            e.printStackTrace()
            isModelLoaded = false
        }
    }

    fun detect(bitmap: Bitmap): DetectionResult {
        if (!isModelLoaded || ortSession == null || ortEnv == null) {
            return DetectionResult(emptyList(), 0L)
        }

        val startTime = SystemClock.uptimeMillis()

        // 1. Resize ảnh về 640x640
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)

        // 2. Chuẩn bị FloatBuffer NCHW (1, 3, 640, 640) chuẩn hóa [0.0f, 1.0f]
        val numPixels = inputWidth * inputHeight
        val floatBuffer = FloatBuffer.allocate(1 * 3 * numPixels)
        val intValues = IntArray(numPixels)
        resizedBitmap.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        // Tách 3 kênh R, G, B thành NCHW
        val rArray = FloatArray(numPixels)
        val gArray = FloatArray(numPixels)
        val bArray = FloatArray(numPixels)

        for (i in 0 until numPixels) {
            val pixel = intValues[i]
            rArray[i] = ((pixel shr 16) and 0xFF) / 255.0f
            gArray[i] = ((pixel shr 8) and 0xFF) / 255.0f
            bArray[i] = (pixel and 0xFF) / 255.0f
        }

        floatBuffer.put(rArray)
        floatBuffer.put(gArray)
        floatBuffer.put(bArray)
        floatBuffer.flip()

        // 3. Tạo Tensor và chạy inference
        val inputName = ortSession?.inputNames?.iterator()?.next() ?: "images"
        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            floatBuffer,
            longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        )

        val rawBoxes = mutableListOf<BoundingBox>()

        inputTensor.use { tensor ->
            val results = ortSession?.run(Collections.singletonMap(inputName, tensor))
            results?.use { outputResult ->
                val outputTensor = outputResult.get(0).value as? Array<Array<FloatArray>>
                if (outputTensor != null && outputTensor.isNotEmpty()) {
                    // YOLOv8 output: [1, 84, 8400]
                    val tensorData = outputTensor[0] // 84 x 8400
                    val numChannels = tensorData.size      // 84 (4 box coords + 80 class scores)
                    val numElements = tensorData[0].size   // 8400
                    val numClasses = numChannels - 4

                    for (i in 0 until numElements) {
                        var maxScore = -1.0f
                        var bestClass = -1

                        for (c in 0 until numClasses) {
                            val score = tensorData[4 + c][i]
                            if (score > maxScore) {
                                maxScore = score
                                bestClass = c
                            }
                        }

                        if (maxScore >= confidenceThreshold && bestClass in labels.indices) {
                            val cxPix = tensorData[0][i]
                            val cyPix = tensorData[1][i]
                            val wPix = tensorData[2][i]
                            val hPix = tensorData[3][i]

                            // Chuẩn hóa tọa độ [0..1]
                            val normCx = cxPix / inputWidth
                            val normCy = cyPix / inputHeight
                            val normW = wPix / inputWidth
                            val normH = hPix / inputHeight

                            val x1 = max(0f, normCx - normW / 2f)
                            val y1 = max(0f, normCy - normH / 2f)
                            val x2 = min(1f, normCx + normW / 2f)
                            val y2 = min(1f, normCy + normH / 2f)

                            val className = labels[bestClass]
                            val labelVi = CocoLabels.getVietnamese(className)

                            // Ước lượng khoảng cách
                            val area = normW * normH
                            val distanceLevel = when {
                                normH > 0.45f || area > 0.28f -> DistanceLevel.NEAR
                                normH > 0.20f || area > 0.07f -> DistanceLevel.MEDIUM
                                else -> DistanceLevel.FAR
                            }

                            // Xác định hướng (Trái / Giữa / Phải)
                            val direction = when {
                                normCx < 0.35f -> Direction.LEFT
                                normCx > 0.65f -> Direction.RIGHT
                                else -> Direction.CENTER
                            }

                            rawBoxes.add(
                                BoundingBox(
                                    x1 = x1,
                                    y1 = y1,
                                    x2 = x2,
                                    y2 = y2,
                                    cx = normCx,
                                    cy = normCy,
                                    w = normW,
                                    h = normH,
                                    score = maxScore,
                                    classIndex = bestClass,
                                    className = className,
                                    labelVi = labelVi,
                                    distanceLevel = distanceLevel,
                                    direction = direction
                                )
                            )
                        }
                    }
                }
            }
        }

        // Lọc trùng bằng NMS
        val finalBoxes = applyNMS(rawBoxes, iouThreshold)
        val inferenceTime = SystemClock.uptimeMillis() - startTime

        return DetectionResult(finalBoxes, inferenceTime)
    }

    private fun applyNMS(boxes: List<BoundingBox>, iouThreshold: Float): List<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.score }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while (sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.removeAt(0)
            selectedBoxes.add(first)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (calculateIoU(first, next) >= iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return selectedBoxes
    }

    private fun calculateIoU(a: BoundingBox, b: BoundingBox): Float {
        val xA = max(a.x1, b.x1)
        val yA = max(a.y1, b.y1)
        val xB = min(a.x2, b.x2)
        val yB = min(a.y2, b.y2)

        val intersectionArea = max(0f, xB - xA) * max(0f, yB - yA)
        val boxAArea = (a.x2 - a.x1) * (a.y2 - a.y1)
        val boxBArea = (b.x2 - b.x1) * (b.y2 - b.y1)

        val unionArea = boxAArea + boxBArea - intersectionArea
        return if (unionArea <= 0f) 0f else intersectionArea / unionArea
    }

    fun close() {
        try {
            ortSession?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ortSession = null
        ortEnv = null
    }
}
