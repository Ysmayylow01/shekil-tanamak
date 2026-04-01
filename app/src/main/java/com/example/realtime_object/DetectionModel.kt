package com.example.realtime_object

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import com.example.realtime_object.ml.SsdMobilenetV11Metadata1

data class DetectionResult(
    val label: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

class DetectionModel(context: Context) {

    private val model = SsdMobilenetV11Metadata1.newInstance(context)
    private val labels = FileUtil.loadLabels(context, "labels.txt")
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    fun detectObjects(bitmap: Bitmap, confidenceThreshold: Float = 0.5f): List<DetectionResult> {
        var image = TensorImage.fromBitmap(bitmap)
        image = imageProcessor.process(image)

        val outputs = model.process(image)
        val locations = outputs.locationsAsTensorBuffer.floatArray
        val classes = outputs.classesAsTensorBuffer.floatArray
        val scores = outputs.scoresAsTensorBuffer.floatArray

        val results = mutableListOf<DetectionResult>()

        scores.forEachIndexed { index, confidence ->
            if (confidence > confidenceThreshold) {
                val classIdx = classes[index].toInt()
                val label = if (classIdx < labels.size) labels[classIdx] else "Unknown"

                val x = index * 4
                results.add(
                    DetectionResult(
                        label = label,
                        confidence = confidence,
                        left = locations[x + 1],
                        top = locations[x],
                        right = locations[x + 3],
                        bottom = locations[x + 2]
                    )
                )
            }
        }

        return results.sortedByDescending { it.confidence }
    }

    fun close() {
        model.close()
    }
}