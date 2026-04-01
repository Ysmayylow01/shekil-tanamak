package com.example.realtime_object

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.os.*
import android.speech.tts.TextToSpeech
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.example.realtime_object.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.*

class MainActivity : ComponentActivity() {

    lateinit var labels: List<String>
    lateinit var imageProcessor: ImageProcessor
    lateinit var imageView: ImageView
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var model: SsdMobilenetV11Metadata1

    lateinit var tts: TextToSpeech
    var isTtsReady = false
    var lastSpoken = ""
    var lastSpeakTime = 0L

    val paint = Paint()
    lateinit var bitmap: Bitmap
    val colors = listOf(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN,
        Color.GRAY, Color.BLACK, Color.DKGRAY,
        Color.MAGENTA, Color.YELLOW, Color.RED
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        get_permission()

        // 🔊 TTS
        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                isTtsReady = true
            }
        }

        labels = FileUtil.loadLabels(this, "labels.txt")

        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR))
            .build()

        model = SsdMobilenetV11Metadata1.newInstance(this)

        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        imageView = findViewById(R.id.imageView)
        textureView = findViewById(R.id.textureView)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                //configureTransform(width, height)
                fixPreview()
                open_camera()
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

           //     val original = textureView.bitmap ?: return
                val original = textureView.bitmap!!
                 bitmap = Bitmap.createScaledBitmap(original, 300, 300, true)
                // 🧠 AI input (NO DISTORTION)
                var image = TensorImage.fromBitmap(bitmap)
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

                for (i in scores.indices) {

                    val score = scores[i]
                    val x = i * 4

                    if (score > 0.5) {

                        val label = labels[classes[i].toInt()]

                        val left = locations[x + 1] * w
                        val top = locations[x] * h
                        val right = locations[x + 3] * w
                        val bottom = locations[x + 2] * h

                        paint.color = colors[i % colors.size]
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(left, top, right, bottom, paint)

                        paint.style = Paint.Style.FILL
                        canvas.drawText("$label ${"%.2f".format(score)}", left, top, paint)

                        // 🔊 SPEAK (anti-spam)
                        if (isTtsReady &&
                            label != lastSpoken &&
                            System.currentTimeMillis() - lastSpeakTime > 2000
                        ) {
                            speak(label)
                            lastSpoken = label
                            lastSpeakTime = System.currentTimeMillis()
                        }
                    }
                }

                imageView.setImageBitmap(mutable)
            }
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val matrix = Matrix()

        val rotation = windowManager.defaultDisplay.rotation
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, viewHeight.toFloat(), viewWidth.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)

            val scale = maxOf(
                viewHeight.toFloat() / viewWidth,
                viewWidth.toFloat() / viewHeight
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        }

        textureView.setTransform(matrix)
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
        tts.stop()
        tts.shutdown()
    }

    // 🔥 CAMERA FIX (NO STRETCH)
    @SuppressLint("MissingPermission")
    fun open_camera() {
        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {

            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera

                val surfaceTexture = textureView.surfaceTexture!!

                // 🔥 IŇ MÖHÜM FIX
                surfaceTexture.setDefaultBufferSize(textureView.width, textureView.height)

                val surface = Surface(surfaceTexture)

                val request = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                request.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        session.setRepeatingRequest(request.build(), null, handler)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                }, handler)
            }

            override fun onDisconnected(camera: CameraDevice) {}

            override fun onError(camera: CameraDevice, error: Int) {}
        }, handler)
    }
    fun fixPreview() {
        val viewWidth = textureView.width.toFloat()
        val viewHeight = textureView.height.toFloat()

        val previewRatio = 4f / 3f  // Kamera 4:3
        val viewRatio = viewWidth / viewHeight

        val matrix = Matrix()

        if (viewRatio > previewRatio) {
            val scale = viewWidth / (viewHeight * previewRatio)
            matrix.setScale(scale, 1f, viewWidth / 2, viewHeight / 2)
        } else {
            val scale = viewHeight / (viewWidth / previewRatio)
            matrix.setScale(1f, scale, viewWidth / 2, viewHeight / 2)
        }

        textureView.setTransform(matrix)
    }
    fun get_permission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
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
}