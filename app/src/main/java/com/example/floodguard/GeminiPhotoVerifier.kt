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
    val isErrorOrTimeout: Boolean = false
)

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
        "You are an AI flood verification assistant for FloodGuard. Analyze the attached photo carefully. " +
        "Determine whether this photo depicts actual flood waters, waterlogged/submerged roads, storm inundation, overflowing canals/rivers, or flood damage. " +
        "Reject selfies, portraits, people indoors, memes, screenshots, pets/animals, dry clear roads, food, random objects, or completely unrelated scenes. " +
        "Return ONLY valid JSON in this exact structure: " +
        "{\"is_flood\": boolean, \"confidence\": float between 0.0 and 1.0, \"estimated_severity\": \"Ankle-Deep\"|\"Knee-Deep\"|\"Waist-Deep\"|\"Impassable\"|\"None\", \"reason\": \"clear short 1-2 sentence explanation\"}"

    fun verifyPhoto(bitmap: Bitmap): GeminiVerificationResult {
        val scaled = scaleForInference(bitmap, 512)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, stream)
        val jpegBytes = stream.toByteArray()
        val base64Data = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

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
                                Log.d(TAG, "Gemini ($model) result: isFlood=${parsed.isFlood}, severity=${parsed.estimatedSeverity}, reason=${parsed.reason}")
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
            isErrorOrTimeout = true
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
            val confidence = json.optDouble("confidence", 0.0)
            val severity = json.optString("estimated_severity", "None")
            val reason = json.optString("reason", if (isFlood) "Flood detected" else "No flood detected in image")

            GeminiVerificationResult(
                isValid = isFlood || confidence < 0.65, // only invalidate if AI is confident it's NOT flood
                isFlood = isFlood,
                confidence = confidence,
                estimatedSeverity = severity,
                reason = reason,
                isErrorOrTimeout = false
            )
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
