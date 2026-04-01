package com.example.realtime_object

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.realtime_object.ml.SsdMobilenetV11Metadata1
import com.google.android.material.button.MaterialButton
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VideoActivity : ComponentActivity() {

    private lateinit var videoView: VideoView
    private lateinit var ivVideoThumbnail: ImageView
    private lateinit var ivFramePreview: ImageView
    private lateinit var btnSelectVideo: MaterialButton
    private lateinit var btnProcess: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvFrameCount: TextView
    private lateinit var tvObjectCount: TextView
    private lateinit var tvFps: TextView

    private lateinit var model: SsdMobilenetV11Metadata1
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var labels: List<String>

    private lateinit var tts: TextToSpeech
    private var isTtsReady = false

    private var videoUri: Uri? = null
    private var isProcessing = false
    private var shouldStopProcessing = false
    private val paint = Paint()
    private val colors = listOf(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN,
        Color.YELLOW, Color.MAGENTA, Color.WHITE
    )

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private val videoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                loadVideo(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)

        initViews()
        initTTS()
        initModel()
        setupListeners()
    }

    private fun initViews() {
        videoView = findViewById(R.id.videoView)
        ivVideoThumbnail = findViewById(R.id.ivVideoThumbnail)
        ivFramePreview = findViewById(R.id.ivFramePreview)
        btnSelectVideo = findViewById(R.id.btnSelectVideo)
        btnProcess = findViewById(R.id.btnProcess)
        btnStop = findViewById(R.id.btnStop)
        progressBar = findViewById(R.id.progressBar)
        tvFrameCount = findViewById(R.id.tvFrameCount)
        tvObjectCount = findViewById(R.id.tvObjectCount)
        tvFps = findViewById(R.id.tvFps)

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

    private fun setupListeners() {
        btnSelectVideo.setOnClickListener {
            openVideoPicker()
        }

        btnProcess.setOnClickListener {
            startVideoProcessing()
        }

        btnStop.setOnClickListener {
            stopProcessing()
        }
    }

    private fun openVideoPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        intent.type = "video/*"
        videoLauncher.launch(intent)
    }

    @SuppressLint("SetTextI18n")
    private fun loadVideo(uri: Uri) {
        videoUri = uri
        videoView.setVideoURI(uri)
        ivVideoThumbnail.visibility = View.GONE
        videoView.visibility = View.VISIBLE

        // Get thumbnail
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(this, uri)
        val thumbnail = retriever.getFrameAtTime(0)
        ivFramePreview.setImageBitmap(thumbnail)
        retriever.release()

        btnProcess.isEnabled = true

        videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.isLooping = true
        }
    }

    @SuppressLint("SetTextI18n")
    private fun startVideoProcessing() {
        val uri = videoUri ?: return

        isProcessing = true
        shouldStopProcessing = false
        btnProcess.isEnabled = false
        btnStop.isEnabled = true
        progressBar.visibility = View.VISIBLE

        executor.execute {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)

            // Get video info
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toInt() ?: 30

            // Process every Nth frame (for performance)
            val frameIntervalMs = 500 // Every 500ms
            var currentTime = 0L
            var frameCount = 0
            var totalObjects = 0
            var lastFpsTime = System.currentTimeMillis()
            var fpsCount = 0

            while (currentTime < duration && !shouldStopProcessing) {
                val startTime = System.currentTimeMillis()

                // Get frame at current time
                val bitmap = retriever.getFrameAtTime(currentTime * 1000, MediaMetadataRetriever.OPTION_CLOSEST)

                if (bitmap != null) {
                    val processedBitmap = processFrame(bitmap)
                    val objectCount = countObjectsInFrame(bitmap)

                    totalObjects += objectCount
                    frameCount++
                    fpsCount++

                    // Update FPS every second
                    if (System.currentTimeMillis() - lastFpsTime >= 1000) {
                        val fps = fpsCount
                        fpsCount = 0
                        lastFpsTime = System.currentTimeMillis()

                        handler.post {
                            tvFps.text = fps.toString()
                        }
                    }

                    // Update UI
                    handler.post {
                        ivFramePreview.setImageBitmap(processedBitmap)
                        tvFrameCount.text = frameCount.toString()
                        tvObjectCount.text = totalObjects.toString()

                        // Speak detected objects occasionally
                        if (frameCount % 10 == 0 && objectCount > 0) {
                            speak("Found $objectCount objects")
                        }
                    }

                    bitmap.recycle()
                }

                // Control processing speed
                val processingTime = System.currentTimeMillis() - startTime
                val sleepTime = maxOf(0, frameIntervalMs - processingTime)
                Thread.sleep(sleepTime)

                currentTime += frameIntervalMs
            }

            retriever.release()

            handler.post {
                onProcessingComplete()
            }
        }
    }

    private fun processFrame(bitmap: Bitmap): Bitmap {
        // Resize for model
        val modelInput = Bitmap.createScaledBitmap(bitmap, 300, 300, true)

        var tensorImage = TensorImage.fromBitmap(modelInput)
        tensorImage = imageProcessor.process(tensorImage)

        val outputs = model.process(tensorImage)
        val locations = outputs.locationsAsTensorBuffer.floatArray
        val classes = outputs.classesAsTensorBuffer.floatArray
        val scores = outputs.scoresAsTensorBuffer.floatArray

        // Scale factors
        val scaleX = bitmap.width.toFloat() / 300f
        val scaleY = bitmap.height.toFloat() / 300f

        // Draw on original size bitmap
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)

        paint.textSize = bitmap.height / 20f
        paint.strokeWidth = bitmap.height / 100f
        paint.isAntiAlias = true

        for (i in scores.indices) {
            val score = scores[i]
            if (score > 0.5) {
                val x = i * 4
                val left = locations[x + 1] * 300f * scaleX
                val top = locations[x] * 300f * scaleY
                val right = locations[x + 3] * 300f * scaleX
                val bottom = locations[x + 2] * 300f * scaleY

                paint.color = colors[i % colors.size]
                paint.style = Paint.Style.STROKE
                canvas.drawRect(left, top, right, bottom, paint)

                val label = labels[classes[i].toInt()]
                paint.style = Paint.Style.FILL
                paint.alpha = 200
                canvas.drawRect(left, top - 40, left + 200, top, paint)

                paint.alpha = 255
                paint.color = Color.WHITE
                canvas.drawText("$label ${"%.0f".format(score * 100)}%", left + 10, top - 10, paint)
            }
        }

        return mutable
    }

    private fun countObjectsInFrame(bitmap: Bitmap): Int {
        val modelInput = Bitmap.createScaledBitmap(bitmap, 300, 300, true)
        var tensorImage = TensorImage.fromBitmap(modelInput)
        tensorImage = imageProcessor.process(tensorImage)

        val outputs = model.process(tensorImage)
        val scores = outputs.scoresAsTensorBuffer.floatArray

        return scores.count { it > 0.5 }
    }

    private fun speak(text: String) {
        if (isTtsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun stopProcessing() {
        shouldStopProcessing = true
    }

    @SuppressLint("SetTextI18n")
    private fun onProcessingComplete() {
        isProcessing = false
        btnProcess.isEnabled = true
        btnStop.isEnabled = false
        progressBar.visibility = View.GONE

        Toast.makeText(this, "Wideo analiz tamamlandy!", Toast.LENGTH_SHORT).show()
        speak("Video analysis complete")
    }

    override fun onDestroy() {
        super.onDestroy()
        shouldStopProcessing = true
        executor.shutdown()
        model.close()
        tts.stop()
        tts.shutdown()
    }
}