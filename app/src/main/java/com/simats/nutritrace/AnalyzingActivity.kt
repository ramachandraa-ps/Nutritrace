package com.simats.nutritrace

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityAnalyzingBinding

class AnalyzingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalyzingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyzingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imageUriString = intent.getStringExtra("IMAGE_URI")
        if (imageUriString == null) {
            android.widget.Toast.makeText(this, "No image to analyze", android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val uri = Uri.parse(imageUriString)

        // Local analysis: ML Kit OCR → Gemini AI (bypasses broken backend)
        NutriTraceAI.analyzeImage(this, uri) { success, json ->
            runOnUiThread {
                if (success && json?.get("success")?.asBoolean == true) {
                    val scanObj = json.getAsJsonObject("scan")
                    val scanId = scanObj.get("id").asInt

                    val resultIntent = Intent(this, AnalysisResultActivity::class.java)
                    resultIntent.putExtra("SCAN_ID", scanId)
                    resultIntent.putExtra("SCAN_JSON", json.toString())
                    resultIntent.putExtra("IMAGE_URI", imageUriString)
                    resultIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                    startActivity(resultIntent)
                    finish()
                } else {
                    val message = json?.get("message")?.asString ?: "Analysis failed"
                    android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }
}
