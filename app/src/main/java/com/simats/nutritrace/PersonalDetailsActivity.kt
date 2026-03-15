package com.simats.nutritrace

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityPersonalDetailsBinding

class PersonalDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonalDetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonalDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.ivEditName.setOnClickListener {
            enableEditing(binding.etFullName)
        }
        binding.ivEditPhone.setOnClickListener {
            enableEditing(binding.etPhone)
        }
        binding.ivEditEmail.setOnClickListener {
            enableEditing(binding.etEmail)
        }

        binding.btnSaveChanges.setOnClickListener {
            saveChanges()
        }

        binding.btnEditHealthPrefs.setOnClickListener {
            val intent = Intent(this, AgeSelectionActivity::class.java).apply {
                putExtra("IS_EDITING_PROFILE", true)
            }
            startActivity(intent)
        }

        // Load profile from backend
        ApiClient.getAuth(this, "/user/profile") { success, json ->
            runOnUiThread {
                if (success && json?.get("success")?.asBoolean == true) {
                    val user = json.getAsJsonObject("user")
                    binding.etFullName.setText(user.get("fullname")?.asString ?: "")
                    binding.etPhone.setText(user.get("phone")?.asString ?: "")
                    binding.etEmail.setText(user.get("email")?.asString ?: "")
                }
            }
        }
    }

    private fun enableEditing(editText: EditText) {
        editText.isEnabled = true
        editText.requestFocus()
        editText.setSelection(editText.text.length)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun saveChanges() {
        val body = mapOf(
            "fullname" to binding.etFullName.text.toString().trim(),
            "phone" to binding.etPhone.text.toString().trim(),
            "email" to binding.etEmail.text.toString().trim()
        )

        ApiClient.putAuth(this, "/user/profile", body) { success, json ->
            runOnUiThread {
                binding.etFullName.isEnabled = false
                binding.etPhone.isEnabled = false
                binding.etEmail.isEnabled = false

                val view = this.currentFocus
                if (view != null) {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                }

                if (success && json?.get("success")?.asBoolean == true) {
                    Toast.makeText(this, "Changes saved successfully", Toast.LENGTH_SHORT).show()
                } else {
                    val message = json?.get("message")?.asString ?: "Failed to save changes"
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
