package com.example.floodguard

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Broadcast manager for dispatching community emergency flood alert SMS messages
 * and managing broadcast history and notification triggers based on geolocation proximity.
 */
object SmsAlertBroadcastManager {

    private const val PREF_NAME = "floodguard_sms_broadcasts"
    private const val KEY_BROADCAST_HISTORY = "broadcast_history_json"
    private const val KEY_SEEN_BROADCAST_IDS = "seen_broadcast_ids"

    // Free public SMS API endpoint (Textbelt Free SMS API)
    const val FREE_SMS_API_ENDPOINT = "https://textbelt.com/text"
    const val FREE_SMS_API_KEY = "textbelt"

    // Default user area center (San Mateo, Rizal)
    const val DEFAULT_USER_LAT = 14.6958
    const val DEFAULT_USER_LON = 121.1207
    const val NEARBY_ALERT_RADIUS_KM = 5.0

    fun resolveCoordinatesForLocation(location: String): Pair<Double, Double> {
        val q = location.lowercase(Locale.ROOT)
        return when {
            q.contains("ampid") -> Pair(14.6885, 121.1120)
            q.contains("timberland") || q.contains("malanday") -> Pair(14.6812, 121.1620)
            q.contains("dulong") || q.contains("bayan") && !q.contains("guitnang") -> Pair(14.7120, 121.1280)
            q.contains("banaba") -> Pair(14.6750, 121.1080)
            q.contains("maly") -> Pair(14.7200, 121.1350)
            q.contains("santa ana") || q.contains("sta ana") || q.contains("sta. ana") -> Pair(14.6920, 121.1180)
            q.contains("silangan") -> Pair(14.6850, 121.1300)
            q.contains("patiis") -> Pair(14.6980, 121.1380)
            q.contains("batasan") -> Pair(14.6805, 121.1135)
            q.contains("felix") -> Pair(14.6150, 121.1020)
            q.contains("del pilar") -> Pair(14.6965, 121.1195)
            q.contains("gen. luna") || q.contains("general luna") || q.contains("guitnang") -> Pair(14.6958, 121.1207)
            else -> Pair(DEFAULT_USER_LAT, DEFAULT_USER_LON)
        }
    }

