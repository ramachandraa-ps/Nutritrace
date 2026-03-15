package com.simats.nutritrace

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityForgotPasswordBinding

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up click listener for Back button
        binding.btnBack.setOnClickListener {
            finish() // Navigates back to the previous screen (LoginActivity)
        }

        // Disable button initially
        binding.btnSendOtp.isEnabled = false
        binding.btnSendOtp.alpha = 0.5f

        binding.etEmail.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val email = s.toString().trim()
                val isValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
                binding.btnSendOtp.isEnabled = isValid
                binding.btnSendOtp.alpha = if (isValid) 1.0f else 0.5f
                
                val layout = binding.etEmail.parent.parent as com.google.android.material.textfield.TextInputLayout
                // Only show error text when they have typed something but it's invalid
                if (email.isNotEmpty() && !isValid) {
                    layout.error = "Enter a valid email address"
                } else {
                    layout.error = null
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Set up click listener for Send OTP button
        binding.btnSendOtp.setOnClickListener {
            val email = binding.etEmail.text.toString()
            startActivity(Intent(this, VerifyOtpActivity::class.java))
        }
    }
}
