package com.stardroid.awakening.ar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.ContextCompat

/**
 * Camera preview using SurfaceView for better z-order compatibility with Vulkan SurfaceView.
 */
class CameraSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var previewSize: Size? = null

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
        holder.addCallback(this)
        // Put camera behind everything
        setZOrderOnTop(false)
        setZOrderMediaOverlay(false)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "Surface created")
        startBackgroundThread()
        openCamera()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "Surface changed: ${width}x${height}")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "Surface destroyed")
        closeCamera()
        stopBackgroundThread()
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
        val ctx = context ?: return

        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Camera permission not granted")
            return
        }

        val manager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    previewSize = chooseOptimalSize(
                        map?.getOutputSizes(SurfaceHolder::class.java) ?: emptyArray(),
                        width, height
                    )
                    Log.d(TAG, "Opening camera $cameraId with preview size $previewSize")
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
        val targetRatio = if (viewHeight > 0) viewWidth.toFloat() / viewHeight.toFloat() else 1f

        return choices
            .filter { it.width <= 1920 && it.height <= 1080 }
            .minByOrNull { size ->
                val ratio = size.width.toFloat() / size.height.toFloat()
                kotlin.math.abs(ratio - targetRatio)
            } ?: choices.firstOrNull() ?: Size(1280, 720)
    }

    private fun createCameraPreviewSession() {
        val surface = holder.surface
        if (!surface.isValid) {
            Log.e(TAG, "Surface not valid")
            return
        }

        try {
            val requestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            requestBuilder?.addTarget(surface)

            cameraDevice?.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.d(TAG, "Camera session configured")
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
        private const val TAG = "CameraSurfaceView"
    }
}
