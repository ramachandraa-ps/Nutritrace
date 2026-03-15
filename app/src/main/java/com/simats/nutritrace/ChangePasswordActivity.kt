package com.simats.nutritrace

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.simats.nutritrace.databinding.ActivityChangePasswordBinding

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChangePasswordBinding

    private var isCurrentPasswordVisible = false
    private var isNewPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnChangePassword.isEnabled = false
        binding.btnChangePassword.alpha = 0.5f

        setupValidations()

        binding.btnCancel.setOnClickListener {
            finish()
        }

        binding.ivToggleCurrentPassword.setOnClickListener {
            isCurrentPasswordVisible = togglePasswordVisibility(
                binding.etCurrentPassword,
                binding.ivToggleCurrentPassword,
                isCurrentPasswordVisible
            )
        }
        binding.ivToggleNewPassword.setOnClickListener {
            isNewPasswordVisible = togglePasswordVisibility(
                binding.etNewPassword,
                binding.ivToggleNewPassword,
                isNewPasswordVisible
            )
        }
        binding.ivToggleConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = togglePasswordVisibility(
                binding.etConfirmNewPassword,
                binding.ivToggleConfirmPassword,
                isConfirmPasswordVisible
            )
        }

        binding.btnChangePassword.setOnClickListener {
            val current = binding.etCurrentPassword.text.toString()
            val newPass = binding.etNewPassword.text.toString()
            val confirm = binding.etConfirmNewPassword.text.toString()

            binding.btnChangePassword.isEnabled = false

            ApiClient.postAuth(this, "/auth/change-password", mapOf(
                "current_password" to current,
                "new_password" to newPass,
                "confirm_password" to confirm
            )) { success, json ->
                runOnUiThread {
                    binding.btnChangePassword.isEnabled = true
                    if (success && json?.get("success")?.asBoolean == true) {
                        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password_success, null)
                        val dialog = android.app.AlertDialog.Builder(this)
                            .setView(dialogView)
                            .setCancelable(false)
                            .create()
                        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                        dialog.show()

                        Handler(Looper.getMainLooper()).postDelayed({
                            dialog.dismiss()
                            finish()
                        }, 2000)
                    } else {
                        val message = json?.get("message")?.asString ?: "Failed to change password"
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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

        binding.etCurrentPassword.addTextChangedListener(textWatcher)
        binding.etConfirmNewPassword.addTextChangedListener(textWatcher)

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
        val currentPass = binding.etCurrentPassword.text.toString()
        val newPass = binding.etNewPassword.text.toString()
        val confirmPass = binding.etConfirmNewPassword.text.toString()

        val isCurrentValid = currentPass.isNotEmpty()
        val isNewValid = isPasswordValid()
        val isConfirmValid = confirmPass.isNotEmpty() && newPass == confirmPass

        val isValid = isCurrentValid && isNewValid && isConfirmValid
        binding.btnChangePassword.isEnabled = isValid
        binding.btnChangePassword.alpha = if (isValid) 1.0f else 0.5f
    }

    private fun togglePasswordVisibility(editText: EditText, icon: ImageView, isVisible: Boolean): Boolean {
        if (isVisible) {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            icon.setImageResource(R.drawable.ic_eye_outline)
        } else {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            icon.setImageResource(R.drawable.ic_eye_off_outline)
        }
        editText.setSelection(editText.text.length)
        return !isVisible
    }
}
