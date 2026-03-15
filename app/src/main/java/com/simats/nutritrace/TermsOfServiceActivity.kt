package com.simats.nutritrace

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityTermsOfServiceBinding

class TermsOfServiceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTermsOfServiceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTermsOfServiceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
