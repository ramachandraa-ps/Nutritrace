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
        val navContainer = bottomNavCard.getChildAt(0)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            navContainer.setPadding(
                navContainer.paddingLeft,
                navContainer.paddingTop,
                navContainer.paddingRight,
                insets.bottom
            )
            bottomNavCard.post {
                val navHeight = bottomNavCard.height
                mainScrollView?.setPadding(
                    mainScrollView.paddingLeft,
                    mainScrollView.paddingTop,
                    mainScrollView.paddingRight,
                    navHeight
                )
            }
            windowInsets
        }

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

        // Load user info into profile header
        val prefs = getSharedPreferences("NutriTracePrefs", MODE_PRIVATE)
        val savedName = prefs.getString("USER_FULLNAME", null)
        val savedEmail = prefs.getString("USER_EMAIL", null)
        if (savedName != null) binding.tvProfileName.text = savedName
        if (savedEmail != null) binding.tvProfileEmail.text = savedEmail

        // Also fetch fresh data from backend
        ApiClient.getAuth(this, "/user/profile") { success, json ->
            runOnUiThread {
                if (success && json?.get("success")?.asBoolean == true) {
                    val user = json.getAsJsonObject("user")
                    binding.tvProfileName.text = user.get("fullname")?.asString ?: ""
                    binding.tvProfileEmail.text = user.get("email")?.asString ?: ""
                }
            }
        }

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
            // Get stored password for deletion confirmation
            val prefs = getSharedPreferences("NutriTracePrefs", MODE_PRIVATE)
            // Use a placeholder - backend requires password but we use confirmation text
            ApiClient.deleteAuth(this, "/user/account", mapOf("password" to etConfirmDelete.text.toString())) { success, _ ->
                runOnUiThread {
                    dialog.dismiss()
                    ApiClient.clearSession(this)
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
            }
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
            ApiClient.clearSession(this)
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }

        dialog.show()
    }
}
