package com.simats.nutritrace

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.simats.nutritrace.databinding.ActivityScanIngredientsBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class ScanIngredientsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanIngredientsBinding
    private var imageCapture: ImageCapture? = null

    private val cameraPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Log.d("PhotoPicker", "Selected URI: $uri")
            // Copy gallery image to local cache file to avoid content:// URI permission issues
            try {
                val localFile = File(
                    cacheDir,
                    "gallery_${SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())}.jpg"
                )
                contentResolver.openInputStream(uri)?.use { input ->
                    localFile.outputStream().use { output -> input.copyTo(output) }
                }
                val localUri = Uri.fromFile(localFile)
                val reviewIntent = Intent(this, ReviewImageActivity::class.java)
                reviewIntent.putExtra("SCAN_SLOT", intent.getStringExtra("SCAN_SLOT"))
                reviewIntent.putExtra("IMAGE_URI", localUri.toString())
                startActivityForResult(reviewIntent, 1001)
            } catch (e: Exception) {
                Log.e("PhotoPicker", "Failed to copy gallery image", e)
                Toast.makeText(this, "Failed to load selected image", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d("PhotoPicker", "No media selected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanIngredientsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Close Activity
        binding.ivClose.setOnClickListener {
            finish()
        }

        // Request camera permissions
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionRequest.launch(Manifest.permission.CAMERA)
        }

        // Navigate to Review Image on Capture
        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        // Open Gallery Picker
        binding.ivGallery.setOnClickListener {
            pickMedia.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraXApp", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            cacheDir,
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraXApp", "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val reviewIntent = Intent(this@ScanIngredientsActivity, ReviewImageActivity::class.java)
                    reviewIntent.putExtra("SCAN_SLOT", intent.getStringExtra("SCAN_SLOT"))
                    reviewIntent.putExtra("IMAGE_URI", savedUri.toString())
                    startActivityForResult(reviewIntent, 1001)
                }
            }
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("NutritionFlow", "ScanIngredients onActivityResult requestCode=$requestCode resultCode=$resultCode")
        // If ReviewImageActivity finishes successfully
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val slot = intent.getStringExtra("SCAN_SLOT")
            if (slot == null) {
                Log.d("NutritionFlow", "ScanIngredients slot is null, launching AnalyzingActivity")
                // Not from CompareActivity
                val analyzeIntent = Intent(this, AnalyzingActivity::class.java)
                data?.getStringExtra("IMAGE_URI")?.let { uriString ->
                    Log.d("NutritionFlow", "ScanIngredients got image URI: $uriString")
                    analyzeIntent.putExtra("IMAGE_URI", uriString)
                    analyzeIntent.data = android.net.Uri.parse(uriString)
                    analyzeIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(analyzeIntent)
                finish()
            } else {
                Log.d("NutritionFlow", "ScanIngredients slot is $slot, returning to CompareActivity")
                // From CompareActivity, pass the result back
                val resultData = Intent().apply {
                    putExtra("SCAN_SLOT", slot)
                    // Optionally pass back the image URI to CompareActivity too
                    data?.getStringExtra("IMAGE_URI")?.let { uri ->
                        putExtra("IMAGE_URI", uri)
                    }
                }
                setResult(RESULT_OK, resultData)
                finish()
            }
        }
    }
}
