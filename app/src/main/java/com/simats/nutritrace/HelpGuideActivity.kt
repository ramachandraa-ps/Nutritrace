package com.simats.nutritrace

import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.simats.nutritrace.databinding.ActivityHelpGuideBinding

class HelpGuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpGuideBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        setupExpandableItem(
            header = binding.llScanHeader,
            content = binding.llScanContent,
            icon = binding.ivScanChevron
        )

        setupExpandableItem(
            header = binding.llRiskHeader,
            content = binding.llRiskContent,
            icon = binding.ivRiskChevron
        )

        setupExpandableItem(
            header = binding.llCompareHeader,
            content = binding.llCompareContent,
            icon = binding.ivCompareChevron
        )

        setupExpandableItem(
            header = binding.llIngredientsHeader,
            content = binding.llIngredientsContent,
            icon = binding.ivIngredientsChevron
        )
        
        setupExpandableItem(
            header = binding.llPrivacyHeader,
            content = binding.llPrivacyContent,
            icon = binding.ivPrivacyChevron
        )

        setupExpandableItem(
            header = binding.llDeleteHeader,
            content = binding.llDeleteContent,
            icon = binding.ivDeleteChevron
        )
    }

    private fun setupExpandableItem(header: LinearLayout, content: LinearLayout, icon: android.widget.ImageView) {
        header.setOnClickListener {
            val isCurrentlyExpanded = content.visibility == View.VISIBLE
            
            TransitionManager.beginDelayedTransition(binding.clRoot, AutoTransition())
            
            if (isCurrentlyExpanded) {
                content.visibility = View.GONE
                icon.setImageResource(R.drawable.ic_chevron_down)
            } else {
                content.visibility = View.VISIBLE
                icon.setImageResource(R.drawable.ic_chevron_up)
            }
        }
    }
}
