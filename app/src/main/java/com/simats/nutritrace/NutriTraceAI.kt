package com.simats.nutritrace

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Local OCR + Gemini AI analysis for demo/review purposes.
 * Bypasses the broken backend OCR (Cloud Vision) and AI (compromised Gemini key) by:
 * - Using Google ML Kit for on-device text recognition
 * - Calling Gemini REST API directly with a fresh API key
 *
 * Backend endpoints that still work (auth, profile, history) remain unchanged.
 */
object NutriTraceAI {

    private const val TAG = "NutriTraceAI"

    // ⚠️ DEMO ONLY — Replace with your new Gemini API key from https://aistudio.google.com/apikey
    private const val GEMINI_API_KEY = "AIzaSyCEu2gU62M8F7Lq8g88padgDEgmOzizZkE"

    private const val GEMINI_MODEL = "gemini-2.5-flash"
    private const val GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    // ─────────────────────────────────────────────
    // OCR via ML Kit (on-device, free, no API key)
    // ─────────────────────────────────────────────
    fun extractText(context: Context, imageUri: Uri, callback: (String?) -> Unit) {
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val text = result.text
                    Log.d(TAG, "OCR extracted ${text.length} chars")
                    callback(if (text.isBlank()) null else text)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR failed: ${e.message}")
                    callback(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "OCR error: ${e.message}")
            callback(null)
        }
    }

    // ─────────────────────────────────────────────
    // Gemini REST API call (uses existing OkHttp)
    // ─────────────────────────────────────────────
    private fun callGemini(prompt: String, callback: (String?) -> Unit) {
        val parts = JsonArray().apply {
            add(JsonObject().apply { addProperty("text", prompt) })
        }
        val content = JsonObject().apply { add("parts", parts) }
        val contents = JsonArray().apply { add(content) }

        val requestBody = JsonObject().apply {
            add("contents", contents)
            add("generationConfig", JsonObject().apply {
                addProperty("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url("$GEMINI_URL?key=$GEMINI_API_KEY")
            .post(requestBody.toString().toRequestBody(JSON_TYPE))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Gemini call failed: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string()
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Gemini error ${response.code}: $body")
                        callback(null)
                        return
                    }
                    val result = gson.fromJson(body, JsonObject::class.java)
                    val text = result?.getAsJsonArray("candidates")
                        ?.get(0)?.asJsonObject
                        ?.getAsJsonObject("content")
                        ?.getAsJsonArray("parts")
                        ?.get(0)?.asJsonObject
                        ?.get("text")?.asString
                    callback(text)
                } catch (e: Exception) {
                    Log.e(TAG, "Gemini parse error: ${e.message}")
                    callback(null)
                }
            }
        })
    }

    // ─────────────────────────────────────────────
    // Detect product name from OCR text
    // ─────────────────────────────────────────────
    private fun detectProductName(ocrText: String, callback: (String, String) -> Unit) {
        val prompt = """From the following text extracted from a food product label, identify the product name and brand name.
If you cannot find a clear product name, infer the most likely product type from the ingredients (e.g., "Instant Noodles", "Chocolate Cookies", "Potato Chips").
If you cannot find a brand name, return empty string.

TEXT:
$ocrText

Respond ONLY with valid JSON:
{"product_name": "...", "brand_name": "..."}"""

        callGemini(prompt) { response ->
            try {
                val json = gson.fromJson(response, JsonObject::class.java)
                val productName = json?.get("product_name")?.asString ?: "Unknown Product"
                val brandName = json?.get("brand_name")?.asString ?: ""
                callback(productName, brandName)
            } catch (e: Exception) {
                callback("Unknown Product", "")
            }
        }
    }

    // ─────────────────────────────────────────────
    // Analyze ingredients (same prompt as backend ai_service.py)
    // ─────────────────────────────────────────────
    private fun analyzeIngredients(
        ocrText: String, ageGroup: String,
        conditions: List<String>, sensitivities: List<String>,
        callback: (JsonObject?) -> Unit
    ) {
        val conditionsStr = if (conditions.isNotEmpty()) conditions.joinToString(", ") else "None specified"
        val sensitivitiesStr = if (sensitivities.isNotEmpty()) sensitivities.joinToString(", ") else "None specified"

        val prompt = """You are a food safety and nutrition expert AI. Analyze the following ingredients
for a user with the given health profile.

USER HEALTH PROFILE:
- Age Group: $ageGroup
- Health Conditions: $conditionsStr
- Food Sensitivities/Allergies: $sensitivitiesStr

INGREDIENTS (extracted from product label):
$ocrText

IMPORTANT LANGUAGE GUIDELINES:
- Use cautious, non-definitive language throughout ALL text fields (reason, overview, guidance, risk_analysis).
- Use hedging words like "may", "could", "can", "might", "is generally associated with", "some studies suggest", "is often linked to" instead of making direct claims.
- NEVER use words like "directly causes", "will harm", "is harmful", "is detrimental", "is dangerous", "negatively impacts", "worsens" as definitive statements.
- Instead say "may contribute to", "could potentially affect", "is often considered", "may not be ideal for", "could be a concern for".
- This applies to overview, ingredient reasons, guidance tips, and risk analysis descriptions.
- The goal is to inform users about potential concerns without making absolute health claims about any product or ingredient.

For each ingredient, provide:
1. ingredient_name: The name of the ingredient
2. status: SAFE, CAUTION, or AVOID
   - SAFE: No known health concerns for this user's profile
   - CAUTION: May have mild effects or could be worth consuming in moderation
   - AVOID: Could potentially aggravate known conditions/allergies for this user
3. reason: A short, clear explanation using cautious language (e.g. "may", "could", "can"),
   referencing the user's conditions if relevant, without making absolute health claims

Also provide:
- overall_score: 0-100 (100 = perfectly safe, 0 = highly concerning)
- risk_level: LOW (score >= 70), MODERATE (40-69), HIGH (< 40)
- overview: A 2-3 sentence summary of the product's suitability for this user, using cautious language
- guidance: 3-5 actionable recommendations using suggestive language (e.g. "consider", "you may want to")
- sugar_estimate: estimated sugar content as a string like "12g" or "High" or "Low" (infer from ingredients)
- additives_count: number of artificial additives, preservatives, or E-numbers found
- allergens_found: list of allergens relevant to this user's sensitivities detected in ingredients
- risk_analysis: array of 3-4 risk category objects with title and detailed description analyzing the product from different angles (e.g. Sugar Analysis, Processing Level, Additive Load, Allergen Risk, Nutritional Value)

Respond ONLY with valid JSON in this exact format:
{
    "overall_score": 65,
    "risk_level": "MODERATE",
    "overview": "...",
    "sugar_estimate": "12g",
    "additives_count": 3,
    "allergens_found": ["Dairy", "Gluten"],
    "ingredients": [
        {"ingredient_name": "...", "status": "SAFE", "reason": "..."},
        {"ingredient_name": "...", "status": "CAUTION", "reason": "..."}
    ],
    "risk_analysis": [
        {"title": "Sugar Analysis", "description": "..."},
        {"title": "Processing Level", "description": "..."},
        {"title": "Additive Load", "description": "..."}
    ],
    "guidance": ["...", "..."]
}"""

        callGemini(prompt) { response ->
            try {
                var text = response?.trim() ?: ""
                if (text.startsWith("```")) {
                    text = text.substringAfter("\n")
                    if (text.endsWith("```")) text = text.dropLast(3)
                    text = text.trim()
                }
                callback(gson.fromJson(text, JsonObject::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse analysis: ${e.message}")
                callback(null)
            }
        }
    }

    // ─────────────────────────────────────────────
    // Compare two products (same prompt as backend ai_service.py)
    // ─────────────────────────────────────────────
    fun compareProducts(
        productAName: String, productAIngredients: String,
        productBName: String, productBIngredients: String,
        ageGroup: String, conditions: List<String>, sensitivities: List<String>,
        callback: (JsonObject?) -> Unit
    ) {
        val conditionsStr = if (conditions.isNotEmpty()) conditions.joinToString(", ") else "None specified"
        val sensitivitiesStr = if (sensitivities.isNotEmpty()) sensitivities.joinToString(", ") else "None specified"

        val prompt = """You are a food safety and nutrition expert AI. Compare these two food products
for the user with the given health profile. Be precise and fair in your analysis.

USER HEALTH PROFILE:
- Age Group: $ageGroup
- Health Conditions: $conditionsStr
- Food Sensitivities/Allergies: $sensitivitiesStr

PRODUCT A: $productAName
Ingredients: $productAIngredients

PRODUCT B: $productBName
Ingredients: $productBIngredients

IMPORTANT GUIDELINES:
- Only recommend "A" or "B" if there is a CLEAR and MEANINGFUL difference in health impact.
- If both products have similar ingredients, similar processing levels, and similar health impacts, you MUST return "NEITHER".
- Do NOT favor one product just because of brand perception or minor cosmetic differences.
- Focus on actual ingredient differences that matter for the user's health conditions.
- Be honest: if both products are equally good or equally bad, say so.
- Use cautious, non-definitive language throughout (e.g. "may", "could", "can", "might", "is generally associated with").
- NEVER make absolute health claims like "directly causes", "is harmful", "will damage". Instead use "may contribute to", "could potentially affect", "is often considered".

Provide:
1. recommendation: "A", "B", or "NEITHER" (if both are similar in health impact)
2. summary: 2-3 sentence explanation using cautious language. If NEITHER, explain why both are similar.
3. detailed_comparison: Key differences relevant to user's health
4. warnings: Any potential concerns for either product (use cautious language)

Respond ONLY with valid JSON in this exact format:
{
    "recommendation": "A",
    "summary": "...",
    "detailed_comparison": {
        "ingredients_of_concern": "...",
        "overall": "..."
    },
    "warnings": ["..."]
}"""

        callGemini(prompt) { response ->
            try {
                var text = response?.trim() ?: ""
                if (text.startsWith("```")) {
                    text = text.substringAfter("\n")
                    if (text.endsWith("```")) text = text.dropLast(3)
                    text = text.trim()
                }
                callback(gson.fromJson(text, JsonObject::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse comparison: ${e.message}")
                callback(null)
            }
        }
    }

    // ─────────────────────────────────────────────
    // Fetch health profile from backend (this endpoint works)
    // ─────────────────────────────────────────────
    fun fetchHealthProfile(context: Context, callback: (String, List<String>, List<String>) -> Unit) {
        ApiClient.getAuth(context, "/user/profile") { success, json ->
            val healthProfile = json?.getAsJsonObject("health_profile")
            val ageGroup = healthProfile?.get("age_group")?.asString ?: "Adult"
            val conditions = mutableListOf<String>()
            val sensitivities = mutableListOf<String>()
            healthProfile?.getAsJsonArray("conditions")?.forEach { conditions.add(it.asString) }
            healthProfile?.getAsJsonArray("sensitivities")?.forEach { sensitivities.add(it.asString) }
            callback(ageGroup, conditions, sensitivities)
        }
    }

    // ─────────────────────────────────────────────
    // Full analysis flow: OCR → Health Profile → Product Name → AI Analysis
    // Returns JSON matching backend /scan/analyze response format exactly
    // ─────────────────────────────────────────────
    fun analyzeImage(context: Context, imageUri: Uri, callback: (Boolean, JsonObject?) -> Unit) {
        // Step 1: OCR via ML Kit
        extractText(context, imageUri) { ocrText ->
            if (ocrText.isNullOrBlank()) {
                callback(false, errorJson("Could not extract text from image. Try a clearer photo."))
                return@extractText
            }

            // Step 2: Fetch health profile from backend (working endpoint)
            fetchHealthProfile(context) { ageGroup, conditions, sensitivities ->

                // Step 3: Detect product name via Gemini
                detectProductName(ocrText) { productName, brandName ->

                    // Step 4: Analyze ingredients via Gemini
                    analyzeIngredients(ocrText, ageGroup, conditions, sensitivities) { analysis ->
                        if (analysis == null) {
                            callback(false, errorJson("AI analysis failed. Please try again."))
                            return@analyzeIngredients
                        }

                        // Step 5: Build response matching backend /scan/analyze format
                        val ingredients = analysis.getAsJsonArray("ingredients") ?: JsonArray()
                        val riskBreakdown = JsonObject().apply {
                            var avoid = 0; var caution = 0; var safe = 0
                            for (i in 0 until ingredients.size()) {
                                when (ingredients[i].asJsonObject.get("status")?.asString) {
                                    "AVOID" -> avoid++
                                    "CAUTION" -> caution++
                                    "SAFE" -> safe++
                                }
                            }
                            addProperty("avoid_count", avoid)
                            addProperty("caution_count", caution)
                            addProperty("safe_count", safe)
                        }

                        val scanObj = JsonObject().apply {
                            addProperty("id", 0) // No backend ID for local scans
                            addProperty("product_name", productName)
                            addProperty("brand_name", brandName)
                            addProperty("score", analysis.get("overall_score")?.asInt ?: 0)
                            addProperty("risk_level", analysis.get("risk_level")?.asString ?: "MODERATE")
                            addProperty("scanned_at", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                            addProperty("overview", analysis.get("overview")?.asString ?: "")
                            addProperty("sugar_estimate", analysis.get("sugar_estimate")?.asString ?: "--")
                            addProperty("additives_count", analysis.get("additives_count")?.asInt ?: 0)
                            add("allergens_found", analysis.getAsJsonArray("allergens_found") ?: JsonArray())
                            add("ingredients", ingredients)
                            add("risk_breakdown", riskBreakdown)
                            add("guidance", analysis.getAsJsonArray("guidance") ?: JsonArray())
                            addProperty("raw_ocr_text", ocrText)
                        }

                        val responseObj = JsonObject().apply {
                            addProperty("success", true)
                            add("scan", scanObj)
                        }

                        callback(true, responseObj)
                    }
                }
            }
        }
    }

    private fun errorJson(message: String): JsonObject {
        return JsonObject().apply {
            addProperty("success", false)
            addProperty("message", message)
        }
    }
}
