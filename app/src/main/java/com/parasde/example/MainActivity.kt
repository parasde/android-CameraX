package com.parasde.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@SuppressLint("RestrictedApi")
class MainActivity : AppCompatActivity() {
    private lateinit var launcher: ActivityResultLauncher<String>

    private lateinit var viewFinder: PreviewView

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture? = null
    private var camera: Camera? = null
    private var lensFacing: CameraSelector = if (Build.MODEL.lowercase().contains("sdk")) {
        CameraSelector.DEFAULT_BACK_CAMERA
    } else {
        CameraSelector.DEFAULT_FRONT_CAMERA
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById<PreviewView>(R.id.viewFinder)

        // permission
        launcher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { permission ->
            if (!permission) {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } else {
                openCamera()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        launcher.launch(Manifest.permission.CAMERA)
    }

    private fun openCamera () {
        viewFinder.post {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                } catch (e: Exception) {
                    Log.e("Camera Provider Error", "${e.message}")
                    return@addListener
                }

                val aspectRatio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val metrics = windowManager.currentWindowMetrics
                    aspectRatio(metrics.bounds.width(), metrics.bounds.height())
                } else {
                    val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
                    aspectRatio(metrics.widthPixels, metrics.heightPixels)
                }
                val rotation = viewFinder.display.rotation
                val localCameraProvider =
                    cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

                preview = Preview.Builder()
                    .setTargetAspectRatio(aspectRatio)
                    .setTargetRotation(rotation)
                    .build()

                val videoCaptureConfig = VideoCapture.DEFAULT_CONFIG.config
                videoCapture = VideoCapture.Builder
                    .fromConfig(videoCaptureConfig)
                    .build()

                localCameraProvider.unbindAll()
                try {
                    camera = localCameraProvider.bindToLifecycle(
                        this, // current lifecycle owner
                        lensFacing, // either front or back facing
                        preview, // camera preview use case
                        videoCapture, // video capture use case
                    )
                    preview?.setSurfaceProvider(viewFinder.surfaceProvider)
                } catch (e: Exception) {
                    Log.e("Camera Error", "${e.message}")
                    return@addListener
                }
            }, ContextCompat.getMainExecutor(this))
        }
    }

    /**
     *  Detecting the most suitable aspect ratio for current dimensions
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - Settings.RATIO_4_3_VALUE) <= abs(previewRatio - Settings.RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /*
    @SuppressLint("MissingPermission")
    private fun recording () {
        videoCapture?.startRecording(outputOption(), mainExecutor(), object: VideoCapture.OnVideoSavedCallback {
            override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                Log.i("Recording", "saved")
            }

            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                Log.e("Recording", message)
            }

        })
    }
    private fun stopRecording () {
        videoCapture?.stopRecording()
    }
    */

    // file des
    private fun outputOption (): VideoCapture.OutputFileOptions {
        val videoNm = "example_" + System.currentTimeMillis()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    videoNm
                )
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, outputDirectory)
            }
            contentResolver.run {
                val contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                VideoCapture.OutputFileOptions.Builder(
                    contentResolver,
                    contentUri,
                    contentValues
                )
            }
        } else {
            File(outputDirectory).mkdirs()
            val file = File("$outputDirectory/$videoNm.mp4")
            VideoCapture.OutputFileOptions.Builder(file)
        }.build()
    }
    // save directory
    private val outputDirectory: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${Environment.DIRECTORY_DCIM}/CameraExample/"
        } else {
//            "${getExternalFilesDir(Environment.DIRECTORY_DCIM)}/OneHandPT/"
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + "/CameraExample/"
        }
    }
}