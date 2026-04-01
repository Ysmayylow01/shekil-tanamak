package com.example.realtime_object

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.realtime_object.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

class GalleryActivity : AppCompatActivity() {

    lateinit var imageView: ImageView
    lateinit var detectionText: TextView
    lateinit var model: SsdMobilenetV11Metadata1
    lateinit var labels: List<String>
    lateinit var imageProcessor: ImageProcessor
    var colors = listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED
    )

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            processImage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        imageView = findViewById(R.id.galleryImageView)
        detectionText = findViewById(R.id.galleryDetectionText)

        // Initialize model
        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR))
            .build()
        model = SsdMobilenetV11Metadata1.newInstance(this)

        // Select image button
        findViewById<LinearLayout>(R.id.selectImageBtn).setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        // Back button
        findViewById<LinearLayout>(R.id.backBtn).setOnClickListener {
            finish()
        }
    }

    private fun processImage(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            var image = TensorImage.fromBitmap(bitmap)
            image = imageProcessor.process(image)

            val outputs = model.process(image)
            val locations = outputs.locationsAsTensorBuffer.floatArray
            val classes = outputs.classesAsTensorBuffer.floatArray
            val scores = outputs.scoresAsTensorBuffer.floatArray

            var mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutable)
            val paint = Paint()

            val h = mutable.height
            val w = mutable.width
            paint.textSize = h / 15f
            paint.strokeWidth = h / 85f

            val detectedList = mutableListOf<String>()

            scores.forEachIndexed { index, fl ->
                if (fl > 0.5) {
                    val classIdx = classes[index].toInt()
                    val label = if (classIdx < labels.size) labels[classIdx] else "Unknown"
                    detectedList.add("$label: ${String.format("%.1f%%", fl * 100)}")

                    val x = index * 4
                    paint.setColor(colors[index % colors.size])
                    paint.style = Paint.Style.STROKE
                    canvas.drawRect(
                        RectF(
                            locations[x + 1] * w,
                            locations[x] * h,
                            locations[x + 3] * w,
                            locations[x + 2] * h
                        ),
                        paint
                    )
                    paint.style = Paint.Style.FILL
                    canvas.drawText(
                        "$label ${String.format("%.2f", fl)}",
                        locations[x + 1] * w,
                        locations[x] * h,
                        paint
                    )
                }
            }

            imageView.setImageBitmap(mutable)
            detectionText.text = if (detectedList.isNotEmpty()) {
                "Tanalyan (${detectedList.size}):\n" + detectedList.take(5).joinToString("\n")
            } else {
                "Objektler gözlenilmedi"
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
    }
}