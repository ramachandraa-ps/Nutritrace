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
        android.util.Log.d("NutritionFlow", "AnalysisResultActivity onCreate started")
        binding = ActivityAnalysisResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        // Setup Thumbnail and Popup
        val imageUriString = intent.getStringExtra("IMAGE_URI")
        if (imageUriString != null) {
            binding.cvThumbnail.visibility = View.VISIBLE
            binding.ivThumbnailImage.setImageURI(android.net.Uri.parse(imageUriString))

            binding.cvThumbnail.setOnClickListener {
                showImagePopup(imageUriString)
            }
        }

        binding.ivEditName.setOnClickListener {
            showEditNameDialog()
        }

        binding.btnSaveScan.setOnClickListener {
            val prefs = getSharedPreferences("NutriTracePrefs", android.content.Context.MODE_PRIVATE)
            val gson = com.google.gson.Gson()
            
            // 1. Get existing scans
            val scansJson = prefs.getString("SAVED_SCANS", "[]")
            val type = object : com.google.gson.reflect.TypeToken<MutableList<ScanData>>() {}.type
            val scanList: MutableList<ScanData> = gson.fromJson(scansJson, type)
            
            // 2. Create new scan (mocking score/risk based on current dummy UI for now)
            val productName = binding.tvProductName.text.toString()
            val brandName = binding.etBrandName.text.toString()
            val newScan = ScanData(
                productName = productName,
                brandName = if (brandName.isBlank() || brandName == "Add Brand Name") "Unknown" else brandName,
                score = 65, // Mock score
                riskLevel = "MODERATE", // Mock risk
                time = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            )
            
            // 3. Add to front of list and save
            scanList.add(0, newScan)
            prefs.edit().putString("SAVED_SCANS", gson.toJson(scanList)).apply()
            
            prefs.edit().putBoolean("HAS_MOCK_SCANS", true).apply() // Keep flag for backward compatibility
            
            val intent = android.content.Intent(this, ScanHistoryActivity::class.java)
            startActivity(intent)
            finish()
        }

        setupTabs()
        populateIngredients()
    }

    private fun showEditNameDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_name, null)
        val etProductName = dialogView.findViewById<android.widget.EditText>(R.id.etProductName)
        val tvCancel = dialogView.findViewById<TextView>(R.id.tvCancel)
        val tvSave = dialogView.findViewById<TextView>(R.id.tvSave)

        // Pre-fill current name but replace newlines with spaces for easier editing
        etProductName.setText(binding.tvProductName.text.toString().replace("\n", " "))

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Make dialog background transparent if using custom rounded drawable
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        tvCancel.setOnClickListener { dialog.dismiss() }
        
        tvSave.setOnClickListener {
            val newName = etProductName.text.toString().trim()
            if (newName.isNotEmpty()) {
                // Optionally re-insert a newline if it's long, or just let it wrap naturally
                binding.tvProductName.text = newName
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
        
        ivCloseDialog.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun setupTabs() {
        val tabs = listOf(
            binding.tabOverview,
            binding.tabIngredients,
            binding.tabRisk,
            binding.tabGuidance
        )
        val contents = listOf(
            binding.llOverview,
            binding.llIngredients,
            binding.llRiskBreakdown,
            binding.llGuidance
        )

        for (i in tabs.indices) {
            tabs[i].setOnClickListener {
                // Reset all tabs to unselected style
                for (tab in tabs) {
                    tab.setBackgroundResource(android.R.color.transparent)
                    tab.setTextColor(Color.parseColor("#8B9BB4")) // home_text_sub
                }
                // Reset all contents to gone
                for (content in contents) {
                    content.visibility = View.GONE
                }

                // Set selected tab style
                tabs[i].setBackgroundResource(R.drawable.bg_tab_selected)
                tabs[i].setTextColor(Color.parseColor("#0E1726")) // home_text_title
                
                // Show selected content
                contents[i].visibility = View.VISIBLE
            }
        }
    }

    private fun populateIngredients() {
        data class Ingredient(val name: String, val explanation: String, val status: String)

        val ingredientList = listOf(
            Ingredient("Whole Grain Oats", "A healthy source of fiber and energy-boosting nutrients.", "SAFE"),
            Ingredient("Cane Sugar", "Added sugar that can spike blood glucose levels if consumed in excess.", "CAUTION"),
            Ingredient("Vegetable Oil", "A heavily processed oil lacking nutritional value and potentially inflammatory.", "CAUTION"),
            Ingredient("Honey", "A natural sweetener containing trace antioxidants, but still a sugar.", "SAFE"),
            Ingredient("Artificial Flavor", "Synthetic chemicals used for taste enhancement, providing zero nutritional value.", "AVOID")
        )

        for (ingredient in ingredientList) {
            val itemView = layoutInflater.inflate(R.layout.item_ingredient, binding.llIngredientList, false)
            
            val tvName = itemView.findViewById<TextView>(R.id.tvIngredientName)
            val tvBadge = itemView.findViewById<TextView>(R.id.tvBadge)
            val tvExplanation = itemView.findViewById<TextView>(R.id.tvExplanation)
            val ivExpand = itemView.findViewById<ImageView>(R.id.ivExpand)
            val clHeader = itemView.findViewById<View>(R.id.clHeader)

            tvName.text = ingredient.name
            tvExplanation.text = ingredient.explanation

            // Assign styles based on status
            when (ingredient.status) {
                "SAFE" -> {
                    tvBadge.text = "✓ SAFE"
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_safe)
                    tvBadge.setTextColor(Color.parseColor("#059669")) // Dark green text
                }
                "CAUTION" -> {
                    tvBadge.text = "⚠️ CAUTION"
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_caution)
                    tvBadge.setTextColor(Color.parseColor("#D97706")) // Amber text
                }
                "AVOID" -> {
                    tvBadge.text = "✕ AVOID"
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_avoid)
                    tvBadge.setTextColor(Color.parseColor("#DC2626")) // Red text
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
}
