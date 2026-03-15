import json
from google import genai
from config import GEMINI_API_KEY

client = genai.Client(api_key=GEMINI_API_KEY)


def detect_product_name(raw_text):
    """Try to detect the product name from the OCR text."""
    try:
        response = client.models.generate_content(
            model="gemini-2.5-flash",
            contents=f"""From the following text extracted from a food product label, identify the product name and brand name.
If you cannot find a clear product name, infer the most likely product type from the ingredients (e.g., "Instant Noodles", "Chocolate Cookies", "Potato Chips").
If you cannot find a brand name, return empty string.

TEXT:
{raw_text}

Respond ONLY with valid JSON:
{{"product_name": "...", "brand_name": "..."}}""",
            config={"response_mime_type": "application/json"},
        )
        result = json.loads(response.text.strip())
        return result.get("product_name", ""), result.get("brand_name", "")
    except Exception:
        return "", ""


def analyze_ingredients(raw_text, age_group, conditions, sensitivities):
    """Analyze ingredients using Gemini AI based on user's health profile."""

    prompt = f"""You are a food safety and nutrition expert AI. Analyze the following ingredients
for a user with the given health profile.

USER HEALTH PROFILE:
- Age Group: {age_group}
- Health Conditions: {', '.join(conditions) if conditions else 'None specified'}
- Food Sensitivities/Allergies: {', '.join(sensitivities) if sensitivities else 'None specified'}

INGREDIENTS (extracted from product label):
{raw_text}

For each ingredient, provide:
1. ingredient_name: The name of the ingredient
2. status: SAFE, CAUTION, or AVOID
   - SAFE: No known health concerns for this user's profile
   - CAUTION: May have mild effects or should be consumed in moderation
   - AVOID: Directly harmful or triggers known conditions/allergies for this user
3. reason: A short, clear explanation of why this status was assigned,
   specifically referencing the user's conditions if relevant

Also provide:
- overall_score: 0-100 (100 = perfectly safe, 0 = extremely harmful)
- risk_level: LOW (score >= 70), MODERATE (40-69), HIGH (< 40)
- overview: A 2-3 sentence summary of the product's safety for this user
- guidance: 3-5 actionable recommendations
- sugar_estimate: estimated sugar content as a string like "12g" or "High" or "Low" (infer from ingredients)
- additives_count: number of artificial additives, preservatives, or E-numbers found
- allergens_found: list of allergens relevant to this user's sensitivities detected in ingredients
- risk_analysis: array of 3-4 risk category objects with title and detailed description analyzing the product from different angles (e.g. Sugar Analysis, Processing Level, Additive Load, Allergen Risk, Nutritional Value)

Respond ONLY with valid JSON in this exact format:
{{
    "overall_score": 65,
    "risk_level": "MODERATE",
    "overview": "...",
    "sugar_estimate": "12g",
    "additives_count": 3,
    "allergens_found": ["Dairy", "Gluten"],
    "ingredients": [
        {{"ingredient_name": "...", "status": "SAFE", "reason": "..."}},
        {{"ingredient_name": "...", "status": "CAUTION", "reason": "..."}}
    ],
    "risk_analysis": [
        {{"title": "Sugar Analysis", "description": "..."}},
        {{"title": "Processing Level", "description": "..."}},
        {{"title": "Additive Load", "description": "..."}}
    ],
    "guidance": ["...", "..."]
}}"""

    response = client.models.generate_content(
        model="gemini-2.5-flash",
        contents=prompt,
        config={
            "response_mime_type": "application/json",
        },
    )
    text = response.text.strip()

    # Strip markdown code fences if present
    if text.startswith("```"):
        text = text.split("\n", 1)[1]
        if text.endswith("```"):
            text = text[:-3]
        text = text.strip()

    return json.loads(text)


def compare_products(product_a_name, product_a_ingredients, product_b_name, product_b_ingredients, age_group, conditions, sensitivities):
    """Compare two products using Gemini AI."""

    prompt = f"""You are a food safety and nutrition expert AI. Compare these two food products
for the user with the given health profile.

USER HEALTH PROFILE:
- Age Group: {age_group}
- Health Conditions: {', '.join(conditions) if conditions else 'None specified'}
- Food Sensitivities/Allergies: {', '.join(sensitivities) if sensitivities else 'None specified'}

PRODUCT A: {product_a_name}
Ingredients: {product_a_ingredients}

PRODUCT B: {product_b_name}
Ingredients: {product_b_ingredients}

Provide:
1. recommendation: "A", "B", or "NEITHER" (if both are equally bad)
2. summary: 2-3 sentence explanation of which is better and why
3. detailed_comparison: Key differences relevant to user's health
4. warnings: Any critical warnings for either product

Respond ONLY with valid JSON in this exact format:
{{
    "recommendation": "A",
    "summary": "...",
    "detailed_comparison": {{
        "harmful_ingredients": "...",
        "overall": "..."
    }},
    "warnings": ["..."]
}}"""

    response = client.models.generate_content(
        model="gemini-2.5-flash",
        contents=prompt,
        config={
            "response_mime_type": "application/json",
        },
    )
    text = response.text.strip()

    if text.startswith("```"):
        text = text.split("\n", 1)[1]
        if text.endswith("```"):
            text = text[:-3]
        text = text.strip()

    return json.loads(text)
