package com.example.realtime_object

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Size
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.util.*

class GeminiFragment : Fragment() {

    private lateinit var textureView: TextureView
    private lateinit var txt: TextView
    private lateinit var progressBar: ProgressBar

    private var cameraDevice: CameraDevice? = null
    private lateinit var handler: Handler
    private lateinit var cameraManager: CameraManager

    private lateinit var tts: TextToSpeech
    private var isTtsReady = false
    private var isSpeaking = false
    private var lastSpeakTime = 0L

    private val apiKey = "AIzaSyCmCcIh3eXzEdoqKEptVG9KQ9-K9afHnDY"
    private lateinit var api: GeminiApi

    private var previewSize: Size? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_gemini, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textureView = view.findViewById(R.id.textureView)
        txt = view.findViewById(R.id.txtView)
        progressBar = view.findViewById(R.id.progressBar)

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

        val retrofit = Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(GeminiApi::class.java)

        val handlerThread = HandlerThread("geminiCameraThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        cameraManager = requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager

        textureView.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastSpeakTime < 3000) return@setOnClickListener
            if (isSpeaking) return@setOnClickListener
            lastSpeakTime = now

            val bitmap = textureView.bitmap ?: return@setOnClickListener
            sendToGemini(bitmap)
        }

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    // ===== KAMERA ÖLÇEG — SÜÝNMEZ =====

    /**
     * Kameranyň goldaýan ölçeglerden iň gowusyny saýla.
     * TextureView-yň aspect ratio-syna iň ýakynyny tapýar.
     */
    private fun chooseOptimalSize(choices: Array<Size>, viewWidth: Int, viewHeight: Int): Size {
        // Portrait mode-da: ekran width < height, kamera width > height (landscape)
        // Şonuň üçin target ratio = ekranyň height/width
        val targetRatio = viewHeight.toDouble() / viewWidth.toDouble()

        return choices
            .filter { it.width >= 640 }
            .minByOrNull {
                val ratio = it.width.toDouble() / it.height.toDouble()
                Math.abs(ratio - targetRatio)
            } ?: choices[0]
    }

    /**
     * TextureView-y kamera ölçegine görä center-crop scale edýär.
     * Surat ASLA süýnmez — diňe gyralary kesilýär (crop).
     */
    private fun applyCenterCropTransform(viewWidth: Int, viewHeight: Int) {
        val size = previewSize ?: return

        // Kamera landscape: meselem 1920x1080
        // Portrait mode-da rotate bolýar → hakyky preview:
        val previewW = size.height.toFloat()  // portrait-da → 1080
        val previewH = size.width.toFloat()   // portrait-da → 1920

        val scaleX = viewWidth / previewW
        val scaleY = viewHeight / previewH

        // Center-crop: iň uly scale-y al (doldurmak üçin)
        val scale = maxOf(scaleX, scaleY)

        val scaledW = previewW * scale
        val scaledH = previewH * scale

        // Merkeze süýşür
        val dx = (viewWidth - scaledW) / 2f
        val dy = (viewHeight - scaledH) / 2f

        val matrix = Matrix()
        matrix.setScale(scaledW / viewWidth, scaledH / viewHeight, viewWidth / 2f, viewHeight / 2f)

        textureView.setTransform(matrix)
    }

    // ===== KAMERA =====

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val cameraId = cameraManager.cameraIdList[0]

        // Kameranyň hakyky goldaýan ölçeglerini al
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val outputSizes = map?.getOutputSizes(SurfaceTexture::class.java) ?: return

        // Iň gowy ölçegi saýla
        previewSize = chooseOptimalSize(outputSizes, textureView.width, textureView.height)

        // Center-crop transform
        applyCenterCropTransform(textureView.width, textureView.height)

        cameraManager.openCamera(
            cameraId,
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera

                    val surfaceTexture = textureView.surfaceTexture ?: return
                    val size = previewSize ?: return

                    // 🔥 Buffer = kameranyň hakyky ölçegi
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

    // ===== GEMINI =====

    private fun sendToGemini(bitmap: Bitmap) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                txt.text = "Analyzing..."

                val base64 = bitmapToBase64(bitmap)

                val request = GeminiRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = "What is in this image? Give a brief explanation in English."),
                                Part(
                                    inline_data = InlineData(
                                        mime_type = "image/jpeg",
                                        data = base64
                                    )
                                )
                            )
                        )
                    )
                )

                val response = api.generateContent(apiKey, request)

                val text = response.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text ?: "No response"

                progressBar.visibility = View.GONE
                txt.text = text
                speakText(text)

            } catch (e: Exception) {
                e.printStackTrace()
                progressBar.visibility = View.GONE
                txt.text = "Error: ${e.message}"
            }
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun speakText(text: String) {
        if (!isTtsReady) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gemini_speak")
    }

    // ===== PERMISSION =====

    private fun getPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 102)
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
        tts.stop()
        tts.shutdown()
    }
}