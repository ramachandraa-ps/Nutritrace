package com.simats.nutritrace

data class ScanData(
    val productName: String,
    val brandName: String,
    val score: Int,
    val riskLevel: String, // "LOW", "MODERATE", "HIGH"
    val time: String
)
