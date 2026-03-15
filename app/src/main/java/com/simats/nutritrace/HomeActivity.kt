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
        binding.navUser.setOnClickListener {
            startActivity(android.content.Intent(this, ProfileActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    private fun loadDashboard() {
        ApiClient.getAuth(this, "/dashboard") { success, json ->
            runOnUiThread {
                if (success && json?.get("success")?.asBoolean == true) {
                    // Update greeting with real user name
                    val user = json.getAsJsonObject("user")
                    val fullname = user.get("fullname")?.asString ?: ""
                    val firstName = fullname.split(" ").firstOrNull() ?: fullname
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    val greeting = when {
                        hour < 12 -> "Good Morning"
                        hour < 17 -> "Good Afternoon"
                        else -> "Good Evening"
                    }
                    binding.tvGreeting.text = "$greeting, $firstName"

                    val scansArray = json.getAsJsonArray("latest_scans")

                    val scanList = mutableListOf<ScanData>()
                    for (i in 0 until scansArray.size()) {
                        val s = scansArray[i].asJsonObject
                        scanList.add(ScanData(
                            id = s.get("id").asInt,
                            productName = s.get("product_name")?.asString ?: "Unknown",
                            brandName = s.get("brand_name")?.asString ?: "",
                            score = s.get("score").asInt,
                            riskLevel = s.get("risk_level").asString,
                            time = s.get("scanned_at")?.asString ?: ""
                        ))
                    }

                    if (scanList.isNotEmpty()) {
                        binding.cardEmptyScanned.visibility = android.view.View.GONE
                        binding.llScannedItems.visibility = android.view.View.VISIBLE
                        binding.llScannedItems.removeAllViews()

                        for (scan in scanList) {
                            val itemView = layoutInflater.inflate(R.layout.item_home_scanned, binding.llScannedItems, false)
                            val tvName = itemView.findViewById<android.widget.TextView>(R.id.tvItemName)
                            val tvBrand = itemView.findViewById<android.widget.TextView>(R.id.tvItemBrand)
                            val tvScore = itemView.findViewById<android.widget.TextView>(R.id.tvScoreVal)
                            val tvLabel = itemView.findViewById<android.widget.TextView>(R.id.tvScoreLabel)
                            val ivItem = itemView.findViewById<android.widget.ImageView>(R.id.ivItem)

                            tvName.text = scan.productName
                            tvBrand.text = scan.brandName
                            tvScore.text = scan.score.toString()

                            when {
                                scan.score >= 70 -> {
                                    tvLabel.text = "SAFE"
                                    tvLabel.setBackgroundResource(R.drawable.bg_pill_low)
                                    tvLabel.setTextColor(android.graphics.Color.parseColor("#16B88A"))
                                    ivItem.setImageResource(R.drawable.ic_shield_check_outline)
                                }
                                scan.score >= 40 -> {
                                    tvLabel.text = "MODERATE"
                                    tvLabel.setBackgroundResource(R.drawable.bg_pill_moderate)
                                    tvLabel.setTextColor(android.graphics.Color.parseColor("#F5A623"))
                                    ivItem.setImageResource(R.drawable.ic_clock)
                                }
                                else -> {
                                    tvLabel.text = "HIGH"
                                    tvLabel.setBackgroundResource(R.drawable.bg_pill_high)
                                    tvLabel.setTextColor(android.graphics.Color.parseColor("#E74C3C"))
                                    ivItem.setImageResource(R.drawable.ic_shield_check_outline)
                                }
                            }

                            itemView.setOnClickListener {
                                ApiClient.getAuth(this, "/scan/${scan.id}") { detailSuccess, detailJson ->
                                    runOnUiThread {
                                        if (detailSuccess && detailJson != null) {
                                            val intent = android.content.Intent(this, AnalysisResultActivity::class.java)
                                            intent.putExtra("SCAN_ID", scan.id)
                                            intent.putExtra("SCAN_JSON", detailJson.toString())
                                            startActivity(intent)
                                        }
                                    }
                                }
                            }

                            binding.llScannedItems.addView(itemView)
                        }

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
        }
    }
}
