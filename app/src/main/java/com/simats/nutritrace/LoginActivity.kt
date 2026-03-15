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

        // Set up click listener for Forgot Password text
        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // Set up click listener for Sign In button
        binding.btnSignIn.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            val isEmailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
            val isPasswordValid = password.length >= 8

            if (!isEmailValid || !isPasswordValid) {
                // Validation bypassed for local testing
                // android.widget.Toast.makeText(this, "Invalid email or password", android.widget.Toast.LENGTH_SHORT).show()
                // return@setOnClickListener
            }

            // Assume successful login and point to HomeActivity as requested
            val intent = Intent(this, HomeActivity::class.java)
            // Clear the stack so user doesn't go back to login 
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        // Set up click listener for Sign Up text
        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
