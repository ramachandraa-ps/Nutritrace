package com.simats.nutritrace

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityScanHistoryBinding

class ScanHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanHistoryBinding.inflate(layoutInflater)
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

        binding.navHome.setOnClickListener {
            startActivity(android.content.Intent(this, HomeActivity::class.java))
            finish()
        }

        binding.navCompare.setOnClickListener {
            startActivity(android.content.Intent(this, CompareActivity::class.java))
            finish()
        }

        binding.btnScanNav.setOnClickListener {
            startActivity(android.content.Intent(this, ScanIngredientsActivity::class.java))
        }

        binding.navUser.setOnClickListener {
            startActivity(android.content.Intent(this, ProfileActivity::class.java))
            finish()
        }

        val openAnalysis = {
            val intent = android.content.Intent(this, AnalysisResultActivity::class.java)
            val uriStr = "android.resource://${packageName}/${R.drawable.bg_splash_logo_square}"
            intent.putExtra("IMAGE_URI", uriStr)
            try {
                intent.data = android.net.Uri.parse(uriStr)
                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // ignore
            }
            startActivity(intent)
        }

        val showImageDialog = {
            val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val dialogView = layoutInflater.inflate(R.layout.dialog_image_preview, null)
            
            val ivClose = dialogView.findViewById<android.widget.ImageView>(R.id.ivCloseDialog)
            val ivPreview = dialogView.findViewById<android.widget.ImageView>(R.id.ivFullImage)

            ivPreview.setImageResource(R.drawable.bg_splash_logo_square) // using mock image

            ivClose.setOnClickListener {
                dialog.dismiss()
            }

            dialog.setContentView(dialogView)
            dialog.show()
        }

        // Read actual saved scans
        val prefs = getSharedPreferences("NutriTracePrefs", android.content.Context.MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        val scansJson = prefs.getString("SAVED_SCANS", "[]")
        val type = object : com.google.gson.reflect.TypeToken<List<ScanData>>() {}.type
        val scanList: List<ScanData> = gson.fromJson(scansJson, type)
        
        if (scanList.isNotEmpty()) {
            binding.llEmptyHistory.visibility = android.view.View.GONE
            binding.llHistoryList.visibility = android.view.View.VISIBLE
            binding.llSearchRow.visibility = android.view.View.VISIBLE
            binding.llHistoryList.removeAllViews()
            
            val itemViews = mutableListOf<Pair<android.view.View, ScanData>>()
            
            for (scan in scanList) {
                val itemView = layoutInflater.inflate(R.layout.item_scan_history, binding.llHistoryList, false)
                
                val tvProductName = itemView.findViewById<android.widget.TextView>(R.id.tvProductName)
                val tvBrandName = itemView.findViewById<android.widget.TextView>(R.id.tvBrandName)
                val tvRiskBadge = itemView.findViewById<android.widget.TextView>(R.id.tvRiskBadge)
                val ivHistoryThumbnail = itemView.findViewById<android.widget.ImageView>(R.id.ivHistoryThumbnail)
                
                tvProductName.text = scan.productName
                tvBrandName.text = scan.brandName // Specific brand name injection!
                
                // Color Code Score
                when {
                    scan.score >= 70 -> {
                        tvRiskBadge.text = "SAFE"
                        tvRiskBadge.setBackgroundResource(R.drawable.bg_pill_safe)
                        tvRiskBadge.setTextColor(android.graphics.Color.parseColor("#16B88A"))
                    }
                    scan.score >= 40 -> {
                        tvRiskBadge.text = "MODERATE"
                        tvRiskBadge.setBackgroundResource(R.drawable.bg_pill_moderate)
                        tvRiskBadge.setTextColor(android.graphics.Color.parseColor("#F5A623"))
                    }
                    else -> {
                        tvRiskBadge.text = "HIGH"
                        tvRiskBadge.setBackgroundResource(R.drawable.bg_pill_high)
                        tvRiskBadge.setTextColor(android.graphics.Color.parseColor("#E74C3C"))
                    }
                }
                
                itemView.setOnClickListener { openAnalysis() } // Clicking anywhere on the row opens result
                ivHistoryThumbnail.setOnClickListener { showImageDialog() } // Clicking image opens popup
                
                binding.llHistoryList.addView(itemView)
                itemViews.add(itemView to scan)
            }
            
            // Re-bind Search Filter
            binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val query = s?.toString()?.trim()?.lowercase() ?: ""
                    itemViews.forEach { (view, scan) ->
                        if (scan.productName.lowercase().contains(query) || scan.brandName.lowercase().contains(query)) {
                            view.visibility = android.view.View.VISIBLE
                        } else {
                            view.visibility = android.view.View.GONE
                        }
                    }
                }
            })

        } else {
            binding.llEmptyHistory.visibility = android.view.View.VISIBLE
            binding.llHistoryList.visibility = android.view.View.GONE
            binding.llSearchRow.visibility = android.view.View.GONE
        }
    }
}
