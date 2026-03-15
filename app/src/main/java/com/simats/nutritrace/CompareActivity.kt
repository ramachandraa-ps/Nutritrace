package com.simats.nutritrace

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityCompareBinding

class CompareActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompareBinding
    private var isProductAUploaded = false
    private var isProductBUploaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompareBinding.inflate(layoutInflater)
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

        // Setup bottom navigation clicks
        binding.navHome.setOnClickListener {
            // Navigate back to Home or start HomeActivity and clear top
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        binding.btnScanNav.setOnClickListener {
            startActivity(Intent(this, ScanIngredientsActivity::class.java))
        }
        
        binding.navHistory.setOnClickListener {
            val intent = Intent(this, ScanHistoryActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        binding.navUser.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
        }
        
        // Scan Launchers
        val scanAListener = android.view.View.OnClickListener {
            val intent = Intent(this, ScanIngredientsActivity::class.java)
            intent.putExtra("SCAN_SLOT", "A")
            startActivityForResult(intent, 2001)
        }
        binding.cvScanA.setOnClickListener(scanAListener)
        binding.cardProductA.setOnClickListener(scanAListener)

        val scanBListener = android.view.View.OnClickListener {
            val intent = Intent(this, ScanIngredientsActivity::class.java)
            intent.putExtra("SCAN_SLOT", "B")
            startActivityForResult(intent, 2001)
        }
        binding.cvScanB.setOnClickListener(scanBListener)
        binding.cardProductB.setOnClickListener(scanBListener)

        // Move showImageDialog to a class method

        // Removal Buttons
        binding.ivRemoveA.setOnClickListener {
            binding.ivProductAImage.visibility = android.view.View.GONE
            binding.ivRemoveA.visibility = android.view.View.GONE
            binding.ivScanIconA.visibility = android.view.View.VISIBLE
            binding.cardProductA.setBackgroundResource(R.drawable.bg_outline_card)
            isProductAUploaded = false
            updateComparisonVisibility()
        }

        binding.ivRemoveB.setOnClickListener {
            binding.ivProductBImage.visibility = android.view.View.GONE
            binding.ivRemoveB.visibility = android.view.View.GONE
            binding.ivScanIconB.visibility = android.view.View.VISIBLE
            binding.cardProductB.setBackgroundResource(R.drawable.bg_outline_card)
            isProductBUploaded = false
            updateComparisonVisibility()
        }

        binding.llChoiceA.setOnClickListener {
            handlePreferenceSelection(selectedA = true)
        }

        binding.llChoiceB.setOnClickListener {
            handlePreferenceSelection(selectedA = false)
        }
    }

    private fun handlePreferenceSelection(selectedA: Boolean) {
        if (selectedA) {
            binding.ivChoiceATick.visibility = android.view.View.VISIBLE
            binding.tvChoiceACircle.visibility = android.view.View.INVISIBLE
            binding.tvChoiceAText.text = "Saved!"
            binding.tvChoiceAText.setTextColor(android.graphics.Color.parseColor("#16B88A"))

            binding.ivChoiceBTick.visibility = android.view.View.GONE
            binding.tvChoiceBCircle.visibility = android.view.View.VISIBLE
            binding.tvChoiceBText.text = "Product B"
            binding.tvChoiceBText.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
        } else {
            binding.ivChoiceBTick.visibility = android.view.View.VISIBLE
            binding.tvChoiceBCircle.visibility = android.view.View.INVISIBLE
            binding.tvChoiceBText.text = "Saved!"
            binding.tvChoiceBText.setTextColor(android.graphics.Color.parseColor("#16B88A"))

            binding.ivChoiceATick.visibility = android.view.View.GONE
            binding.tvChoiceACircle.visibility = android.view.View.VISIBLE
            binding.tvChoiceAText.text = "Product A"
            binding.tvChoiceAText.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
        }

        // Navigate to History page after a short delay
        binding.root.postDelayed({
            val intent = Intent(this, ScanHistoryActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }, 1000)
    }

    private fun showImageDialog(imageUriString: String?) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dialogView = layoutInflater.inflate(R.layout.dialog_image_preview, null)
        
        val ivClose = dialogView.findViewById<android.widget.ImageView>(R.id.ivCloseDialog)
        val ivPreview = dialogView.findViewById<android.widget.ImageView>(R.id.ivFullImage)

        if (imageUriString != null) {
            ivPreview.setImageURI(android.net.Uri.parse(imageUriString))
        } else {
            ivPreview.setImageResource(R.drawable.bg_splash_logo_square)
        }

        ivClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(dialogView)
        dialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == RESULT_OK) {
            val slot = data?.getStringExtra("SCAN_SLOT")
            val imageUriString = data?.getStringExtra("IMAGE_URI")
            if (slot == "A") {
                binding.ivProductAImage.visibility = android.view.View.VISIBLE
                if (imageUriString != null) {
                    binding.ivProductAImage.setImageURI(android.net.Uri.parse(imageUriString))
                    binding.ivProductAImage.setOnClickListener { showImageDialog(imageUriString) }
                }
                binding.ivRemoveA.visibility = android.view.View.VISIBLE
                binding.ivScanIconA.visibility = android.view.View.GONE
                binding.cardProductA.setBackgroundResource(R.drawable.bg_outline_card_highlight)
                
                // Clear the parent card generic listener so tapping the image doesn't just re-open the camera
                binding.cardProductA.setOnClickListener(null)
                binding.cvScanA.setOnClickListener(null)

                isProductAUploaded = true
                updateComparisonVisibility()
            } else if (slot == "B") {
                binding.ivProductBImage.visibility = android.view.View.VISIBLE
                if (imageUriString != null) {
                    binding.ivProductBImage.setImageURI(android.net.Uri.parse(imageUriString))
                    binding.ivProductBImage.setOnClickListener { showImageDialog(imageUriString) }
                }
                binding.ivRemoveB.visibility = android.view.View.VISIBLE
                binding.ivScanIconB.visibility = android.view.View.GONE
                binding.cardProductB.setBackgroundResource(R.drawable.bg_outline_card_highlight)
                
                // Clear the parent card generic listener so tapping the image doesn't just re-open the camera
                binding.cardProductB.setOnClickListener(null)
                binding.cvScanB.setOnClickListener(null)

                isProductBUploaded = true
                updateComparisonVisibility()
            }
        }
    }

    private fun updateComparisonVisibility() {
        if (isProductAUploaded && isProductBUploaded) {
            binding.llStartPlaceholder.visibility = android.view.View.GONE
            binding.llComparisonResults.visibility = android.view.View.VISIBLE
            // Reset selection state when new comparisons happen
            binding.ivChoiceATick.visibility = android.view.View.GONE
            binding.tvChoiceACircle.visibility = android.view.View.VISIBLE
            binding.tvChoiceAText.text = "Product A"
            binding.tvChoiceAText.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            binding.ivChoiceBTick.visibility = android.view.View.GONE
            binding.tvChoiceBCircle.visibility = android.view.View.VISIBLE
            binding.tvChoiceBText.text = "Product B"
            binding.tvChoiceBText.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
        } else {
            binding.llStartPlaceholder.visibility = android.view.View.VISIBLE
            binding.llComparisonResults.visibility = android.view.View.GONE
        }
    }
}
