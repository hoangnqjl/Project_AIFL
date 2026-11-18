package com.dacn2.objectdetect.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.dacn2.objectdetect.ml.BoundingBox
import com.dacn2.objectdetect.ml.DistanceLevel
import kotlin.math.max

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: List<BoundingBox> = emptyList()

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val textBgPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 34f
        isAntiAlias = true
        isFakeBoldText = true
    }

    fun setResults(detectionResults: List<BoundingBox>) {
        this.results = detectionResults
        postInvalidate()
    }

    fun clear() {
        this.results = emptyList()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        for (box in results) {
            // Chuyển tọa độ chuẩn hóa [0..1] sang pixel màn hình
            val left = box.x1 * viewWidth
            val top = box.y1 * viewHeight
            val right = box.x2 * viewWidth
            val bottom = box.y2 * viewHeight

            // Chọn màu theo mức độ khoảng cách
            val color = when (box.distanceLevel) {
                DistanceLevel.NEAR -> Color.parseColor("#F44336")   // Đỏ - Cực gần
                DistanceLevel.MEDIUM -> Color.parseColor("#FF9800") // Cam - Vừa
                DistanceLevel.FAR -> Color.parseColor("#4CAF50")    // Xanh - Xa
            }

            boxPaint.color = color
            boxPaint.strokeWidth = when (box.distanceLevel) {
                DistanceLevel.NEAR -> 8f
                DistanceLevel.MEDIUM -> 6f
                DistanceLevel.FAR -> 4f
            }

            // Vẽ viền bounding box
            val rect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(rect, 12f, 12f, boxPaint)

            // Chuẩn bị nhãn tiếng Việt + Khoảng cách + Confidence
            val scorePercent = (box.score * 100).toInt()
            val labelText = "${box.labelVi} (${box.distanceLevel.vietnameseName}) - $scorePercent%"

            val textWidth = textPaint.measureText(labelText)
            val textHeight = textPaint.textSize

            // Vẽ nền cho nhãn chữ (Badge)
            val badgeHeight = textHeight + 20f
            val badgeWidth = textWidth + 24f
            val badgeTop = max(0f, top - badgeHeight)
            val badgeRect = RectF(left, badgeTop, left + badgeWidth, badgeTop + badgeHeight)

            textBgPaint.color = color
            canvas.drawRoundRect(badgeRect, 8f, 8f, textBgPaint)

            // Vẽ chữ
            canvas.drawText(
                labelText,
                left + 12f,
                badgeTop + textHeight + 2f,
                textPaint
            )
        }
    }
}
