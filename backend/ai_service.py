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
for the user with the given health profile. Be precise and fair in your analysis.

USER HEALTH PROFILE:
- Age Group: {age_group}
- Health Conditions: {', '.join(conditions) if conditions else 'None specified'}
- Food Sensitivities/Allergies: {', '.join(sensitivities) if sensitivities else 'None specified'}

PRODUCT A: {product_a_name}
Ingredients: {product_a_ingredients}

PRODUCT B: {product_b_name}
Ingredients: {product_b_ingredients}

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
{{
    "recommendation": "A",
    "summary": "...",
    "detailed_comparison": {{
        "ingredients_of_concern": "...",
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