    fun distanceInKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }

    fun isUserNearArea(
        reportLat: Double,
        reportLon: Double,
        userLat: Double = DEFAULT_USER_LAT,
        userLon: Double = DEFAULT_USER_LON,
        radiusKm: Double = NEARBY_ALERT_RADIUS_KM
    ): Boolean {
        return distanceInKm(userLat, userLon, reportLat, reportLon) <= radiusKm
    }

    private fun normalizeLocationTokens(loc: String): Set<String> {
        val cleaned = loc.lowercase(Locale.ROOT)
            .replace("auto detected -", "")
            .replace(",", " ")
            .replace(".", " ")
            .replace("-", " ")
            .replace("/", " ")
        val stopWords = setOf(
            "st", "street", "ave", "avenue", "rd", "road",
            "brgy", "barangay", "city", "province", "rizal"
        )
        return cleaned.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()
    }

    fun areReportsSimilar(r1: FloodReport, r2: FloodReport): Boolean {
        val loc1 = r1.location.trim().lowercase(Locale.ROOT).replace("auto detected - ", "")
        val loc2 = r2.location.trim().lowercase(Locale.ROOT).replace("auto detected - ", "")

        if (loc1.isNotEmpty() && loc2.isNotEmpty() && (loc1.contains(loc2) || loc2.contains(loc1))) {
            return true
        }

        val tokens1 = normalizeLocationTokens(loc1)
        val tokens2 = normalizeLocationTokens(loc2)
        val specific1 = tokens1 - setOf("san", "mateo")
        val specific2 = tokens2 - setOf("san", "mateo")

        if (specific1.isNotEmpty() && specific2.isNotEmpty() && specific1.intersect(specific2).isNotEmpty()) {
            return true
        }

        val (lat1, lon1) = if (r1.lat != 0.0 && (r1.lat != DEFAULT_USER_LAT || r1.lon != DEFAULT_USER_LON)) {
            Pair(r1.lat, r1.lon)
        } else {
            resolveCoordinatesForLocation(r1.location)
        }
        val (lat2, lon2) = if (r2.lat != 0.0 && (r2.lat != DEFAULT_USER_LAT || r2.lon != DEFAULT_USER_LON)) {
            Pair(r2.lat, r2.lon)
        } else {
            resolveCoordinatesForLocation(r2.location)
        }

        val distKm = distanceInKm(lat1, lon1, lat2, lon2)
        if (distKm <= 2.0 && (tokens1.intersect(tokens2).isNotEmpty() || r1.severity.equals(r2.severity, ignoreCase = true))) {
            return true
        }

        return false
    }

    fun findSimilarActiveReports(targetReport: FloodReport, allReports: List<FloodReport>): List<FloodReport> {
        if (targetReport.severityLevel == SeverityLevel.CLEARED) return emptyList()
        val snapshot = allReports.toList()
        return snapshot.filter { candidate ->
            candidate.severityLevel != SeverityLevel.CLEARED && areReportsSimilar(targetReport, candidate)
        }
    }

    fun formatPhoneE164(rawPhone: String): String {
        val digits = rawPhone.filter { it.isDigit() || it == '+' }
        if (digits.isBlank()) return ""
        return when {
            digits.startsWith("09") && digits.length == 11 -> "+63" + digits.substring(1)
            digits.startsWith("9") && digits.length == 10 -> "+63$digits"
            !digits.startsWith("+") -> "+$digits"
            else -> digits
        }
    }

    fun collectNearbyRecipientPhones(context: Context): List<String> {
        val phones = linkedSetOf<String>()
        try {
            val currentContact = formatPhoneE164(UserSession.getUserContact(context))
            if (currentContact.isNotBlank()) {
                phones.add(currentContact)
            }
            val dbUsers = FloodGuardDatabase.getInstance(context).getAllUsers()
            for (u in dbUsers) {
                val formatted = formatPhoneE164(u.mobile)
                if (formatted.isNotBlank()) {
                    phones.add(formatted)
                }
            }
        } catch (_: Exception) {
        }
        if (phones.isEmpty()) {
            phones.add("+639152456879")
        }
        return phones.toList()
    }

    /**
     * Evaluates whether a newly submitted report has reached >= 2 similar reports in the area.
     * If so, triggers the Free SMS API (Textbelt) to broadcast the SMS warning to all users near the area
     * and dispatches the pop-up warning alert to nearby users.
     */
    fun evaluateAndBroadcastIfCorroborated(
        context: Context,
        newReport: FloodReport,
        allReports: List<FloodReport>,
        onBroadcastTriggered: ((FloodBroadcast) -> Unit)? = null
    ) {
        val similarReports = findSimilarActiveReports(newReport, allReports)
        if (similarReports.size < 2) {
            return
        }

        val (resolvedLat, resolvedLon) = if (newReport.lat != DEFAULT_USER_LAT || newReport.lon != DEFAULT_USER_LON) {
            Pair(newReport.lat, newReport.lon)
        } else {
            resolveCoordinatesForLocation(newReport.location)
        }

        val recipientPhones = collectNearbyRecipientPhones(context)
        val reporters = similarReports
            .map { it.reporterName.ifBlank { it.authorUsername.ifBlank { "Community Reporter" } } }
            .distinct()
            .take(3)
            .joinToString(", ")

        val similarCount = similarReports.size
        val smsMessage = "[FLOODGUARD SMS ALERT] URGENT: $similarCount similar flood reports verified near " +
            "${newReport.location}. Water Level: ${newReport.severity} (${newReport.statusText}). " +
            "${newReport.notes}. Avoid flooded roads and move to safer ground."

        val broadcast = FloodBroadcast(
            id = "sms_bcast_${newReport.id}_${similarCount}",
            location = newReport.location,
            severity = newReport.severity,
            notes = newReport.notes,
            reporterName = reporters,
            timestamp = System.currentTimeMillis(),
            authorUsername = newReport.authorUsername,
            statusText = newReport.statusText,
            similarReportCount = similarCount,
            lat = resolvedLat,
            lon = resolvedLon,
            radiusKm = NEARBY_ALERT_RADIUS_KM,
            smsMessage = smsMessage,
            smsProvider = "Textbelt Free SMS API",
            smsRecipientsCount = recipientPhones.size,
            recipientPhones = recipientPhones
        )

        saveBroadcastLocally(context, broadcast)

        // Dispatch via Free SMS API (https://textbelt.com/text) on background thread
        thread {
            for (phone in recipientPhones) {
                sendSmsViaFreeApi(phone, smsMessage)
            }
            onBroadcastTriggered?.invoke(broadcast)
            FloatingAlertManager.deliverBroadcastIfNearby(context, broadcast, forcePopUp = true)
        }
    }

    private fun sendSmsViaFreeApi(phone: String, message: String): Boolean {
        return try {
            val url = URL(FREE_SMS_API_ENDPOINT)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("phone", phone)
                put("message", message)
                put("key", FREE_SMS_API_KEY)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = conn.responseCode
            val responseText = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            conn.disconnect()

            val success = try {
                JSONObject(responseText).optBoolean("success", false)
            } catch (_: Exception) {
                false
            }
            Log.d("FloodGuardSMS", "Free SMS API ($FREE_SMS_API_ENDPOINT) dispatched to $phone [HTTP $code]: $responseText")
            success
        } catch (e: Exception) {
            Log.w("FloodGuardSMS", "Free SMS API fallback for $phone: ${e.message}")
            false
        }
    }

    fun saveBroadcastLocally(context: Context, broadcast: FloodBroadcast) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_BROADCAST_HISTORY, "[]") ?: "[]"
        val existingArray = try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }

        val updatedArray = JSONArray()
        updatedArray.put(broadcastToJson(broadcast))

        for (i in 0 until existingArray.length()) {
            val obj = existingArray.optJSONObject(i) ?: continue
            if (obj.optString("id") != broadcast.id && updatedArray.length() < 25) {
                updatedArray.put(obj)
            }
        }

        prefs.edit {
            putString(KEY_BROADCAST_HISTORY, updatedArray.toString())
        }
    }

    fun getSavedBroadcasts(context: Context): List<FloodBroadcast> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_BROADCAST_HISTORY, "[]") ?: "[]"
        val list = mutableListOf<FloodBroadcast>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(jsonToBroadcast(obj))
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun hasUserSeenBroadcast(context: Context, broadcastId: String): Boolean {
        val userKey = UserSession.getUsername(context).trim().lowercase(Locale.ROOT).ifEmpty { "guest" }
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val seenSet = prefs.getStringSet("${KEY_SEEN_BROADCAST_IDS}_$userKey", emptySet()) ?: emptySet()
        return broadcastId in seenSet
    }

    fun markBroadcastSeen(context: Context, broadcastId: String) {
        val userKey = UserSession.getUsername(context).trim().lowercase(Locale.ROOT).ifEmpty { "guest" }
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("${KEY_SEEN_BROADCAST_IDS}_$userKey", emptySet())?.toMutableSet()
            ?: mutableSetOf()
        current.add(broadcastId)
        prefs.edit {
            putStringSet("${KEY_SEEN_BROADCAST_IDS}_$userKey", current)
        }
    }

    fun broadcastToJson(b: FloodBroadcast): JSONObject {
        return JSONObject().apply {
            put("id", b.id)
            put("location", b.location)
            put("severity", b.severity)
            put("notes", b.notes)
            put("reporterName", b.reporterName)
            put("timestamp", b.timestamp)
            put("authorUsername", b.authorUsername)
            put("statusText", b.statusText)
            put("similarReportCount", b.similarReportCount)
            put("lat", b.lat)
            put("lon", b.lon)
            put("radiusKm", b.radiusKm)
            put("smsMessage", b.smsMessage)
            put("smsProvider", b.smsProvider)
            put("smsRecipientsCount", b.smsRecipientsCount)
            val phonesArr = JSONArray()
            b.recipientPhones.forEach { phonesArr.put(it) }
            put("recipientPhones", phonesArr)
        }
    }

    fun jsonToBroadcast(obj: JSONObject): FloodBroadcast {
        val phones = mutableListOf<String>()
        val phonesArr = obj.optJSONArray("recipientPhones")
        if (phonesArr != null) {
            for (i in 0 until phonesArr.length()) {
                val p = phonesArr.optString(i)
                if (p.isNotBlank()) phones.add(p)
            }
        }
        val loc = obj.optString("location", "San Mateo, Rizal")
        val (defaultLat, defaultLon) = resolveCoordinatesForLocation(loc)
        return FloodBroadcast(
            id = obj.optString("id", "sms_bcast_${System.currentTimeMillis()}"),
            location = loc,
            severity = obj.optString("severity", "Waist-Deep"),
            notes = obj.optString("notes", ""),
            reporterName = obj.optString("reporterName", "Community Reporter"),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            authorUsername = obj.optString("authorUsername", ""),
            statusText = obj.optString("statusText", "Not Passable"),
            similarReportCount = obj.optInt("similarReportCount", 2).coerceAtLeast(2),
            lat = obj.optDouble("lat", defaultLat),
            lon = obj.optDouble("lon", defaultLon),
            radiusKm = obj.optDouble("radiusKm", NEARBY_ALERT_RADIUS_KM),
            smsMessage = obj.optString("smsMessage", ""),
            smsProvider = obj.optString("smsProvider", "Textbelt Free SMS API"),
            smsRecipientsCount = obj.optInt("smsRecipientsCount", phones.size.coerceAtLeast(1)),
            recipientPhones = phones
        )
    }
}
