package com.example.realtime_object

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.os.Handler
import android.view.Surface

class CameraManager(context: Context, private val handler: Handler) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    @SuppressLint("MissingPermission")
    fun openCamera(surfaceTexture: android.graphics.SurfaceTexture, onCameraReady: () -> Unit) {
        val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera

                val surface = Surface(surfaceTexture)
                val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(surface)
                }

                camera.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            session.setRepeatingRequest(captureRequest.build(), null, handler)
                            onCameraReady()
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    },
                    handler
                )
            }

            override fun onDisconnected(camera: CameraDevice) {}
            override fun onError(camera: CameraDevice, error: Int) {}
        }, handler)
    }

    fun closeCamera() {
        captureSession?.close()
        cameraDevice?.close()
    }
}