package com.simats.nutritrace

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.simats.nutritrace.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCreateAccount.isEnabled = true
        binding.btnCreateAccount.alpha = 1.0f

        setupValidations()

        binding.btnCreateAccount.setOnClickListener {
            if (!validateFullName() || !validatePhone() || !validateEmail() || !isPasswordValid() || !validateConfirmPassword()) {
                return@setOnClickListener
            }

            binding.btnCreateAccount.isEnabled = false

            val body = mapOf(
                "fullname" to binding.etFullName.text.toString().trim(),
                "phone" to binding.etPhone.text.toString().trim(),
                "email" to binding.etEmail.text.toString().trim(),
                "password" to binding.etPassword.text.toString(),
                "confirm_password" to binding.etConfirmPassword.text.toString()
            )

            ApiClient.post("/auth/signup", body) { success, json ->
                runOnUiThread {
                    binding.btnCreateAccount.isEnabled = true
                    if (success && json?.get("success")?.asBoolean == true) {
                        // Save token so health profile can be saved during onboarding
                        json.get("token")?.asString?.let { token ->
                            ApiClient.saveToken(this@RegisterActivity, token)
                        }

                        val dialogView = layoutInflater.inflate(R.layout.dialog_account_created, null)
                        val dialog = android.app.AlertDialog.Builder(this@RegisterActivity)
                            .setView(dialogView)
                            .setCancelable(false)
                            .create()
                        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                        dialog.show()

                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            dialog.dismiss()
                            startActivity(Intent(this@RegisterActivity, AgeSelectionActivity::class.java))
                            finish()
                        }, 2000)
                    } else {
                        val message = json?.get("message")?.asString ?: "Registration failed"
                        android.widget.Toast.makeText(this@RegisterActivity, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.tvSignIn.setOnClickListener {
            finish()
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

        binding.etFullName.addTextChangedListener(textWatcher)
        binding.etPhone.addTextChangedListener(textWatcher)
        binding.etEmail.addTextChangedListener(textWatcher)
        binding.etPassword.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePasswordChecklist(s.toString())
                validateForm()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        binding.etConfirmPassword.addTextChangedListener(textWatcher)

        binding.etFullName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateFullName()
        }
        binding.etPhone.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validatePhone()
        }
        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateEmail()
        }
        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.llPasswordChecklist.visibility = android.view.View.VISIBLE
            }
        }
        binding.etConfirmPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateConfirmPassword()
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

    private fun validateFullName(): Boolean {
        val name = binding.etFullName.text.toString().trim()
        val isValid = name.matches(Regex("^[a-zA-Z\\s]{3,}$"))
        val layout = binding.etFullName.parent.parent as com.google.android.material.textfield.TextInputLayout
        if (name.isNotEmpty() && !isValid) {
            layout.error = "Enter a valid full name"
            return false
        }
        layout.error = null
        return name.isNotEmpty() && isValid
    }

    private fun validatePhone(): Boolean {
        val phone = binding.etPhone.text.toString().trim()
        val isValid = phone.matches(Regex("^\\d{10}$"))
        val layout = binding.etPhone.parent.parent as com.google.android.material.textfield.TextInputLayout
        if (phone.isNotEmpty() && !isValid) {
            layout.error = "Enter a valid 10-digit phone number"
            return false
        }
        layout.error = null
        return phone.isNotEmpty() && isValid
    }

    private fun validateEmail(): Boolean {
        val email = binding.etEmail.text.toString().trim()
        val isValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
        val layout = binding.etEmail.parent.parent as com.google.android.material.textfield.TextInputLayout
        if (email.isNotEmpty() && !isValid) {
            layout.error = "Enter a valid email address"
            return false
        }
        layout.error = null
        return email.isNotEmpty() && isValid
    }

    private fun isPasswordValid(): Boolean {
        val p = binding.etPassword.text.toString()
        return p.length >= 8 && p.any { it.isUpperCase() } && p.any { it.isLowerCase() } && p.any { it.isDigit() } && p.any { "!@#$%^&*".contains(it) }
    }

    private fun validateConfirmPassword(): Boolean {
        val password = binding.etPassword.text.toString()
        val confirm = binding.etConfirmPassword.text.toString()
        val layout = binding.etConfirmPassword.parent.parent as com.google.android.material.textfield.TextInputLayout

        if (confirm.isNotEmpty() && confirm != password) {
            layout.error = "Passwords do not match"
            return false
        }
        layout.error = null
        return confirm.isNotEmpty() && confirm == password
    }

    private fun validateForm() {
        validateFullName()
        validatePhone()
        validateEmail()
        validateConfirmPassword()
        binding.btnCreateAccount.isEnabled = true
        binding.btnCreateAccount.alpha = 1.0f
    }
}
