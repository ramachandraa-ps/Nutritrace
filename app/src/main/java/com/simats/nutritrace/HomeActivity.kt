package com.simats.nutritrace

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
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

        binding.cardCompare.setOnClickListener {
            startActivity(android.content.Intent(this, CompareActivity::class.java))
        }

        binding.navCompare.setOnClickListener {
            startActivity(android.content.Intent(this, CompareActivity::class.java))
        }

        binding.cardScanProduct.setOnClickListener {
            startActivity(android.content.Intent(this, ScanIngredientsActivity::class.java))
        }

        binding.btnScanNav.setOnClickListener {
            startActivity(android.content.Intent(this, ScanIngredientsActivity::class.java))
        }

        binding.btnEmptyScan.setOnClickListener {
            startActivity(android.content.Intent(this, ScanIngredientsActivity::class.java))
        }

        binding.navHistory.setOnClickListener {
            startActivity(android.content.Intent(this, ScanHistoryActivity::class.java))
        }

        binding.llViewAllHistory.setOnClickListener {
            startActivity(android.content.Intent(this, ScanHistoryActivity::class.java))
        }

        val openAnalysis = {
            val intent = android.content.Intent(this, AnalysisResultActivity::class.java)
            // Use a mock resource URI since we don't have a real saved image
            val uriStr = "android.resource://${packageName}/${R.drawable.bg_splash_logo_square}"
            intent.putExtra("IMAGE_URI", uriStr)
            try {
                intent.data = android.net.Uri.parse(uriStr)
                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Ignore
            }
            startActivity(intent)
        }

        binding.navUser.setOnClickListener {
            startActivity(android.content.Intent(this, ProfileActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        
        val prefs = getSharedPreferences("NutriTracePrefs", android.content.Context.MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        val scansJson = prefs.getString("SAVED_SCANS", "[]")
        val type = object : com.google.gson.reflect.TypeToken<List<ScanData>>() {}.type
        val scanList: List<ScanData> = gson.fromJson(scansJson, type)
        
        if (scanList.isNotEmpty()) {
            binding.cardEmptyScanned.visibility = android.view.View.GONE
            binding.llScannedItems.visibility = android.view.View.VISIBLE
            binding.llScannedItems.removeAllViews()
            
            // Only show up to 3 items on Home Screen
            val topScans = scanList.take(3)
            for (scan in topScans) {
                val itemView = layoutInflater.inflate(R.layout.item_home_scanned, binding.llScannedItems, false)
                val tvName = itemView.findViewById<android.widget.TextView>(R.id.tvItemName)
                val tvBrand = itemView.findViewById<android.widget.TextView>(R.id.tvItemBrand)
                val tvScore = itemView.findViewById<android.widget.TextView>(R.id.tvScoreVal)
                val tvLabel = itemView.findViewById<android.widget.TextView>(R.id.tvScoreLabel)
                val ivItem = itemView.findViewById<android.widget.ImageView>(R.id.ivItem)
                
                tvName.text = scan.productName
                tvBrand.text = scan.brandName
                tvScore.text = scan.score.toString()
                
                // Color Code Score
                when {
                    scan.score >= 70 -> {
                        tvLabel.text = "SAFE"
                        tvLabel.setBackgroundResource(R.drawable.bg_pill_low)
                        tvLabel.setTextColor(android.graphics.Color.parseColor("#16B88A")) // home_status_low
                        ivItem.setImageResource(R.drawable.ic_shield_check_outline)
                    }
                    scan.score >= 40 -> {
                        tvLabel.text = "MODERATE"
                        tvLabel.setBackgroundResource(R.drawable.bg_pill_moderate)
                        tvLabel.setTextColor(android.graphics.Color.parseColor("#F5A623")) // home_status_mod
                        ivItem.setImageResource(R.drawable.ic_clock) // or a warning icon
                    }
                    else -> {
                        tvLabel.text = "HIGH"
                        tvLabel.setBackgroundResource(R.drawable.bg_pill_high)
                        tvLabel.setTextColor(android.graphics.Color.parseColor("#E74C3C")) // home_status_high
                        ivItem.setImageResource(R.drawable.ic_shield_check_outline) // change to high risk icon if exists
                    }
                }
                
                // Open Result on Click
                itemView.setOnClickListener {
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
                
                binding.llScannedItems.addView(itemView)
            }
            
            // Un-hide the risk pill, populate score for the overall risk card to reflect the latest scan
            binding.llRiskPill.visibility = android.view.View.VISIBLE
            binding.ivDialDial.visibility = android.view.View.VISIBLE
            binding.tvEmptyRiskLabel.visibility = android.view.View.GONE
            binding.tvScoreValue.text = scanList.first().score.toString()
            
        } else {
            binding.cardEmptyScanned.visibility = android.view.View.VISIBLE
            binding.llScannedItems.visibility = android.view.View.GONE
            
            binding.llRiskPill.visibility = android.view.View.GONE
            binding.ivDialDial.visibility = android.view.View.GONE
            binding.tvEmptyRiskLabel.visibility = android.view.View.VISIBLE
            binding.tvScoreValue.text = "--"
        }
    }
}
