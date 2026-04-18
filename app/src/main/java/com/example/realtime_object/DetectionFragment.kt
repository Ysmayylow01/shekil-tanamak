package com.example.realtime_object

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Size
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.realtime_object.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.*

class DetectionFragment : Fragment() {

    private lateinit var labels: List<String>
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var imageView: ImageView
    private lateinit var textureView: TextureView
    private lateinit var txt: TextView
    private lateinit var model: SsdMobilenetV11Metadata1

    private var cameraDevice: CameraDevice? = null
    private lateinit var handler: Handler
    private lateinit var cameraManager: CameraManager

    private lateinit var tts: TextToSpeech
    private var isTtsReady = false
    private var isSpeaking = false
    private var lastSpeakTime = 0L

    // 🔥 Häzirki frame-de tapylan obýektler (label + count)
    private var currentDetections = listOf<String>()

    private var previewSize: Size? = null

    private val paint = Paint()
    private val colors = listOf(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN,
        Color.GRAY, Color.BLACK, Color.DKGRAY,
        Color.MAGENTA, Color.YELLOW, Color.RED
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_detection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageView = view.findViewById(R.id.imageView)
        textureView = view.findViewById(R.id.textureView)
        txt = view.findViewById(R.id.txtView)

        getPermission()

        tts = TextToSpeech(requireContext()) {
            if (it == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                isTtsReady = true

                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { isSpeaking = true }
                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        Handler(Looper.getMainLooper()).post { txt.text = "" }
                    }
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        Handler(Looper.getMainLooper()).post { txt.text = "" }
                    }
                })
            }
        }

        labels = FileUtil.loadLabels(requireContext(), "labels.txt")

        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR))
            .build()

        model = SsdMobilenetV11Metadata1.newInstance(requireContext())

        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // 🔥 Kamera üstüne basanda — HEMME tapylan obýektleri ses bilen aýt
        textureView.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastSpeakTime < 3000) return@setOnClickListener
            if (isSpeaking) return@setOnClickListener
            lastSpeakTime = now

            val detections = currentDetections
            if (detections.isNotEmpty()) {
                // 🔥 Sanly: "2 persons, 1 bottle, 1 laptop"
                val summary = buildDetectionSummary(detections)
                txt.text = summary
                speakText(summary)
            } else {
                val noDetection = "No objects detected"
                txt.text = noDetection
                speakText(noDetection)
            }
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture, width: Int, height: Int
            ) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture, width: Int, height: Int
            ) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                processFrame()
            }
        }
    }

    // ===== DETECTION SUMMARY =====

    /**
     * ["person", "person", "bottle", "laptop"]
     *  → "I can see: 2 persons, 1 bottle, 1 laptop"
     */
    private fun buildDetectionSummary(detections: List<String>): String {
        // groupBy → her label-den näçe sany bar
        val counts = detections.groupingBy { it }.eachCount()

        val parts = counts.map { (label, count) ->
            if (count > 1) "$count ${label}s" else "1 $label"
        }

        return "I can see: ${parts.joinToString(", ")}"
    }

    // ===== KAMERA ÖLÇEG =====

    private fun chooseOptimalSize(choices: Array<Size>, viewWidth: Int, viewHeight: Int): Size {
        val targetRatio = viewHeight.toDouble() / viewWidth.toDouble()

        return choices
            .filter { it.width >= 640 }
            .minByOrNull {
                val ratio = it.width.toDouble() / it.height.toDouble()
                Math.abs(ratio - targetRatio)
            } ?: choices[0]
    }

    private fun applyCenterCropTransform(viewWidth: Int, viewHeight: Int) {
        val size = previewSize ?: return

        val previewW = size.height.toFloat()
        val previewH = size.width.toFloat()

        val scaleX = viewWidth / previewW
        val scaleY = viewHeight / previewH
        val scale = maxOf(scaleX, scaleY)

        val scaledW = previewW * scale
        val scaledH = previewH * scale

        val matrix = Matrix()
        matrix.setScale(scaledW / viewWidth, scaledH / viewHeight, viewWidth / 2f, viewHeight / 2f)

        textureView.setTransform(matrix)
    }

    // ===== KAMERA =====

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val cameraId = cameraManager.cameraIdList[0]

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val outputSizes = map?.getOutputSizes(SurfaceTexture::class.java) ?: return

        previewSize = chooseOptimalSize(outputSizes, textureView.width, textureView.height)
        applyCenterCropTransform(textureView.width, textureView.height)

        cameraManager.openCamera(
            cameraId,
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera

                    val surfaceTexture = textureView.surfaceTexture ?: return
                    val size = previewSize ?: return

                    surfaceTexture.setDefaultBufferSize(size.width, size.height)

                    val surface = Surface(surfaceTexture)
                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    request.addTarget(surface)

                    camera.createCaptureSession(
                        listOf(surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                session.setRepeatingRequest(request.build(), null, handler)
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

    // ===== DETECTION =====

    private fun processFrame() {
        val original = textureView.bitmap ?: return
        val scaled = Bitmap.createScaledBitmap(original, 300, 300, true)

        var image = TensorImage.fromBitmap(scaled)
        image = imageProcessor.process(image)

        val outputs = model.process(image)
        val locations = outputs.locationsAsTensorBuffer.floatArray
        val classes = outputs.classesAsTensorBuffer.floatArray
        val scores = outputs.scoresAsTensorBuffer.floatArray

        val mutable = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)
        val h = mutable.height
        val w = mutable.width

        paint.textSize = h / 15f
        paint.strokeWidth = h / 85f

        // 🔥 Her frame-de HEMME obýektleri ýygna
        val frameDetections = mutableListOf<String>()

        for (i in scores.indices) {
            val score = scores[i]
            val x = i * 4

            if (score > 0.5) {
                val classIndex = classes[i].toInt()

                // ??? label-leri skip et
                if (classIndex >= labels.size) continue
                val label = labels[classIndex]
                if (label == "???") continue

                frameDetections.add(label)

                val left = locations[x + 1] * w
                val top = locations[x] * h
                val right = locations[x + 3] * w
                val bottom = locations[x + 2] * h

                paint.color = colors[i % colors.size]
                paint.style = Paint.Style.STROKE
                canvas.drawRect(left, top, right, bottom, paint)

                paint.style = Paint.Style.FILL
                canvas.drawText("$label ${"%.2f".format(score)}", left, top, paint)
            }
        }

        // 🔥 Atomic update — click wagtynda thread-safe bolsun
        currentDetections = frameDetections.toList()
        imageView.setImageBitmap(mutable)
    }

    private fun speakText(text: String) {
        if (!isTtsReady) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "detection_speak")
    }

    // ===== PERMISSION =====

    private fun getPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            getPermission()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraDevice?.close()
        model.close()
        tts.stop()
        tts.shutdown()
    }
}