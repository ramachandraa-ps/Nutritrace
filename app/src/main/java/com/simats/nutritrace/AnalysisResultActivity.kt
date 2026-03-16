package com.simats.nutritrace

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityAnalysisResultBinding

class AnalysisResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        // Setup Thumbnail — from local URI or from server image_path
        val imageUriString = intent.getStringExtra("IMAGE_URI")
        if (imageUriString != null) {
            binding.cvThumbnail.visibility = View.VISIBLE
            binding.ivThumbnailImage.setImageURI(android.net.Uri.parse(imageUriString))
            binding.cvThumbnail.setOnClickListener { showImagePopup(imageUriString) }
        } else {
            // Try to load from server using image_path in SCAN_JSON
            val scanJsonStr = intent.getStringExtra("SCAN_JSON")
            if (scanJsonStr != null) {
                val scanObj = com.google.gson.Gson().fromJson(scanJsonStr, com.google.gson.JsonObject::class.java)?.getAsJsonObject("scan")
                val imagePath = scanObj?.get("image_path")?.let { if (it.isJsonNull) null else it.asString }
                if (!imagePath.isNullOrEmpty()) {
                    binding.cvThumbnail.visibility = View.VISIBLE
                    ApiClient.loadImage(binding.ivThumbnailImage, imagePath)
                }
            }
        }

        binding.ivEditName.setOnClickListener { showEditNameDialog() }

        binding.btnSaveScan.setOnClickListener {
            startActivity(android.content.Intent(this, ScanHistoryActivity::class.java))
            finish()
        }

        setupTabs()
        populateFromApi()
    }

    private fun populateFromApi() {
        val scanJsonStr = intent.getStringExtra("SCAN_JSON") ?: return
        val gson = com.google.gson.Gson()
        val jsonObj = gson.fromJson(scanJsonStr, com.google.gson.JsonObject::class.java)
        val scanObj = jsonObj.getAsJsonObject("scan")

        // Product name & brand
        val productName = scanObj.get("product_name")?.asString ?: "Unknown Product"
        val brandName = scanObj.get("brand_name")?.asString ?: ""
        binding.tvProductName.text = productName
        if (brandName.isNotEmpty()) {
            binding.etBrandName.setText(brandName)
        }

        // Score badge
        val score = scanObj.get("score")?.asInt ?: 0
        val riskLevel = scanObj.get("risk_level")?.asString ?: "MODERATE"
        binding.tvScoreValue.text = score.toString()
        when {
            score >= 70 -> {
                binding.tvScoreValue.setTextColor(Color.parseColor("#16B88A"))
                binding.tvScoreMax.setTextColor(Color.parseColor("#0D9668"))
            }
            score >= 40 -> {
                binding.tvScoreValue.setTextColor(Color.parseColor("#E67E22"))
                binding.tvScoreMax.setTextColor(Color.parseColor("#D68910"))
            }
            else -> {
                binding.tvScoreValue.setTextColor(Color.parseColor("#E74C3C"))
                binding.tvScoreMax.setTextColor(Color.parseColor("#C0392B"))
            }
        }

        // Overview / risk subtitle
        val overview = scanObj.get("overview")?.asString ?: ""
        binding.tvRiskSubtitle.text = overview
        binding.tvOverviewText.text = overview

        // Stat cards
        val sugarEstimate = scanObj.get("sugar_estimate")?.asString ?: "--"
        val additivesCount = scanObj.get("additives_count")?.asInt ?: 0
        val allergensArray = scanObj.getAsJsonArray("allergens_found")
        val allergensCount = allergensArray?.size() ?: 0

        binding.tvSugarValue.text = sugarEstimate
        binding.tvAdditivesValue.text = additivesCount.toString()
        binding.tvAllergensValue.text = if (allergensCount > 0) allergensCount.toString() else "No"

        // Ingredients tab
        val ingredients = scanObj.getAsJsonArray("ingredients")
        if (ingredients != null) {
            binding.llIngredientList.removeAllViews()
            for (i in 0 until ingredients.size()) {
                val ing = ingredients[i].asJsonObject
                val name = ing.get("ingredient_name")?.asString ?: ""
                val status = ing.get("status")?.asString ?: "SAFE"
                val reason = ing.get("reason")?.asString ?: ""

                val itemView = layoutInflater.inflate(R.layout.item_ingredient, binding.llIngredientList, false)
                val tvName = itemView.findViewById<TextView>(R.id.tvIngredientName)
                val tvBadge = itemView.findViewById<TextView>(R.id.tvBadge)
                val tvExplanation = itemView.findViewById<TextView>(R.id.tvExplanation)
                val ivExpand = itemView.findViewById<ImageView>(R.id.ivExpand)
                val clHeader = itemView.findViewById<View>(R.id.clHeader)

                tvName.text = name
                tvExplanation.text = reason

                when (status) {
                    "SAFE" -> {
                        tvBadge.text = "SAFE"
                        tvBadge.setBackgroundResource(R.drawable.bg_badge_safe)
                        tvBadge.setTextColor(Color.parseColor("#059669"))
                    }
                    "CAUTION" -> {
                        tvBadge.text = "CAUTION"
                        tvBadge.setBackgroundResource(R.drawable.bg_badge_caution)
                        tvBadge.setTextColor(Color.parseColor("#D97706"))
                    }
                    "AVOID" -> {
                        tvBadge.text = "AVOID"
                        tvBadge.setBackgroundResource(R.drawable.bg_badge_avoid)
                        tvBadge.setTextColor(Color.parseColor("#DC2626"))
                    }
                }

                var isExpanded = false
                clHeader.setOnClickListener {
                    isExpanded = !isExpanded
                    tvExplanation.visibility = if (isExpanded) View.VISIBLE else View.GONE
                    ivExpand.setImageResource(if (isExpanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down)
                }

                binding.llIngredientList.addView(itemView)
            }
        }

        // Guidance tab (dynamic bullet points)
        val guidance = scanObj.getAsJsonArray("guidance")
        if (guidance != null) {
            binding.llGuidanceList.removeAllViews()
            for (i in 0 until guidance.size()) {
                val tip = guidance[i].asString

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    val bottomMargin = (12 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { this.bottomMargin = bottomMargin }
                }

                val dot = View(this).apply {
                    val size = (6 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        topMargin = (6 * resources.displayMetrics.density).toInt()
                        marginEnd = (12 * resources.displayMetrics.density).toInt()
                    }
                    setBackgroundResource(R.drawable.bg_choice_circle)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#94A3B8"))
                }

                val tvTip = TextView(this).apply {
                    text = tip
                    setTextColor(Color.parseColor("#475569"))
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                row.addView(dot)
                row.addView(tvTip)
                binding.llGuidanceList.addView(row)
            }
        }
    }

    private fun showEditNameDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_name, null)
        val etProductName = dialogView.findViewById<android.widget.EditText>(R.id.etProductName)
        val tvCancel = dialogView.findViewById<TextView>(R.id.tvCancel)
        val tvSave = dialogView.findViewById<TextView>(R.id.tvSave)

        etProductName.setText(binding.tvProductName.text.toString().replace("\n", " "))

        val dialog = android.app.AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        tvCancel.setOnClickListener { dialog.dismiss() }
        tvSave.setOnClickListener {
            val newName = etProductName.text.toString().trim()
            if (newName.isNotEmpty()) {
                binding.tvProductName.text = newName
                val scanId = intent.getIntExtra("SCAN_ID", 0)
                if (scanId > 0) {
                    ApiClient.putAuth(this, "/scan/$scanId", mapOf("product_name" to newName)) { _, _ -> }
                }
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showImagePopup(uriString: String) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_image_preview)
        val ivFullImage = dialog.findViewById<ImageView>(R.id.ivFullImage)
        val ivCloseDialog = dialog.findViewById<ImageView>(R.id.ivCloseDialog)
        ivFullImage.setImageURI(android.net.Uri.parse(uriString))
        ivCloseDialog.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun setupTabs() {
        val tabs = listOf(binding.tabOverview, binding.tabIngredients, binding.tabGuidance)
        val contents = listOf(binding.llOverview, binding.llIngredients, binding.llGuidance)

        for (i in tabs.indices) {
            tabs[i].setOnClickListener {
                for (tab in tabs) {
                    tab.setBackgroundResource(android.R.color.transparent)
                    tab.setTextColor(Color.parseColor("#8B9BB4"))
                }
                for (content in contents) {
                    content.visibility = View.GONE
                }
                tabs[i].setBackgroundResource(R.drawable.bg_tab_selected)
                tabs[i].setTextColor(Color.parseColor("#0E1726"))
                contents[i].visibility = View.VISIBLE
            }
        }
    }
}
