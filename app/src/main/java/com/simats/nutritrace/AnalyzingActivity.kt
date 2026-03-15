package com.simats.nutritrace

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityAnalyzingBinding // Added import for ViewBinding

class AnalyzingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalyzingBinding // Added for ViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyzingBinding.inflate(layoutInflater) // Added for ViewBinding
        setContentView(binding.root) // Changed for ViewBinding
        android.util.Log.d("NutritionFlow", "AnalyzingActivity onCreate started") // Added Log.d

        // Simulate analysis for 2 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            // After 2 seconds, either go back to Home or to a Result screen.
            // For now, we go back to HomeActivity, clearing the stack, or just finish.
            // Navigate to final result screen after delay
            val resultIntent = Intent(this, AnalysisResultActivity::class.java)
            android.util.Log.d("NutritionFlow", "AnalyzingActivity 3 seconds passed, starting Result activity")
            intent.getStringExtra("IMAGE_URI")?.let { uriString ->
                android.util.Log.d("NutritionFlow", "AnalyzingActivity got image URI: $uriString")
                resultIntent.putExtra("IMAGE_URI", uriString)
                resultIntent.data = android.net.Uri.parse(uriString)
                resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            resultIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
            startActivity(resultIntent)
            finish()
        }, 3000)
    }
}
