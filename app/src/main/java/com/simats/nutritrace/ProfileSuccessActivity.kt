package com.simats.nutritrace

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityProfileSuccessBinding

class ProfileSuccessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSuccessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val isEditing = intent.getBooleanExtra("IS_EDITING_PROFILE", false)
        
        // Update Title/Description if editing
        if (isEditing) {
            binding.tvTitle.text = "Profile Updated Successfully!"
            binding.tvSubtitle.text = "Your health preferences have been saved."
            binding.btnReturnSign.text = "Return to Personal Details"
        }

        binding.btnReturnSign.setOnClickListener {
            if (isEditing) {
                // Go back to the profile navigation stack (PersonalDetails -> Profile -> Home)
                val intent = Intent(this, PersonalDetailsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            } else {
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }

        binding.btnEditProfile.setOnClickListener {
            // For now, this could just return to the beginning of the onboarding or finish
            val intent = Intent(this, AgeSelectionActivity::class.java)
            if (isEditing) {
                intent.putExtra("IS_EDITING_PROFILE", true)
            } else {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }
}
