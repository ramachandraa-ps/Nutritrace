package com.simats.nutritrace

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityReviewImageBinding

class ReviewImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewImageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load image if passed
        val imageUriString = intent.getStringExtra("IMAGE_URI")
        if (imageUriString != null) {
            binding.ivPreview.setImageURI(android.net.Uri.parse(imageUriString))
        }

        // Close Activity (Cancel flow)
        binding.ivClose.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        // Retake (Go back to Scanner)
        binding.btnRetake.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        // Analyze Text (Success, pass slot back)
        binding.btnAnalyze.setOnClickListener {
            val resultIntent = Intent()
            val slot = intent.getStringExtra("SCAN_SLOT")
            resultIntent.putExtra("SCAN_SLOT", slot)
            if (imageUriString != null) {
                resultIntent.putExtra("IMAGE_URI", imageUriString)
                resultIntent.data = android.net.Uri.parse(imageUriString)
                resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }
}
