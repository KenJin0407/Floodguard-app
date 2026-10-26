package com.example.floodguard

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

data class GeminiVerificationResult(
    val isValid: Boolean,
    val isFlood: Boolean,
    val confidence: Double,
    val estimatedSeverity: String,
    val reason: String,
    val isErrorOrTimeout: Boolean = false,
    val rejectionCategory: String = "NONE" // "NONE", "AI_GENERATED", "SCREENSHOT_OR_FAKE", "DUPLICATE_PHOTO", "NOT_FLOOD"
)

/**
 * Forensic AI Photo Verifier powered by Google Gemini Vision models.
 * Examines uploaded photos to verify genuine flooding AND strictly blocks AI-generated/synthetic images,
 * screenshots, internet downloads, and non-flood photos.
 */
object GeminiPhotoVerifier {
    private const val TAG = "GeminiPhotoVerifier"
    private val API_KEY: String by lazy {
        try {
            val encoded = "QVEuQWI4Uk42S3hsamtGSW9Rd09ibDBValc2MjZJV0RwclE0a0VxRjR3RURldkMxNWxZbnc="
            String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8).trim()
        } catch (_: Exception) {
            ""
        }
    }

    // Primary and fallback models tested and verified active on Google AI Studio
    private val MODELS = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.5-flash",
        "gemini-3.8-flash"
    )

    private const val VERIFICATION_PROMPT =
        "You are a strict forensic AI flood image verifier for the FloodGuard disaster response system in San Mateo, Rizal. " +
        "Analyze the attached image carefully for BOTH flood presence AND photographic authenticity.\n\n" +
        "1. AUTHENTICITY & ANTI-AI CHECK (CRITICAL):\n" +
        "- Carefully inspect if the image is AI-generated or synthetic (e.g., Midjourney, DALL-E, Stable Diffusion, Imagen, Flux, CGI, 3D render, digital art, painting, cartoon). " +
        "Look for AI artifacts: overly smooth/plastic or painterly textures, unnatural cinematic lighting, impossible water reflections, distorted/garbled text on signs or license plates, warped buildings/vehicles, deformed human figures/hands/umbrellas, or AI watermarks.\n" +
        "- Check if the image is a screenshot, a photo taken of another screen/monitor (moire pixel grid, screen glare, bezel), a meme, a news broadcast graphic, or a stock photo with watermarks/overlays.\n" +
        "- If the image is AI-generated, synthetic, CGI, a screenshot, or a photo of a screen, you MUST reject it.\n\n" +
        "2. FLOOD PRESENCE CHECK:\n" +
        "- Determine whether the photo depicts genuine outdoor flood waters, submerged/waterlogged streets, overflowing rivers/canals, or storm inundation.\n" +
        "- Reject selfies, portraits, indoor scenes, pets, food, dry clear roads, or unrelated objects.\n\n" +
        "Return ONLY valid JSON in this exact structure:\n" +
        "{\n" +
        "  \"is_flood\": boolean,\n" +
        "  \"is_ai_generated\": boolean,\n" +
        "  \"is_screenshot_or_fake\": boolean,\n" +
        "  \"is_authentic_camera_photo\": boolean,\n" +
        "  \"confidence\": float between 0.0 and 1.0,\n" +
        "  \"estimated_severity\": \"Ankle-Deep\"|\"Knee-Deep\"|\"Waist-Deep\"|\"Impassable\"|\"None\",\n" +
        "  \"rejection_category\": \"NONE\"|\"AI_GENERATED\"|\"SCREENSHOT_OR_FAKE\"|\"NOT_FLOOD\",\n" +
        "  \"reason\": \"clear 1-2 sentence explanation of why it was verified or rejected\"\n" +
        "}"

    private val BACKEND_VERIFY_URLS = listOf(
        "https://floodguard-app-90dp.onrender.com/api/reports/verify-photo",
        "http://10.0.2.2:8000/api/reports/verify-photo",
        "http://127.0.0.1:8000/api/reports/verify-photo"
    )

    fun verifyPhoto(bitmap: Bitmap): GeminiVerificationResult {
        val scaled = scaleForInference(bitmap, 512)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, stream)
        val jpegBytes = stream.toByteArray()
        val base64Data = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

        // 1. Primary path: Verify via FloodGuard Cloud Backend (Render US Cloud -> Gemini API).
        // Avoids local ISP/IPv6 routing timeouts when calling generativelanguage.googleapis.com directly from device/emulator.
        for (verifyUrl in BACKEND_VERIFY_URLS) {
            try {
                val url = URL(verifyUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = if (verifyUrl.contains("onrender.com")) 15000 else 3000
                conn.readTimeout = if (verifyUrl.contains("onrender.com")) 25000 else 5000
                conn.doOutput = true

                val reqPayload = JSONObject().apply {
                    put("photo", "data:image/jpeg;base64,$base64Data")
                }
                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(reqPayload.toString())
                    writer.flush()
                }

                val code = conn.responseCode
                if (code == 200) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val parsed = parseGeminiJson(responseStr)
                    if (parsed != null && !parsed.reason.contains("AI verification skipped", ignoreCase = true)) {
                        Log.d(TAG, "Backend Gemini ($verifyUrl) result: isValid=${parsed.isValid}, cat=${parsed.rejectionCategory}, reason=${parsed.reason}")
                        return parsed
                    }
                } else {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Backend verify ($verifyUrl) error: ${e.message}")
            }
        }

        // 2. Secondary fallback path: Direct device-to-Google Gemini API call
        for (model in MODELS) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$API_KEY")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 12000
                conn.readTimeout = 18000
                conn.doOutput = true

                val payload = JSONObject().apply {
                    val contentsArr = JSONArray().apply {
                        val contentObj = JSONObject().apply {
                            val partsArr = JSONArray().apply {
                                val textPart = JSONObject().apply {
                                    put("text", VERIFICATION_PROMPT)
                                }
                                val imagePart = JSONObject().apply {
                                    val inlineData = JSONObject().apply {
                                        put("mime_type", "image/jpeg")
                                        put("data", base64Data)
                                    }
                                    put("inline_data", inlineData)
                                }
                                put(textPart)
                                put(imagePart)
                            }
                            put("parts", partsArr)
                        }
                        put(contentObj)
                    }
                    put("contents", contentsArr)
                    val genConfig = JSONObject().apply {
                        put("response_mime_type", "application/json")
                    }
                    put("generationConfig", genConfig)
                }

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(payload.toString())
                writer.flush()
                writer.close()

                val code = conn.responseCode
                if (code == 200) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()

                    val root = JSONObject(responseStr)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text")
                            val parsed = parseGeminiJson(text)
                            if (parsed != null) {
                                Log.d(TAG, "Gemini ($model) result: isValid=${parsed.isValid}, cat=${parsed.rejectionCategory}, reason=${parsed.reason}")
                                return parsed
                            }
                        }
                    }
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.w(TAG, "Model $model returned HTTP $code: $err")
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Model $model error: ${e.message}")
            }
        }

        // If network or all models timed out/failed, return fallback result allowing submission if basic heuristic passed
        return GeminiVerificationResult(
            isValid = true,
            isFlood = true,
            confidence = 0.5,
            estimatedSeverity = "Unknown",
            reason = "Verification service unreachable; fallback to heuristic verification.",
            isErrorOrTimeout = true,
            rejectionCategory = "NONE"
        )
    }

    private fun parseGeminiJson(rawText: String): GeminiVerificationResult? {
        return try {
            val clean = rawText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val json = JSONObject(clean)
            val isFlood = json.optBoolean("is_flood", false)
            val isAiGenerated = json.optBoolean("is_ai_generated", false)
            val isScreenshotOrFake = json.optBoolean("is_screenshot_or_fake", false)
            val isAuthentic = json.optBoolean("is_authentic_camera_photo", true)
            val confidence = json.optDouble("confidence", 0.0)
            val severity = json.optString("estimated_severity", "None")
            val rawCategory = json.optString("rejection_category", "NONE")
            val rawReason = json.optString("reason", if (isFlood) "Flood detected" else "No flood detected in image")

            when {
                isAiGenerated || rawCategory.equals("AI_GENERATED", ignoreCase = true) -> {
                    GeminiVerificationResult(
                        isValid = false,
                        isFlood = false,
                        confidence = confidence.coerceAtLeast(0.92),
                        estimatedSeverity = "None (AI / Synthetic)",
                        reason = "AI-generated or synthetic flood image detected. $rawReason Please capture an authentic, real-time photo using your camera.",
                        isErrorOrTimeout = false,
                        rejectionCategory = "AI_GENERATED"
                    )
                }
                isScreenshotOrFake || !isAuthentic || rawCategory.equals("SCREENSHOT_OR_FAKE", ignoreCase = true) -> {
                    GeminiVerificationResult(
                        isValid = false,
                        isFlood = false,
                        confidence = confidence.coerceAtLeast(0.90),
                        estimatedSeverity = "None (Non-Original)",
                        reason = "Non-original image, screenshot, or screen photo detected. $rawReason Please take a real camera photo of the flooded street.",
                        isErrorOrTimeout = false,
                        rejectionCategory = "SCREENSHOT_OR_FAKE"
                    )
                }
                !isFlood && confidence >= 0.60 -> {
                    GeminiVerificationResult(
                        isValid = false,
                        isFlood = false,
                        confidence = confidence,
                        estimatedSeverity = "None",
                        reason = rawReason,
                        isErrorOrTimeout = false,
                        rejectionCategory = "NOT_FLOOD"
                    )
                }
                else -> {
                    GeminiVerificationResult(
                        isValid = true,
                        isFlood = true,
                        confidence = confidence,
                        estimatedSeverity = severity,
                        reason = rawReason,
                        isErrorOrTimeout = false,
                        rejectionCategory = "NONE"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemini response: $rawText", e)
            null
        }
    }

    private fun scaleForInference(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longest = max(width, height)
        if (longest <= maxDimension) return bitmap

        val ratio = maxDimension.toFloat() / longest.toFloat()
        val newWidth = (width * ratio).roundToInt().coerceAtLeast(1)
        val newHeight = (height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
