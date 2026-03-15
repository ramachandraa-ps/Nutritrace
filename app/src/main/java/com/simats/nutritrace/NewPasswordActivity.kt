package com.simats.nutritrace

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.simats.nutritrace.databinding.ActivityNewPasswordBinding

class NewPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val email = intent.getStringExtra("EMAIL") ?: ""
        val resetToken = intent.getStringExtra("RESET_TOKEN") ?: ""

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnResetPassword.isEnabled = false
        binding.btnResetPassword.alpha = 0.5f

        setupValidations()

        binding.btnResetPassword.setOnClickListener {
            val newPassword = binding.etNewPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            binding.btnResetPassword.isEnabled = false

            ApiClient.post("/auth/reset-password", mapOf(
                "email" to email,
                "reset_token" to resetToken,
                "new_password" to newPassword,
                "confirm_password" to confirmPassword
            )) { success, json ->
                runOnUiThread {
                    binding.btnResetPassword.isEnabled = true
                    if (success && json?.get("success")?.asBoolean == true) {
                        showSuccessDialog()
                    } else {
                        val message = json?.get("message")?.asString ?: "Password reset failed"
                        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupValidations() {
        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateForm()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }

        binding.etConfirmPassword.addTextChangedListener(textWatcher)

        binding.etNewPassword.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePasswordChecklist(s.toString())
                validateForm()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.etNewPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.llPasswordChecklist.visibility = View.VISIBLE
            }
        }
    }

    private fun updatePasswordChecklist(password: String) {
        val hasLength = password.length >= 8
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasNumber = password.any { it.isDigit() }
        val hasSpecial = password.any { "!@#$%^&*".contains(it) }

        updateChecklistItem(binding.tvCheckLength, hasLength)
        updateChecklistItem(binding.tvCheckUppercase, hasUpper)
        updateChecklistItem(binding.tvCheckLowercase, hasLower)
        updateChecklistItem(binding.tvCheckNumber, hasNumber)
        updateChecklistItem(binding.tvCheckSpecial, hasSpecial)
    }

    private fun updateChecklistItem(textView: TextView, isValid: Boolean) {
        val color = if (isValid) ContextCompat.getColor(this, R.color.primary_green) else ContextCompat.getColor(this, android.R.color.holo_red_dark)
        val icon = if (isValid) ContextCompat.getDrawable(this, R.drawable.ic_check) else ContextCompat.getDrawable(this, R.drawable.ic_close)

        textView.setTextColor(color)
        textView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
        textView.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(color)
    }

    private fun isPasswordValid(): Boolean {
        val p = binding.etNewPassword.text.toString()
        return p.length >= 8 && p.any { it.isUpperCase() } && p.any { it.isLowerCase() } && p.any { it.isDigit() } && p.any { "!@#$%^&*".contains(it) }
    }

    private fun validateForm() {
        val newPass = binding.etNewPassword.text.toString()
        val confirmPass = binding.etConfirmPassword.text.toString()

        val isNewValid = isPasswordValid()
        val isConfirmValid = confirmPass.isNotEmpty() && newPass == confirmPass

        val isValid = isNewValid && isConfirmValid
        binding.btnResetPassword.isEnabled = isValid
        binding.btnResetPassword.alpha = if (isValid) 1.0f else 0.5f
    }

    private fun showSuccessDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_password_success, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (dialog.isShowing) {
                dialog.dismiss()
            }
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }, 2500)
    }
}
