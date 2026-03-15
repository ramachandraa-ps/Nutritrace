package com.simats.nutritrace

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        binding.btnSignIn.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                android.widget.Toast.makeText(this, "Please enter email and password", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnSignIn.isEnabled = false

            ApiClient.post("/auth/login", mapOf("email" to email, "password" to password)) { success, json ->
                runOnUiThread {
                    binding.btnSignIn.isEnabled = true
                    if (success && json?.get("success")?.asBoolean == true) {
                        val token = json.get("token").asString
                        val user = json.getAsJsonObject("user")
                        ApiClient.saveToken(this, token)
                        ApiClient.saveUserInfo(
                            this,
                            user.get("id").asInt,
                            user.get("fullname").asString,
                            user.get("email").asString,
                            user.get("phone").asString
                        )

                        val hasHealthProfile = user.get("has_health_profile").asBoolean
                        val intent = if (hasHealthProfile) {
                            Intent(this, HomeActivity::class.java)
                        } else {
                            Intent(this, AgeSelectionActivity::class.java)
                        }
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    } else {
                        val message = json?.get("message")?.asString ?: "Login failed"
                        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
