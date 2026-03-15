package com.simats.nutritrace

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.simats.nutritrace.databinding.ActivityAgeSelectionBinding

class AgeSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgeSelectionBinding
    private var selectedAgeGroup: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgeSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Back Button
        // Basic mockup of prefilling data if editing
        val isEditing = intent.getBooleanExtra("IS_EDITING_PROFILE", false)
        if (isEditing) {
            // Pre-select Adult as mockup of existing data
            selectCard(binding.cardAdult, "Adult")
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        // Setup Card Clicks for Single Select
        binding.cardChild.setOnClickListener { selectCard(binding.cardChild, "Child") }
        binding.cardTeen.setOnClickListener { selectCard(binding.cardTeen, "Teen") }
        binding.cardAdult.setOnClickListener { selectCard(binding.cardAdult, "Adult") }
        binding.cardSenior.setOnClickListener { selectCard(binding.cardSenior, "Senior") }

        // Setup Continue Button
        binding.btnContinue.setOnClickListener {
            if (selectedAgeGroup != null) {
                // Navigate to next step
                val intent = Intent(this, HealthSelectionActivity::class.java)
                val isEditing = getIntent().getBooleanExtra("IS_EDITING_PROFILE", false)
                intent.putExtra("IS_EDITING_PROFILE", isEditing)
                // If editing, we can optionally pre-select based on mock data
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please select an age group", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selectCard(selectedCard: MaterialCardView, ageGroup: String) {
        selectedAgeGroup = ageGroup

        // Reset all cards
        resetCard(binding.cardChild, binding.rbChild)
        resetCard(binding.cardTeen, binding.rbTeen)
        resetCard(binding.cardAdult, binding.rbAdult)
        resetCard(binding.cardSenior, binding.rbSenior)

        // Highlight selected card
        val density = resources.displayMetrics.density
        selectedCard.strokeWidth = (2 * density).toInt() // 2dp
        selectedCard.strokeColor = ContextCompat.getColor(this, R.color.primary_green)
        
        when (selectedCard) {
            binding.cardChild -> binding.rbChild.isChecked = true
            binding.cardTeen -> binding.rbTeen.isChecked = true
            binding.cardAdult -> binding.rbAdult.isChecked = true
            binding.cardSenior -> binding.rbSenior.isChecked = true
        }
    }

    private fun resetCard(card: MaterialCardView, radioButton: android.widget.RadioButton) {
        val density = resources.displayMetrics.density
        card.strokeWidth = (1 * density).toInt() // 1dp
        card.strokeColor = Color.parseColor("#E0E0E0")
        radioButton.isChecked = false
    }
}
