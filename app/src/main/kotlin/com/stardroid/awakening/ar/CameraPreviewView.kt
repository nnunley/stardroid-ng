package com.stardroid.awakening.ar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat

/**
 * Camera preview view for AR mode background.
 * Uses Camera2 API to show live camera feed behind the star overlay.
 */
class CameraPreviewView(context: Context) : TextureView(context) {

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var previewSize: Size? = null

    private val textureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "Surface texture available: ${width}x${height}")
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "Surface texture size changed: ${width}x${height}")
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            Log.d(TAG, "Surface texture destroyed")
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            // Called every time the preview updates
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera opened successfully")
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "Camera disconnected")
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
            Log.e(TAG, "Camera error: $error")
        }
    }

    init {
        surfaceTextureListener = textureListener
    }

    fun startPreview() {
        Log.d(TAG, "startPreview called, isAvailable=$isAvailable")
        startBackgroundThread()
        if (isAvailable) {
            openCamera()
        }
        // Always set listener - it's already set in init, but this ensures it
    }

    fun stopPreview() {
        closeCamera()
        stopBackgroundThread()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    private fun openCamera() {
        val context = context ?: return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Camera permission not granted")
            return
        }

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            // Find back-facing camera
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    // Get supported preview sizes
                    val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    previewSize = chooseOptimalSize(
                        map?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray(),
                        width, height
                    )

                    manager.openCamera(cameraId, stateCallback, backgroundHandler)
                    return
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Cannot access camera", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission denied", e)
        }
    }

    private fun chooseOptimalSize(choices: Array<Size>, viewWidth: Int, viewHeight: Int): Size {
        // Try to find a size that matches the view aspect ratio
        val targetRatio = viewWidth.toFloat() / viewHeight.toFloat()

        return choices
            .filter { it.width <= 1920 && it.height <= 1080 } // Limit resolution for performance
            .minByOrNull { size ->
                val ratio = size.width.toFloat() / size.height.toFloat()
                kotlin.math.abs(ratio - targetRatio)
            } ?: choices.firstOrNull() ?: Size(1280, 720)
    }

    private fun createCameraPreviewSession() {
        val texture = surfaceTexture ?: return
        val size = previewSize ?: return

        texture.setDefaultBufferSize(size.width, size.height)
        val surface = Surface(texture)

        try {
            val requestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            requestBuilder?.addTarget(surface)

            cameraDevice?.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.d(TAG, "Camera session configured successfully")
                        captureSession = session
                        try {
                            requestBuilder?.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            session.setRepeatingRequest(
                                requestBuilder!!.build(),
                                null,
                                backgroundHandler
                            )
                            Log.d(TAG, "Camera preview started")
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "Camera access exception", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Camera session configuration failed")
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Cannot create camera preview session", e)
        }
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }

    companion object {
        private const val TAG = "CameraPreviewView"
    }
}
