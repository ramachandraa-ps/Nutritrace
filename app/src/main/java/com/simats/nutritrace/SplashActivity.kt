package com.simats.nutritrace

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Animate the progress bar
        binding.progressTrack.post {
            val totalWidth = binding.progressTrack.width
            val progressAnimator = android.animation.ValueAnimator.ofInt(0, totalWidth)
            progressAnimator.duration = 2500 // 2.5 seconds to fill

            progressAnimator.addUpdateListener { animator ->
                val params = binding.progressView.layoutParams
                params.width = animator.animatedValue as Int
                binding.progressView.layoutParams = params
            }
            progressAnimator.start()
        }

        // Simulate a 3-second system check/loading delay
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }, 3000)
    }
}
