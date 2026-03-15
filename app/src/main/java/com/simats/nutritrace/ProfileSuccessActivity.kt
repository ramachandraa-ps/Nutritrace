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

        // Send health profile to backend
        val ageGroup = intent.getStringExtra("AGE_GROUP") ?: "Adult"
        val conditions = intent.getStringArrayListExtra("CONDITIONS") ?: arrayListOf()
        val sensitivities = intent.getStringArrayListExtra("SENSITIVITIES") ?: arrayListOf()
        val customSensitivities = intent.getStringArrayListExtra("CUSTOM_SENSITIVITIES") ?: arrayListOf()

        val body = mapOf<String, Any>(
            "age_group" to ageGroup,
            "conditions" to conditions,
            "sensitivities" to sensitivities,
            "custom_sensitivities" to customSensitivities
        )

        ApiClient.postAuth(this, "/user/health-profile", body) { success, json ->
            runOnUiThread {
                if (!success) {
                    android.widget.Toast.makeText(this, "Failed to save health profile", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (isEditing) {
            binding.tvTitle.text = "Profile Updated Successfully!"
            binding.tvSubtitle.text = "Your health preferences have been saved."
            binding.btnReturnSign.text = "Return to Personal Details"
        }

        binding.btnReturnSign.setOnClickListener {
            if (isEditing) {
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
