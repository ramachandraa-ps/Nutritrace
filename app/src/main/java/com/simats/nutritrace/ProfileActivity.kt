package com.simats.nutritrace

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bottomNavCard = findViewById<androidx.cardview.widget.CardView>(R.id.bottomNavCard)
        val mainScrollView = findViewById<android.widget.ScrollView>(R.id.mainScrollView)
        
        // Find the inner LinearLayout of the CardView to apply padding to
        val navContainer = bottomNavCard.getChildAt(0)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            
            // Apply padding to the inner container so the background extends to the bottom
            navContainer.setPadding(
                navContainer.paddingLeft,
                navContainer.paddingTop,
                navContainer.paddingRight,
                insets.bottom // Just the inset, no extra gap
            )

            bottomNavCard.post {
                val navHeight = bottomNavCard.height
                mainScrollView?.setPadding(
                    mainScrollView.paddingLeft,
                    mainScrollView.paddingTop,
                    mainScrollView.paddingRight,
                    navHeight // Scroll up above the *total* height of the nav (including its new pad)
                )
            }
            windowInsets
        }

        // Navigation
        binding.navHome.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        binding.navCompare.setOnClickListener {
            val intent = Intent(this, CompareActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        binding.navHistory.setOnClickListener {
            val intent = Intent(this, ScanHistoryActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        binding.btnScanNav.setOnClickListener {
            startActivity(Intent(this, ScanIngredientsActivity::class.java))
        }

        // Profile Section Navigation
        binding.llPersonalDetails.setOnClickListener {
            startActivity(Intent(this, PersonalDetailsActivity::class.java))
        }

        binding.llChangePassword.setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        binding.llPrivacyPolicy.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        binding.llTerms.setOnClickListener {
            startActivity(Intent(this, TermsOfServiceActivity::class.java))
        }

        binding.llHelp.setOnClickListener {
            startActivity(Intent(this, HelpGuideActivity::class.java))
        }

        binding.cardLogout.setOnClickListener {
            showLogoutDialog()
        }

        binding.llDeleteAccount.setOnClickListener {
            showDeleteAccountDialog()
        }
    }

    private fun showDeleteAccountDialog() {
        val dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_delete_account, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val btnCancel = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnDelete)
        val etConfirmDelete = dialogView.findViewById<android.widget.EditText>(R.id.etConfirmDelete)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        etConfirmDelete.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s?.toString()?.trim() == "DELETE") {
                    btnDelete.isEnabled = true
                    btnDelete.alpha = 1.0f
                } else {
                    btnDelete.isEnabled = false
                    btnDelete.alpha = 0.5f
                }
            }
        })

        btnDelete.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }

        dialog.show()
    }

    private fun showLogoutDialog() {
        val dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_logout, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val btnCancel = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnCancel)
        val btnLogout = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnLogout)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnLogout.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }

        dialog.show()
    }
}
