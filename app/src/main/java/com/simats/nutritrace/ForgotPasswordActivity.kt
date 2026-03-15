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

        binding.btnBack.setOnClickListener {
            finish()
        }

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
                if (email.isNotEmpty() && !isValid) {
                    layout.error = "Enter a valid email address"
                } else {
                    layout.error = null
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.btnSendOtp.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            binding.btnSendOtp.isEnabled = false
            binding.btnSendOtp.alpha = 0.5f
            binding.btnSendOtp.text = "Sending..."

            ApiClient.post("/auth/forgot-password", mapOf("email" to email)) { success, json ->
                runOnUiThread {
                    if (success && json?.get("success")?.asBoolean == true) {
                        val intent = Intent(this, VerifyOtpActivity::class.java)
                        intent.putExtra("EMAIL", email)
                        startActivity(intent)
                        // Don't re-enable — user navigates away
                    } else {
                        binding.btnSendOtp.isEnabled = true
                        binding.btnSendOtp.alpha = 1.0f
                        binding.btnSendOtp.text = "Send OTP"
                        val message = json?.get("message")?.asString ?: "Failed to send OTP"
                        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
