package com.simats.nutritrace

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.simats.nutritrace.databinding.ActivitySensitivitySelectionBinding

class SensitivitySelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySensitivitySelectionBinding
    private val customSensitivities = mutableListOf<String>()

    private val addCustomLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val customSens = result.data?.getStringExtra("CUSTOM_SENSITIVITY")
            if (customSens != null) {
                addCustomChip(customSens)
                binding.chipNone.isChecked = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySensitivitySelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val chips = listOf(
            binding.chipDairy,
            binding.chipNuts,
            binding.chipGluten,
            binding.chipSulfites,
            binding.chipArtificialColors,
            binding.chipArtificialSweet
        )

        val checkedColor = ContextCompat.getColor(this, R.color.primary_green)
        val defaultBgColor = ContextCompat.getColor(this, R.color.white)

        val bgStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                (checkedColor and 0x00FFFFFF) or 0x1A000000,
                defaultBgColor
            )
        )

        val strokeStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                checkedColor,
                android.graphics.Color.parseColor("#E0E0E0")
            )
        )

        val allChips = chips + binding.chipNone
        allChips.forEach { chip ->
            chip.chipBackgroundColor = bgStateList
            chip.chipStrokeColor = strokeStateList
        }

        for (chip in chips) {
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    binding.chipNone.isChecked = false
                }
            }
        }

        binding.chipNone.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                chips.forEach { it.isChecked = false }
                for (i in 0 until binding.cgSensitivities.childCount) {
                    val view = binding.cgSensitivities.getChildAt(i)
                    if (view is Chip && view != binding.chipNone && !chips.contains(view)) {
                         view.isChecked = false
                    }
                }
            }
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnAddCustom.setOnClickListener {
            val intent = Intent(this, AddCustomSensitivityActivity::class.java)
            addCustomLauncher.launch(intent)
        }

        binding.btnFinish.setOnClickListener {
            var isAnyChecked = false
            for (i in 0 until binding.cgSensitivities.childCount) {
                val view = binding.cgSensitivities.getChildAt(i)
                if (view is Chip && view.isChecked) {
                    isAnyChecked = true
                    break
                }
            }

            if (isAnyChecked) {
                val selectedSensitivities = ArrayList<String>()
                val customSens = ArrayList<String>()
                val predefinedChips = listOf(binding.chipDairy, binding.chipNuts, binding.chipGluten, binding.chipSulfites, binding.chipArtificialColors, binding.chipArtificialSweet)
                val predefinedNames = listOf("Dairy", "Nuts", "Gluten", "Sulfites", "Artificial Colors", "Artificial Sweeteners")

                for (i in predefinedChips.indices) {
                    if (predefinedChips[i].isChecked) {
                        selectedSensitivities.add(predefinedNames[i])
                    }
                }
                customSens.addAll(customSensitivities)

                val intent = Intent(this, ProfileSuccessActivity::class.java)
                val isEditing = getIntent().getBooleanExtra("IS_EDITING_PROFILE", false)
                intent.putExtra("IS_EDITING_PROFILE", isEditing)
                intent.putExtra("AGE_GROUP", getIntent().getStringExtra("AGE_GROUP"))
                intent.putStringArrayListExtra("CONDITIONS", getIntent().getStringArrayListExtra("CONDITIONS"))
                intent.putStringArrayListExtra("SENSITIVITIES", selectedSensitivities)
                intent.putStringArrayListExtra("CUSTOM_SENSITIVITIES", customSens)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Please select at least one option", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addCustomChip(text: String) {
        val newChip = Chip(this).apply {
            this.text = text
            isCheckable = true
            isChecked = true
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                binding.cgSensitivities.removeView(this)
                customSensitivities.remove(text)
            }

            val checkedColor = ContextCompat.getColor(context, R.color.primary_green)
            val defaultBgColor = ContextCompat.getColor(context, R.color.white)

            chipBackgroundColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
                intArrayOf((checkedColor and 0x00FFFFFF) or 0x1A000000, defaultBgColor)
            )
            chipStrokeColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
                intArrayOf(checkedColor, android.graphics.Color.parseColor("#E0E0E0"))
            )
            chipStrokeWidth = 1f * resources.displayMetrics.density
            setTextColor(ContextCompat.getColor(context, R.color.text_dark))

            setOnCheckedChangeListener { _, isChecked ->
                if(isChecked) {
                    binding.chipNone.isChecked = false
                }
            }
        }

        val noneIndex = binding.cgSensitivities.indexOfChild(binding.chipNone)
        binding.cgSensitivities.addView(newChip, if (noneIndex >= 0) noneIndex else binding.cgSensitivities.childCount)
        customSensitivities.add(text)
    }
}
