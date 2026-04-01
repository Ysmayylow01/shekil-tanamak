package com.example.realtime_object

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.realtime_object.ml.SsdMobilenetV11Metadata1
import com.google.android.material.button.MaterialButton
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.*

class GalleryActivity : ComponentActivity() {

    private lateinit var imageView: ImageView
    private lateinit var btnSelectImage: MaterialButton
    private lateinit var btnDetect: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View

    private lateinit var model: SsdMobilenetV11Metadata1
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var labels: List<String>

    private lateinit var tts: TextToSpeech
    private var isTtsReady = false

    private var selectedBitmap: Bitmap? = null
    private val paint = Paint()
    private val colors = listOf(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN,
        Color.YELLOW, Color.MAGENTA, Color.WHITE, Color.CYAN
    )

    private val detectionResults = mutableListOf<DetectionResult>()
    private lateinit var adapter: DetectionResultAdapter

    // Gallery picker launcher
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                loadImageFromUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        initViews()
        initTTS()
        initModel()
        setupRecyclerView()
        setupListeners()
    }

    private fun initViews() {
        imageView = findViewById(R.id.imageViewGallery)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnDetect = findViewById(R.id.btnDetect)
        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.recyclerViewResults)
        emptyState = findViewById(R.id.emptyState)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }
    }

    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                isTtsReady = true
            }
        }
    }

    private fun initModel() {
        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR))
            .build()
        model = SsdMobilenetV11Metadata1.newInstance(this)
    }

    private fun setupRecyclerView() {
        adapter = DetectionResultAdapter(detectionResults)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        btnSelectImage.setOnClickListener {
            openGallery()
        }

        btnDetect.setOnClickListener {
            selectedBitmap?.let { bitmap ->
                detectObjects(bitmap)
            }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        galleryLauncher.launch(intent)
    }

    @SuppressLint("Range")
    private fun loadImageFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Fix rotation if needed
            val rotatedBitmap = fixImageRotation(uri, bitmap)

            selectedBitmap = rotatedBitmap
            imageView.setImageBitmap(rotatedBitmap)
            btnDetect.isEnabled = true

            // Clear previous results
            detectionResults.clear()
            adapter.notifyDataSetChanged()
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE

        } catch (e: Exception) {
            Toast.makeText(this, "Surat ýüklänlikde ýalňyşlyk: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("Range")
    private fun fixImageRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val projection = arrayOf(MediaStore.Images.Media.ORIENTATION)
        val cursor = contentResolver.query(uri, projection, null, null, null)
        var rotation = 0

        cursor?.use {
            if (it.moveToFirst()) {
                rotation = it.getInt(it.getColumnIndex(MediaStore.Images.Media.ORIENTATION))
            }
        }

        if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun detectObjects(originalBitmap: Bitmap) {
        progressBar.visibility = View.VISIBLE
        btnDetect.isEnabled = false

        // Run on background thread
        Thread {
            try {
                // Resize for model input (300x300)
                val modelInput = Bitmap.createScaledBitmap(originalBitmap, 300, 300, true)

                // Process image
                var tensorImage = TensorImage.fromBitmap(modelInput)
                tensorImage = imageProcessor.process(tensorImage)

                // Run inference
                val outputs = model.process(tensorImage)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray

                // Scale factors to map back to original image
                val scaleX = originalBitmap.width.toFloat() / 300f
                val scaleY = originalBitmap.height.toFloat() / 300f

                // Create mutable copy for drawing
                val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutableBitmap)

                // Setup paint
                paint.textSize = originalBitmap.height / 25f
                paint.strokeWidth = originalBitmap.height / 120f
                paint.isAntiAlias = true

                detectionResults.clear()
                val detectedLabels = mutableListOf<String>()

                for (i in scores.indices) {
                    val score = scores[i]
                    if (score > 0.5) {
                        val classIndex = classes[i].toInt()
                        val label = labels.getOrElse(classIndex) { "Unknown" }
                        val x = i * 4

                        // Scale coordinates back to original image
                        val left = locations[x + 1] * 300f * scaleX
                        val top = locations[x] * 300f * scaleY
                        val right = locations[x + 3] * 300f * scaleX
                        val bottom = locations[x + 2] * 300f * scaleY

                        val color = colors[i % colors.size]

                        // Draw bounding box
                        paint.color = color
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(left, top, right, bottom, paint)

                        // Draw label background
                        val text = "$label ${"%.1f".format(score * 100)}%"
                        val textBounds = Rect()
                        paint.getTextBounds(text, 0, text.length, textBounds)

                        paint.style = Paint.Style.FILL
                        paint.alpha = 200
                        canvas.drawRect(
                            left,
                            top - textBounds.height() - 8f,
                            left + textBounds.width() + 16f,
                            top,
                            paint
                        )

                        // Draw text
                        paint.alpha = 255
                        paint.color = Color.WHITE
                        canvas.drawText(text, left + 8f, top - 8f, paint)

                        // Add to results
                        detectionResults.add(
                            DetectionResult(
                                label = label,
                                confidence = score,
                                color = color,
                                boundingBox = RectF(left, top, right, bottom)
                            )
                        )

                        if (label !in detectedLabels) {
                            detectedLabels.add(label)
                        }
                    }
                }

                // Update UI
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnDetect.isEnabled = true
                    imageView.setImageBitmap(mutableBitmap)

                    if (detectionResults.isNotEmpty()) {
                        emptyState.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        adapter.notifyDataSetChanged()

                        // Speak detected objects
                        speakDetectedObjects(detectedLabels)
                    } else {
                        Toast.makeText(this, "Hiç obýekt tanylmady", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnDetect.isEnabled = true
                    Toast.makeText(this, "Ýalňyşlyk: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun speakDetectedObjects(objects: List<String>) {
        if (isTtsReady && objects.isNotEmpty()) {
            val text = when {
                objects.size == 1 -> "Found ${objects[0]}"
                objects.size == 2 -> "Found ${objects[0]} and ${objects[1]}"
                else -> "Found ${objects.take(3).joinToString(", ")} and others"
            }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
        tts.stop()
        tts.shutdown()
    }
}

// Data class for detection results
data class DetectionResult(
    val label: String,
    val confidence: Float,
    val color: Int,
    val boundingBox: RectF
)