package com.simats.nutritrace

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityHealthSelectionBinding

class HealthSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHealthSelectionBinding
    private val conditionMap = mapOf(
        "thyroid" to "Hormonal & Metabolic Health",
        "diabetes" to "Blood Sugar Regulation",
        "heart" to "Cardiovascular & Circulatory Health",
        "kidney" to "Digestive & Organ Health",
        "celiac" to "Allergy & Immune Sensitivity",
        "lactose" to "Allergy & Immune Sensitivity",
        "pcos" to "Hormonal & Metabolic Health",
        "blood pressure" to "Cardiovascular & Circulatory Health",
        "cholesterol" to "Cardiovascular & Circulatory Health"
    )

    private val checkboxes = mutableListOf<CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHealthSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val isEditing = intent.getBooleanExtra("IS_EDITING_PROFILE", false)

        checkboxes.apply {
            add(binding.cbCat1) // Blood Sugar Regulation
            add(binding.cbCat2) // Cardiovascular
            add(binding.cbCat3) // Hormonal
            add(binding.cbCat4) // Digestive
            add(binding.cbCat5) // Allergy
            add(binding.cbCat6) // No Specific
            add(binding.cbCat7) // Not Sure
        }

        val exclusiveBoxes = listOf(binding.cbCat6, binding.cbCat7)
        val normalBoxes = checkboxes.filter { it !in exclusiveBoxes }

        for (cb in normalBoxes) {
            cb.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    exclusiveBoxes.forEach { it.isChecked = false }
                }
            }
        }

        exclusiveBoxes.forEach { cb ->
            cb.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    checkboxes.forEach { if (it != cb) it.isChecked = false }
                }
            }
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase().trim()
                if (query.isEmpty()) {
                    binding.llSuggestion.visibility = View.GONE
                    return
                }

                var foundCategory: String? = null
                for ((condition, category) in conditionMap) {
                    if (query.contains(condition) || condition.contains(query)) {
                        foundCategory = category
                        break
                    }
                }

                if (foundCategory != null) {
                    binding.tvSuggestedCategory.text = foundCategory
                    binding.llSuggestion.visibility = View.VISIBLE
                } else {
                    binding.llSuggestion.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnApplySuggestion.setOnClickListener {
            val suggestedCategory = binding.tvSuggestedCategory.text.toString()
            checkCategory(suggestedCategory)
            binding.etSearch.text?.clear()
            binding.llSuggestion.visibility = View.GONE
        }

        binding.btnContinue.setOnClickListener {
            if (checkboxes.any { it.isChecked }) {
                val selectedConditions = ArrayList<String>()
                val conditionKeys = listOf("blood_sugar", "cardiovascular", "hormonal", "digestive", "allergy_immune", "no_specific", "not_sure")
                for (i in checkboxes.indices) {
                    if (checkboxes[i].isChecked) {
                        selectedConditions.add(conditionKeys[i])
                    }
                }

                val intent = Intent(this, SensitivitySelectionActivity::class.java)
                val isEditing = getIntent().getBooleanExtra("IS_EDITING_PROFILE", false)
                intent.putExtra("IS_EDITING_PROFILE", isEditing)
                intent.putExtra("AGE_GROUP", getIntent().getStringExtra("AGE_GROUP"))
                intent.putStringArrayListExtra("CONDITIONS", selectedConditions)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please select at least one health risk category", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkCategory(categoryText: String) {
        val targetBox = checkboxes.find { it.text.toString() == categoryText }
        if (targetBox != null) {
            targetBox.isChecked = true
        }
    }
}
