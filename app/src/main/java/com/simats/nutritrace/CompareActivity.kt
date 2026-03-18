package com.simats.nutritrace

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityCompareBinding
import java.io.File

class CompareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompareBinding
    private var isProductAUploaded = false
    private var isProductBUploaded = false

    private var imageUriA: String? = null
    private var imageUriB: String? = null
    private var scanIdA: Int? = null
    private var scanIdB: Int? = null
    private var isComparing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bottomNavCard = findViewById<androidx.cardview.widget.CardView>(R.id.bottomNavCard)
        val mainScrollView = findViewById<android.widget.ScrollView>(R.id.mainScrollView)
        val navContainer = bottomNavCard.getChildAt(0)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            navContainer.setPadding(navContainer.paddingLeft, navContainer.paddingTop, navContainer.paddingRight, insets.bottom)
            bottomNavCard.post {
                mainScrollView?.setPadding(mainScrollView.paddingLeft, mainScrollView.paddingTop, mainScrollView.paddingRight, bottomNavCard.height)
            }
            windowInsets
        }

        // Bottom nav
        binding.navHome.setOnClickListener { startActivity(Intent(this, HomeActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }); finish() }
        binding.btnScanNav.setOnClickListener { startActivity(Intent(this, ScanIngredientsActivity::class.java)) }
        binding.navHistory.setOnClickListener { startActivity(Intent(this, ScanHistoryActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }); finish() }
        binding.navUser.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)); finish() }

        // Product card click → show source chooser dialog
        val clickA = View.OnClickListener { if (!isComparing) showProductSourceDialog("A") }
        binding.cvScanA.setOnClickListener(clickA)
        binding.cardProductA.setOnClickListener(clickA)

        val clickB = View.OnClickListener { if (!isComparing) showProductSourceDialog("B") }
        binding.cvScanB.setOnClickListener(clickB)
        binding.cardProductB.setOnClickListener(clickB)

        // Remove buttons
        binding.ivRemoveA.setOnClickListener {
            if (!isComparing) {
                binding.ivProductAImage.visibility = View.GONE
                binding.ivRemoveA.visibility = View.GONE
                binding.ivScanIconA.visibility = View.VISIBLE
                binding.cardProductA.setBackgroundResource(R.drawable.bg_outline_card)
                isProductAUploaded = false; imageUriA = null; scanIdA = null
                val c = View.OnClickListener { if (!isComparing) showProductSourceDialog("A") }
                binding.cvScanA.setOnClickListener(c); binding.cardProductA.setOnClickListener(c)
                updateComparisonVisibility()
            }
        }
        binding.ivRemoveB.setOnClickListener {
            if (!isComparing) {
                binding.ivProductBImage.visibility = View.GONE
                binding.ivRemoveB.visibility = View.GONE
                binding.ivScanIconB.visibility = View.VISIBLE
                binding.cardProductB.setBackgroundResource(R.drawable.bg_outline_card)
                isProductBUploaded = false; imageUriB = null; scanIdB = null
                val c = View.OnClickListener { if (!isComparing) showProductSourceDialog("B") }
                binding.cvScanB.setOnClickListener(c); binding.cardProductB.setOnClickListener(c)
                updateComparisonVisibility()
            }
        }

        binding.llChoiceA.setOnClickListener { handlePreferenceSelection(true) }
        binding.llChoiceB.setOnClickListener { handlePreferenceSelection(false) }
    }

    // ──────────────────────────────────────────────
    // Product Source Chooser Dialog
    // ──────────────────────────────────────────────
    private fun showProductSourceDialog(slot: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_product_source, null)
        val dialog = android.app.AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<LinearLayout>(R.id.llScanNew).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, ScanIngredientsActivity::class.java)
            intent.putExtra("SCAN_SLOT", slot)
            startActivityForResult(intent, 2001)
        }

        dialogView.findViewById<LinearLayout>(R.id.llFromHistory).setOnClickListener {
            dialog.dismiss()
            showHistoryPickerDialog(slot)
        }

        dialog.show()
    }

    // ──────────────────────────────────────────────
    // History Picker Dialog
    // ──────────────────────────────────────────────
    private fun showHistoryPickerDialog(slot: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_history_picker, null)
        val dialog = android.app.AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        val pbLoading = dialogView.findViewById<android.widget.ProgressBar>(R.id.pbLoading)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tvEmptyHistory)
        val svList = dialogView.findViewById<android.widget.ScrollView>(R.id.svHistoryList)
        val llList = dialogView.findViewById<LinearLayout>(R.id.llHistoryPickerList)
        dialogView.findViewById<ImageView>(R.id.ivCloseHistoryPicker).setOnClickListener { dialog.dismiss() }

        dialog.show()

        // Fetch scan history
        ApiClient.getAuth(this, "/scan/history") { success, json ->
            runOnUiThread {
                pbLoading.visibility = View.GONE
                if (!success || json?.get("success")?.asBoolean != true) {
                    tvEmpty.text = "Failed to load history"
                    tvEmpty.visibility = View.VISIBLE
                    return@runOnUiThread
                }

                val scans = json.getAsJsonArray("scans")
                if (scans == null || scans.size() == 0) {
                    tvEmpty.visibility = View.VISIBLE
                    return@runOnUiThread
                }

                svList.visibility = View.VISIBLE
                llList.removeAllViews()

                for (i in 0 until scans.size()) {
                    val s = scans[i].asJsonObject
                    val scanId = s.get("id").asInt
                    val productName = s.get("product_name")?.asString ?: "Unknown"
                    val brandName = s.get("brand_name")?.asString ?: ""
                    val score = s.get("score").asInt
                    val imagePath = s.get("image_path")?.let { if (it.isJsonNull) "" else it.asString } ?: ""

                    val itemView = layoutInflater.inflate(R.layout.item_scan_history, llList, false)
                    val tvName = itemView.findViewById<TextView>(R.id.tvProductName)
                    val tvBrand = itemView.findViewById<TextView>(R.id.tvBrandName)
                    val tvBadge = itemView.findViewById<TextView>(R.id.tvRiskBadge)
                    val ivThumb = itemView.findViewById<ImageView>(R.id.ivHistoryThumbnail)

                    tvName.text = productName
                    tvBrand.text = if (brandName.isNotBlank()) brandName else "Unknown"

                    when {
                        score >= 70 -> { tvBadge.text = "SAFE"; tvBadge.setBackgroundResource(R.drawable.bg_pill_safe); tvBadge.setTextColor(Color.parseColor("#16B88A")) }
                        score >= 40 -> { tvBadge.text = "MODERATE"; tvBadge.setBackgroundResource(R.drawable.bg_pill_moderate); tvBadge.setTextColor(Color.parseColor("#F5A623")) }
                        else -> { tvBadge.text = "HIGH"; tvBadge.setBackgroundResource(R.drawable.bg_pill_high); tvBadge.setTextColor(Color.parseColor("#E74C3C")) }
                    }

                    if (imagePath.isNotEmpty()) {
                        ApiClient.loadImage(ivThumb, imagePath)
                    }

                    itemView.setOnClickListener {
                        dialog.dismiss()
                        onHistoryProductSelected(slot, scanId, productName, imagePath)
                    }

                    llList.addView(itemView)
                }
            }
        }
    }

    // ──────────────────────────────────────────────
    // Handle history product selection
    // ──────────────────────────────────────────────
    private fun onHistoryProductSelected(slot: String, scanId: Int, productName: String, imagePath: String) {
        if (slot == "A") {
            scanIdA = scanId
            imageUriA = null // No local URI — image is on server
            isProductAUploaded = true

            binding.ivProductAImage.visibility = View.VISIBLE
            binding.ivScanIconA.visibility = View.GONE
            binding.ivRemoveA.visibility = View.VISIBLE
            binding.cardProductA.setBackgroundResource(R.drawable.bg_outline_card_highlight)
            binding.cardProductA.setOnClickListener(null)
            binding.cvScanA.setOnClickListener(null)

            if (imagePath.isNotEmpty()) {
                ApiClient.loadImage(binding.ivProductAImage, imagePath)
            }
        } else {
            scanIdB = scanId
            imageUriB = null
            isProductBUploaded = true

            binding.ivProductBImage.visibility = View.VISIBLE
            binding.ivScanIconB.visibility = View.GONE
            binding.ivRemoveB.visibility = View.VISIBLE
            binding.cardProductB.setBackgroundResource(R.drawable.bg_outline_card_highlight)
            binding.cardProductB.setOnClickListener(null)
            binding.cvScanB.setOnClickListener(null)

            if (imagePath.isNotEmpty()) {
                ApiClient.loadImage(binding.ivProductBImage, imagePath)
            }
        }
        updateComparisonVisibility()
    }

    // ──────────────────────────────────────────────
    // onActivityResult — from camera scan
    // ──────────────────────────────────────────────
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == RESULT_OK) {
            val slot = data?.getStringExtra("SCAN_SLOT")
            val imageUriString = data?.getStringExtra("IMAGE_URI")
            if (slot == "A") {
                binding.ivProductAImage.visibility = View.VISIBLE
                if (imageUriString != null) {
                    binding.ivProductAImage.setImageURI(android.net.Uri.parse(imageUriString))
                    binding.ivProductAImage.setOnClickListener { showImageDialog(imageUriString) }
                }
                binding.ivRemoveA.visibility = View.VISIBLE
                binding.ivScanIconA.visibility = View.GONE
                binding.cardProductA.setBackgroundResource(R.drawable.bg_outline_card_highlight)
                binding.cardProductA.setOnClickListener(null)
                binding.cvScanA.setOnClickListener(null)
                isProductAUploaded = true
                imageUriA = imageUriString
                scanIdA = null
                updateComparisonVisibility()
            } else if (slot == "B") {
                binding.ivProductBImage.visibility = View.VISIBLE
                if (imageUriString != null) {
                    binding.ivProductBImage.setImageURI(android.net.Uri.parse(imageUriString))
                    binding.ivProductBImage.setOnClickListener { showImageDialog(imageUriString) }
                }
                binding.ivRemoveB.visibility = View.VISIBLE
                binding.ivScanIconB.visibility = View.GONE
                binding.cardProductB.setBackgroundResource(R.drawable.bg_outline_card_highlight)
                binding.cardProductB.setOnClickListener(null)
                binding.cvScanB.setOnClickListener(null)
                isProductBUploaded = true
                imageUriB = imageUriString
                scanIdB = null
                updateComparisonVisibility()
            }
        }
    }

    // ──────────────────────────────────────────────
    // Comparison flow
    // ──────────────────────────────────────────────
    private fun updateComparisonVisibility() {
        if (isProductAUploaded && isProductBUploaded) {
            binding.llStartPlaceholder.visibility = View.GONE
            binding.llComparisonResults.visibility = View.GONE
            binding.llLoadingState.visibility = View.VISIBLE
            resetChoiceState()
            startComparisonFlow()
        } else {
            binding.llStartPlaceholder.visibility = View.VISIBLE
            binding.llComparisonResults.visibility = View.GONE
            binding.llLoadingState.visibility = View.GONE
        }
    }

    private fun resetChoiceState() {
        binding.ivChoiceATick.visibility = View.GONE
        binding.tvChoiceACircle.visibility = View.VISIBLE
        binding.tvChoiceAText.text = "Product A"
        binding.tvChoiceAText.setTextColor(Color.parseColor("#94A3B8"))
        binding.ivChoiceBTick.visibility = View.GONE
        binding.tvChoiceBCircle.visibility = View.VISIBLE
        binding.tvChoiceBText.text = "Product B"
        binding.tvChoiceBText.setTextColor(Color.parseColor("#94A3B8"))
    }

    private fun startComparisonFlow() {
        if (isComparing) return
        isComparing = true

        runOnUiThread {
            binding.tvLoadingTitle.text = "Analyzing Products..."
            binding.tvLoadingSubtitle.text = "Preparing comparison..."
        }

        // Resolve scan IDs — upload images only for products that don't have IDs yet
        resolveScanId("A") { idA ->
            if (idA == null) {
                isComparing = false
                runOnUiThread { showError("Failed to analyze Product A"); resetToPlaceholder() }
                return@resolveScanId
            }
            scanIdA = idA

            resolveScanId("B") { idB ->
                if (idB == null) {
                    isComparing = false
                    runOnUiThread { showError("Failed to analyze Product B"); resetToPlaceholder() }
                    return@resolveScanId
                }
                scanIdB = idB

                // Both IDs ready — compare
                runOnUiThread { binding.tvLoadingSubtitle.text = "Comparing products with AI..." }

                ApiClient.postAuth(this, "/compare", mapOf("scan_id_a" to idA, "scan_id_b" to idB)) { success, response ->
                    isComparing = false
                    if (!success || response == null) {
                        runOnUiThread { showError(response?.get("message")?.asString ?: "Comparison failed"); resetToPlaceholder() }
                        return@postAuth
                    }

                    val comparison = response.getAsJsonObject("comparison")
                    if (comparison == null) {
                        runOnUiThread { showError("Invalid comparison response"); resetToPlaceholder() }
                        return@postAuth
                    }

                    val productA = comparison.getAsJsonObject("product_a")
                    val productB = comparison.getAsJsonObject("product_b")
                    val recommendation = comparison.get("recommendation")?.asString ?: "NEITHER"
                    val summary = comparison.get("summary")?.asString ?: ""

                    runOnUiThread {
                        displayComparisonResults(
                            productA?.get("name")?.asString ?: "Product A",
                            productB?.get("name")?.asString ?: "Product B",
                            productA?.get("brand")?.asString ?: "--",
                            productB?.get("brand")?.asString ?: "--",
                            productA?.get("score")?.asInt ?: 0,
                            productB?.get("score")?.asInt ?: 0,
                            productA?.get("risk_level")?.asString ?: "--",
                            productB?.get("risk_level")?.asString ?: "--",
                            when (recommendation) {
                                "A" -> "${productA?.get("name")?.asString} is the better choice"
                                "B" -> "${productB?.get("name")?.asString} is the better choice"
                                "EQUAL" -> "Both products have similar health impact"
                                else -> "Neither product is clearly better"
                            },
                            summary, recommendation
                        )
                    }
                }
            }
        }
    }

    /**
     * Resolve a scan ID for the given slot.
     * If scanId is already set (from history), return it immediately.
     * Otherwise, upload the image to /scan/analyze and return the new scan ID.
     */
    private fun resolveScanId(slot: String, callback: (Int?) -> Unit) {
        val existingId = if (slot == "A") scanIdA else scanIdB
        val uri = if (slot == "A") imageUriA else imageUriB

        if (existingId != null) {
            // Already have a scan ID (from history) — skip upload
            callback(existingId)
            return
        }

        if (uri == null) {
            callback(null)
            return
        }

        runOnUiThread { binding.tvLoadingSubtitle.text = "Scanning Product $slot ingredients..." }

        val file = uriToFile(uri)
        if (file == null) {
            callback(null)
            return
        }

        ApiClient.uploadImage(this, "/scan/analyze", file) { success, response ->
            if (!success || response == null) {
                callback(null)
                return@uploadImage
            }
            val scanId = response.getAsJsonObject("scan")?.get("id")?.asInt
            file.delete()
            callback(scanId)
        }
    }

    // ──────────────────────────────────────────────
    // Display results
    // ──────────────────────────────────────────────
    private fun displayComparisonResults(
        nameA: String, nameB: String, brandA: String, brandB: String,
        scoreA: Int, scoreB: Int, riskA: String, riskB: String,
        recommendationText: String, summary: String, recommendation: String
    ) {
        binding.llLoadingState.visibility = View.GONE
        binding.llComparisonResults.visibility = View.VISIBLE

        binding.tvRecommendationSummary.text = recommendationText

        binding.tvNameA.text = nameA; binding.tvNameB.text = nameB
        binding.tvScoreA.text = "${scoreA}/100"; binding.tvScoreB.text = "${scoreB}/100"
        binding.tvRiskA.text = riskA.lowercase().replaceFirstChar { it.uppercase() }
        binding.tvRiskB.text = riskB.lowercase().replaceFirstChar { it.uppercase() }
        binding.tvBrandA.text = if (brandA.isBlank() || brandA == "--") "--" else brandA
        binding.tvBrandB.text = if (brandB.isBlank() || brandB == "--") "--" else brandB
        binding.tvComparisonSummary.text = if (summary.isNotBlank()) summary else "No summary available."

        val isEqual = recommendation == "EQUAL" || recommendation == "NEITHER"
        val whiteBg = R.drawable.bg_pill_white_border
        val darkColor = Color.parseColor("#1E293B")

        if (isEqual) {
            // Equal/similar products: neutral styling — no highlight on either side
            binding.tvRecommendationTitle.text = "Similar Products"
            binding.llRecommendationCard.setBackgroundResource(R.drawable.bg_neutral_recommendation)
            binding.ivRecommendationIcon.setBackgroundResource(R.drawable.bg_icon_neutral_rounded)

            fun styleNeutral(tvA: TextView, tvB: TextView) {
                tvA.setBackgroundResource(whiteBg)
                tvA.setTextColor(darkColor)
                tvB.setBackgroundResource(whiteBg)
                tvB.setTextColor(darkColor)
            }
            styleNeutral(binding.tvScoreA, binding.tvScoreB)
            styleNeutral(binding.tvRiskA, binding.tvRiskB)
            styleNeutral(binding.tvNameA, binding.tvNameB)
            styleNeutral(binding.tvBrandA, binding.tvBrandB)
        } else {
            // Clear winner: highlight the better product in light blue
            val blueBg = R.drawable.bg_pill_blue_light
            val blueColor = Color.parseColor("#3B82F6")

            binding.llRecommendationCard.setBackgroundResource(R.drawable.bg_blue_recommendation)
            binding.ivRecommendationIcon.setBackgroundResource(R.drawable.bg_icon_blue_rounded)

            when (recommendation) {
                "A" -> binding.tvRecommendationTitle.text = "Choose $nameA"
                "B" -> binding.tvRecommendationTitle.text = "Choose $nameB"
            }

            val aIsBetter = recommendation == "A"
            fun styleHighlight(tvA: TextView, tvB: TextView) {
                tvA.setBackgroundResource(if (aIsBetter) blueBg else whiteBg)
                tvA.setTextColor(if (aIsBetter) blueColor else darkColor)
                tvB.setBackgroundResource(if (aIsBetter) whiteBg else blueBg)
                tvB.setTextColor(if (aIsBetter) darkColor else blueColor)
            }
            styleHighlight(binding.tvScoreA, binding.tvScoreB)
            styleHighlight(binding.tvRiskA, binding.tvRiskB)
            styleHighlight(binding.tvNameA, binding.tvNameB)
            styleHighlight(binding.tvBrandA, binding.tvBrandB)
        }

        binding.tvChoiceAText.text = nameA
        binding.tvChoiceBText.text = nameB
    }

    // ──────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────
    private fun handlePreferenceSelection(selectedA: Boolean) {
        if (selectedA) {
            binding.ivChoiceATick.visibility = View.VISIBLE; binding.tvChoiceACircle.visibility = View.INVISIBLE
            binding.tvChoiceAText.text = "Saved!"; binding.tvChoiceAText.setTextColor(Color.parseColor("#16B88A"))
            binding.ivChoiceBTick.visibility = View.GONE; binding.tvChoiceBCircle.visibility = View.VISIBLE
            binding.tvChoiceBText.text = "Product B"; binding.tvChoiceBText.setTextColor(Color.parseColor("#94A3B8"))
        } else {
            binding.ivChoiceBTick.visibility = View.VISIBLE; binding.tvChoiceBCircle.visibility = View.INVISIBLE
            binding.tvChoiceBText.text = "Saved!"; binding.tvChoiceBText.setTextColor(Color.parseColor("#16B88A"))
            binding.ivChoiceATick.visibility = View.GONE; binding.tvChoiceACircle.visibility = View.VISIBLE
            binding.tvChoiceAText.text = "Product A"; binding.tvChoiceAText.setTextColor(Color.parseColor("#94A3B8"))
        }

        // Delete the non-preferred scan from history
        val scanToDelete = if (selectedA) scanIdB else scanIdA
        if (scanToDelete != null) {
            ApiClient.deleteAuth(this, "/scan/$scanToDelete") { _, _ -> }
        }

        binding.root.postDelayed({
            startActivity(Intent(this, ScanHistoryActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP })
            finish()
        }, 1000)
    }

    private fun showImageDialog(imageUriString: String?) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dialogView = layoutInflater.inflate(R.layout.dialog_image_preview, null)
        if (imageUriString != null) dialogView.findViewById<ImageView>(R.id.ivFullImage).setImageURI(android.net.Uri.parse(imageUriString))
        dialogView.findViewById<ImageView>(R.id.ivCloseDialog).setOnClickListener { dialog.dismiss() }
        dialog.setContentView(dialogView); dialog.show()
    }

    private fun uriToFile(uriString: String): File? {
        return try {
            val uri = android.net.Uri.parse(uriString)
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "compare_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { output -> inputStream.copyTo(output) }
            inputStream.close()
            file
        } catch (e: Exception) {
            Log.e("CompareActivity", "Error: ${e.message}")
            null
        }
    }

    private fun showError(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun resetToPlaceholder() {
        binding.llLoadingState.visibility = View.GONE; binding.llComparisonResults.visibility = View.GONE; binding.llStartPlaceholder.visibility = View.VISIBLE
    }
}
