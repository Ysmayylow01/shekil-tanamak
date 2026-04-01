package com.example.realtime_object

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var detections: List<DetectionResult> = emptyList()
    private val colors = listOf(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED
    )

    fun setDetections(detections: List<DetectionResult>) {
        this.detections = detections
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paint = Paint().apply {
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }

        val textPaint = Paint().apply {
            textSize = 40f
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        val width = width.toFloat()
        val height = height.toFloat()

        detections.forEachIndexed { index, detection ->
            paint.color = colors[index % colors.size]

            val left = detection.left * width
            val top = detection.top * height
            val right = detection.right * width
            val bottom = detection.bottom * height

            canvas.drawRect(left, top, right, bottom, paint)

            textPaint.color = colors[index % colors.size]
            val label = "${detection.label} ${String.format("%.1f%%", detection.confidence * 100)}"
            canvas.drawText(label, left, top - 10, textPaint)
        }
    }
}