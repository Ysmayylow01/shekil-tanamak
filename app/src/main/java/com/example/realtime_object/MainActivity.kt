package com.example.realtime_object

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.realtime_object.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.common.FileUtil
import java.util.Locale

class MainActivity : AppCompatActivity() {

    lateinit var labels: List<String>
    var colors = listOf<Int>(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED
    )
    val paint = Paint()
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap: Bitmap
    lateinit var imageView: ImageView
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var model: SsdMobilenetV11Metadata1
    lateinit var speechRecognizer: SpeechRecognizer
    lateinit var detectionTextView: TextView
    var detectedObjects = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        get_permission()

        // Initialize TensorFlow Lite model
        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR))
            .build()
        model = SsdMobilenetV11Metadata1.newInstance(this)

        // Initialize handler thread for camera
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        // Initialize views
        imageView = findViewById(R.id.imageView)
        detectionTextView = findViewById(R.id.detectionText)
        textureView = findViewById(R.id.textureView)

        // Setup TextureView listener
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                open_camera()
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                try {
                    bitmap = textureView.bitmap ?: return
                    var image = TensorImage.fromBitmap(bitmap)
                    image = imageProcessor.process(image)

                    val outputs = model.process(image)
                    val locations = outputs.locationsAsTensorBuffer.floatArray
                    val classes = outputs.classesAsTensorBuffer.floatArray
                    val scores = outputs.scoresAsTensorBuffer.floatArray

                    var mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = Canvas(mutable)

                    val h = mutable.height
                    val w = mutable.width
                    paint.textSize = h / 15f
                    paint.strokeWidth = h / 85f

                    detectedObjects.clear()

                    scores.forEachIndexed { index, fl ->
                        if (fl > 0.5) {
                            val classIdx = classes[index].toInt()
                            val label = if (classIdx < labels.size) labels[classIdx] else "Unknown"
                            detectedObjects.add("$label: ${String.format("%.1f%%", fl * 100)}")

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
                    updateDetectionText()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Initialize Speech Recognizer
        initializeSpeechRecognizer()
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {}

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val spokenText = matches[0].lowercase(Locale.getDefault())
                        announceDetectedObjects(spokenText)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun updateDetectionText() {
        runOnUiThread {
            detectionTextView.text = if (detectedObjects.isNotEmpty()) {
                "Tanalyan:\n" + detectedObjects.take(5).joinToString("\n")
            } else {
                "Objektler gözlenilmedi"
            }
        }
    }

    private fun announceDetectedObjects(voiceCommand: String) {
        if (voiceCommand.contains("ayt") || voiceCommand.contains("ne")) {
            val announcement = detectedObjects.take(3).joinToString(", ")
            runOnUiThread {
                detectionTextView.text = "Sesli: $announcement"
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun open_camera() {
        cameraManager.openCamera(
            cameraManager.cameraIdList[0],
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera

                    val surfaceTexture = textureView.surfaceTexture
                    val surface = Surface(surfaceTexture)

                    val captureRequest =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    captureRequest.addTarget(surface)

                    cameraDevice.createCaptureSession(
                        listOf(surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                session.setRepeatingRequest(captureRequest.build(), null, null)
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {}
                        },
                        handler
                    )
                }

                override fun onDisconnected(camera: CameraDevice) {}
                override fun onError(camera: CameraDevice, error: Int) {}
            },
            handler
        )
    }

    fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tk_TM")
        speechRecognizer.startListening(intent)
    }

    fun get_permission() {
        val permissions = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
        if (!permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            get_permission()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
        if (::cameraDevice.isInitialized) {
            cameraDevice.close()
        }
    }
}