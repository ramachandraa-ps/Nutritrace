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

        // Setup Back Button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Setup Edit Buttons
        binding.ivEditName.setOnClickListener {
            enableEditing(binding.etFullName)
        }
        
        binding.ivEditPhone.setOnClickListener {
            enableEditing(binding.etPhone)
        }
        
        binding.ivEditEmail.setOnClickListener {
            enableEditing(binding.etEmail)
        }

        // Setup Save Changes Button
        binding.btnSaveChanges.setOnClickListener {
            saveChanges()
        }

        // Setup Edit Health Preferences Button
        binding.btnEditHealthPrefs.setOnClickListener {
            // Navigate to AgeSelectionActivity (start of Health Profile Setup)
            // Pass a flag to indicate we are editing rather than first-time setup
            val intent = Intent(this, AgeSelectionActivity::class.java).apply {
                putExtra("IS_EDITING_PROFILE", true)
            }
            startActivity(intent)
        }
    }

    private fun enableEditing(editText: EditText) {
        editText.isEnabled = true
        editText.requestFocus()
        // Move cursor to end
        editText.setSelection(editText.text.length)
        
        // Show keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun saveChanges() {
        // Disable editing for all fields
        binding.etFullName.isEnabled = false
        binding.etPhone.isEnabled = false
        binding.etEmail.isEnabled = false

        // Hide keyboard
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }

        // Provide feedback
        Toast.makeText(this, "Changes saved successfully", Toast.LENGTH_SHORT).show()
    }
}
