package com.simats.nutritrace

data class ScanData(
    val id: Int = 0,
    val productName: String,
    val brandName: String,
    val score: Int,
    val riskLevel: String,
    val time: String,
    val imagePath: String = ""
)
