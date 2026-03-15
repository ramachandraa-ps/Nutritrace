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

        loadHistory("")

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                loadHistory(s?.toString()?.trim() ?: "")
            }
        })
    }

    private fun loadHistory(search: String) {
        val endpoint = if (search.isEmpty()) "/scan/history" else "/scan/history?search=$search"

        ApiClient.getAuth(this, endpoint) { success, json ->
            runOnUiThread {
                if (success && json?.get("success")?.asBoolean == true) {
                    val scansArray = json.getAsJsonArray("scans")

                    if (scansArray.size() > 0) {
                        binding.llEmptyHistory.visibility = android.view.View.GONE
                        binding.llHistoryList.visibility = android.view.View.VISIBLE
                        binding.llSearchRow.visibility = android.view.View.VISIBLE
                        binding.llHistoryList.removeAllViews()

                        for (i in 0 until scansArray.size()) {
                            val s = scansArray[i].asJsonObject
                            val scanId = s.get("id").asInt
                            val productName = s.get("product_name")?.asString ?: "Unknown"
                            val brandName = s.get("brand_name")?.asString ?: ""
                            val score = s.get("score").asInt

                            val itemView = layoutInflater.inflate(R.layout.item_scan_history, binding.llHistoryList, false)
                            val tvProductName = itemView.findViewById<android.widget.TextView>(R.id.tvProductName)
                            val tvBrandName = itemView.findViewById<android.widget.TextView>(R.id.tvBrandName)
                            val tvRiskBadge = itemView.findViewById<android.widget.TextView>(R.id.tvRiskBadge)

                            tvProductName.text = productName
                            tvBrandName.text = brandName

                            when {
                                score >= 70 -> {
                                    tvRiskBadge.text = "SAFE"
                                    tvRiskBadge.setBackgroundResource(R.drawable.bg_pill_safe)
                                    tvRiskBadge.setTextColor(android.graphics.Color.parseColor("#16B88A"))
                                }
                                score >= 40 -> {
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

                            itemView.setOnClickListener {
                                ApiClient.getAuth(this, "/scan/$scanId") { detailSuccess, detailJson ->
                                    runOnUiThread {
                                        if (detailSuccess && detailJson != null) {
                                            val intent = android.content.Intent(this, AnalysisResultActivity::class.java)
                                            intent.putExtra("SCAN_ID", scanId)
                                            intent.putExtra("SCAN_JSON", detailJson.toString())
                                            startActivity(intent)
                                        }
                                    }
                                }
                            }

                            binding.llHistoryList.addView(itemView)
                        }
                    } else {
                        binding.llEmptyHistory.visibility = android.view.View.VISIBLE
                        binding.llHistoryList.visibility = android.view.View.GONE
                        binding.llSearchRow.visibility = android.view.View.GONE
                    }
                }
            }
        }
    }
}
