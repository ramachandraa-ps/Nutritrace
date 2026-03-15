package com.simats.nutritrace

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityVerifyOtpBinding

class VerifyOtpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerifyOtpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val email = intent.getStringExtra("EMAIL") ?: ""

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnVerifyCode.setOnClickListener {
            val otpCode = "${binding.etOtp1.text}${binding.etOtp2.text}${binding.etOtp3.text}${binding.etOtp4.text}"
            if (otpCode.length != 4) {
                android.widget.Toast.makeText(this, "Please enter the 4-digit code", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnVerifyCode.isEnabled = false

            ApiClient.post("/auth/verify-otp", mapOf("email" to email, "otp" to otpCode)) { success, json ->
                runOnUiThread {
                    binding.btnVerifyCode.isEnabled = true
                    if (success && json?.get("success")?.asBoolean == true) {
                        val resetToken = json.get("reset_token").asString
                        val intent = Intent(this, NewPasswordActivity::class.java)
                        intent.putExtra("EMAIL", email)
                        intent.putExtra("RESET_TOKEN", resetToken)
                        startActivity(intent)
                    } else {
                        val message = json?.get("message")?.asString ?: "OTP verification failed"
                        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        setupOtpInputs()
    }

    private fun setupOtpInputs() {
        val editTexts = arrayOf(binding.etOtp1, binding.etOtp2, binding.etOtp3, binding.etOtp4)

        for (i in editTexts.indices) {
            editTexts[i].addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s?.length == 1 && i < editTexts.size - 1) {
                        editTexts[i + 1].requestFocus()
                    }
                }

                override fun afterTextChanged(s: android.text.Editable?) {}
            })

            editTexts[i].setOnKeyListener(android.view.View.OnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DEL && editTexts[i].text.isEmpty() && i > 0) {
                    editTexts[i - 1].requestFocus()
                    editTexts[i - 1].text.clear()
                    return@OnKeyListener true
                }
                false
            })
        }
    }
}
