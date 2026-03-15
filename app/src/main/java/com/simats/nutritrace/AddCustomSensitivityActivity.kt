package com.simats.nutritrace

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityAddCustomSensitivityBinding

class AddCustomSensitivityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddCustomSensitivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCustomSensitivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        binding.btnCancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        binding.btnSave.setOnClickListener {
            val customSensitivity = binding.etCustomSensitivity.text.toString().trim()
            if (customSensitivity.isNotEmpty()) {
                val resultIntent = Intent()
                resultIntent.putExtra("CUSTOM_SENSITIVITY", customSensitivity)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } else {
                binding.etCustomSensitivity.error = "Please enter an ingredient"
            }
        }
    }
}
