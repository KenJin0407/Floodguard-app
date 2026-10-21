package com.example.floodguard

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object FloodGuardApiService {

    private val HOST_URLS = listOf(
        BuildConfig.API_BASE_URL,
        "http://192.168.100.184:3000/api",
        "http://192.168.254.175:3000/api",
        "http://10.0.2.2:3000/api",
        "http://127.0.0.1:3000/api",
        "http://192.168.254.175:8000/api",
        "http://10.0.2.2:8000/api",
    ).distinct()

    fun syncReportsFromBackend(onSuccess: (List<FloodReport>) -> Unit) {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val url = URL("$baseUrl/reports")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000

                    if (conn.responseCode == 200) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream))
                        val response = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }
                        reader.close()

                        val json = JSONObject(response.toString())
                        val array = json.optJSONArray("reports")
                        val list = mutableListOf<FloodReport>()

                        if (array != null) {
                            for (i in 0 until array.length()) {
                                val item = array.getJSONObject(i)
                                val id = item.optString("id")
                                val location = item.optString("location")
                                val severity = item.optString("severity")
                                val statusText = item.optString("statusText")
                                val notes = item.optString("notes")
                                val timestamp = item.optLong("timestamp", System.currentTimeMillis())
                                val severityLevelStr = item.optString("severityLevel")
                                val photoUri = if (item.has("photoUri") && !item.isNull("photoUri")) item.optString("photoUri") else null
                                val lat = item.optDouble("lat", 14.6958)
                                val lon = item.optDouble("lon", 121.1207)
                                val authorUsername = item.optString("authorUsername", "")
                                val reporterName = item.optString("reporterName", "")

                                val level = try {
                                    SeverityLevel.valueOf(severityLevelStr)
                                } catch (_: Exception) {
                                    SeverityLevel.NOT_PASSABLE
                                }

                                list.add(
                                    FloodReport(
                                        id = id,
                                        location = location,
                                        severity = severity,
                                        statusText = statusText,
                                        notes = notes,
                                        timestamp = timestamp,
                                        severityLevel = level,
                                        photoUri = photoUri,
                                        lat = lat,
                                        lon = lon,
                                        authorUsername = authorUsername,
                                        reporterName = reporterName,
                                    )
                                )
                            }
                        }
                        conn.disconnect()
                        onSuccess(list)
                        return@thread
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w("FloodGuardApi", "Sync failed on $baseUrl: ${e.message}")
                }
            }
        }
    }

    fun syncUsersFromBackend(onSuccess: (List<UserEntity>) -> Unit) {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val url = URL("$baseUrl/users")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000

                    if (conn.responseCode == 200) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream))
                        val response = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }
                        reader.close()

                        val json = JSONObject(response.toString())
                        val array = json.optJSONArray("users")
                        val list = mutableListOf<UserEntity>()

                        if (array != null) {
                            for (i in 0 until array.length()) {
                                val item = array.getJSONObject(i)
                                list.add(
                                    UserEntity(
                                        id = item.optString("id"),
                                        fullName = item.optString("fullName"),
                                        gender = item.optString("gender", "Male"),
                                        mobile = item.optString("mobile"),
                                        username = item.optString("username"),
                                        password = item.optString("password", "password123")
                                    )
                                )
                            }
                        }
                        conn.disconnect()
                        onSuccess(list)
                        return@thread
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w("FloodGuardApi", "Sync users failed on $baseUrl: ${e.message}")
                }
            }
        }
    }

    fun submitReportToBackend(
        report: FloodReport,
        onResult: (ReportSubmissionStatus) -> Unit = {}
    ) {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val url = URL("$baseUrl/reports")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.doOutput = true

                    val payload = JSONObject().apply {
                        put("id", report.id)
                        put("location", report.location)
                        put("severity", report.severity)
                        put("statusText", report.statusText)
                        put("notes", report.notes)
                        put("timestamp", report.timestamp)
                        put("severityLevel", report.severityLevel.name)
                        put("photoUri", report.photoUri)
                        put("lat", report.lat)
                        put("lon", report.lon)
                        put("authorUsername", report.authorUsername)
                        put("reporterName", report.reporterName)
                    }

                    val writer = OutputStreamWriter(conn.outputStream)
                    writer.write(payload.toString())
                    writer.flush()
                    writer.close()

                    val code = conn.responseCode
                    Log.d("FloodGuardApi", "Submit report to $baseUrl code: $code")
                    if (code in 200..299) {
                        val response = conn.inputStream?.bufferedReader()?.use { it.readText() }
                            ?: "{}"
                        val respJson = JSONObject(response)
                        val notificationAccepted = respJson
                            .optJSONObject("notification")
                            ?.optBoolean("accepted", false) == true
                        val smsBcastObj = respJson.optJSONObject("smsBroadcast")
                        val parsedBroadcast = if (smsBcastObj != null) {
                            SmsAlertBroadcastManager.jsonToBroadcast(smsBcastObj)
                        } else {
                            null
                        }
                        conn.disconnect()
                        onResult(
                            ReportSubmissionStatus(
                                submittedToServer = true,
                                pushAccepted = notificationAccepted || parsedBroadcast != null,
                                smsBroadcastTriggered = parsedBroadcast != null,
                                smsBroadcast = parsedBroadcast,
                            )
                        )
                        return@thread
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w("FloodGuardApi", "Submit report failed on $baseUrl: ${e.message}")
                }
            }
            onResult(ReportSubmissionStatus(submittedToServer = false, pushAccepted = false))
        }
    }

    fun updateReportInBackend(id: String, location: String, severity: String, notes: String, authorUsername: String = "") {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val url = URL("$baseUrl/reports/$id")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "PUT"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.doOutput = true

                    val payload = JSONObject().apply {
                        put("location", location)
                        put("severity", severity)
                        put("notes", notes)
                        put("authorUsername", authorUsername)
                    }

                    val writer = OutputStreamWriter(conn.outputStream)
                    writer.write(payload.toString())
                    writer.flush()
                    writer.close()

                    val code = conn.responseCode
                    if (code in 200..299) {
                        conn.inputStream?.readBytes()
                        conn.disconnect()
                        return@thread
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w("FloodGuardApi", "Update report failed on $baseUrl: ${e.message}")
                }
            }
        }
    }

    fun deleteReportInBackend(id: String, authorUsername: String = "") {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val encodedAuthor = java.net.URLEncoder.encode(authorUsername, "UTF-8")
                    val url = URL("$baseUrl/reports/$id?authorUsername=$encodedAuthor")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "DELETE"
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000

                    val code = conn.responseCode
                    if (code in 200..299) {
                        conn.inputStream?.readBytes()
                        conn.disconnect()
                        return@thread
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w("FloodGuardApi", "Delete report failed on $baseUrl: ${e.message}")
                }
            }
        }
    }

    fun registerUserInBackend(user: UserEntity) {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val url = URL("$baseUrl/auth/register")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.doOutput = true

                    val payload = JSONObject().apply {
                        put("fullName", user.fullName)
                        put("gender", user.gender)
                        put("mobile", user.mobile)
                        put("username", user.username)
                        put("password", user.password)
                    }

                    val writer = OutputStreamWriter(conn.outputStream)
                    writer.write(payload.toString())
                    writer.flush()
                    writer.close()

                    val code = conn.responseCode
                    Log.d("FloodGuardApi", "Register user to $baseUrl code: $code")
                    if (code in 200..299) {
                        conn.inputStream?.readBytes()
                        conn.disconnect()
                        return@thread
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w("FloodGuardApi", "Register user failed on $baseUrl: ${e.message}")
                }
            }
        }
    }

    fun updateUserProfileInBackend(username: String, fullName: String, gender: String, mobile: String) {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val url = URL("$baseUrl/auth/profile")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "PUT"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.doOutput = true

                    val payload = JSONObject().apply {
                        put("username", username)
                        put("fullName", fullName)
                        put("gender", gender)
                        put("mobile", mobile)
                    }

                    val writer = OutputStreamWriter(conn.outputStream)
                    writer.write(payload.toString())
                    writer.flush()
                    writer.close()

                    val code = conn.responseCode
                    Log.d("FloodGuardApi", "Update profile on $baseUrl code: $code")
                    if (code in 200..299) {
                        conn.inputStream?.readBytes()
                        conn.disconnect()
                        return@thread
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w("FloodGuardApi", "Update profile failed on $baseUrl: ${e.message}")
                }
            }
        }
    }

    fun loginUserInBackend(username: String, password: String, onResult: (UserEntity?) -> Unit) {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val url = URL("$baseUrl/auth/login")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.doOutput = true

                    val payload = JSONObject().apply {
                        put("username", username)
                        put("password", password)
                    }

                    val writer = OutputStreamWriter(conn.outputStream)
                    writer.write(payload.toString())
                    writer.flush()
                    writer.close()

                    if (conn.responseCode == 200) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream))
                        val response = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }
                        reader.close()

                        val json = JSONObject(response.toString())
                        val userObj = json.optJSONObject("user")
                        if (userObj != null) {
                            val user = UserEntity(
                                id = userObj.optString("id"),
                                fullName = userObj.optString("fullName"),
                                gender = userObj.optString("gender", "Male"),
                                mobile = userObj.optString("mobile"),
                                username = userObj.optString("username", username),
                                password = password,
                            )
                            conn.disconnect()
                            onResult(user)
                            return@thread
                        }
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w("FloodGuardApi", "Login failed on $baseUrl: ${e.message}")
                }
            }
            onResult(null)
        }
    }

    fun syncBroadcastsFromBackend(onSuccess: (List<FloodBroadcast>) -> Unit) {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val url = URL("$baseUrl/broadcasts")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000

                    if (conn.responseCode == 200) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream))
                        val response = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }
                        reader.close()

                        val json = JSONObject(response.toString())
                        val array = json.optJSONArray("broadcasts")
                        val list = mutableListOf<FloodBroadcast>()
                        if (array != null) {
                            for (i in 0 until array.length()) {
                                val item = array.optJSONObject(i) ?: continue
                                list.add(SmsAlertBroadcastManager.jsonToBroadcast(item))
                            }
                        }
                        conn.disconnect()
                        onSuccess(list)
                        return@thread
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w("FloodGuardApi", "Sync broadcasts failed on $baseUrl: ${e.message}")
                }
            }
        }
    }

    fun fetchFloodGeoJsonFromBackend(): String? {
        for (baseUrl in HOST_URLS) {
            try {
                val cleanBase = baseUrl.removeSuffix("/api")
                val url = URL("$cleanBase/api/mapping/flood-geojson")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    conn.disconnect()
                    return response.toString()
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w("FloodGuardApi", "fetchFloodGeoJson failed on $baseUrl: ${e.message}")
            }
        }
        return null
    }

    fun sendOtpToBackend(mobile: String, onResult: (Boolean, String?, String?) -> Unit) {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val url = URL("$baseUrl/auth/send-otp")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.doOutput = true

                    val payload = JSONObject().apply {
                        put("mobile", mobile)
                        put("purpose", "registration")
                    }

                    OutputStreamWriter(conn.outputStream).use { writer ->
                        writer.write(payload.toString())
                        writer.flush()
                    }

                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val responseStr = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    conn.disconnect()

                    if (code in 200..299) {
                        val json = JSONObject(responseStr)
                        val demoCode = if (json.has("demoOtpCode") && !json.isNull("demoOtpCode")) json.optString("demoOtpCode") else null
                        val msg = json.optString("message", "Verification code sent")
                        onResult(true, demoCode, msg)
                        return@thread
                    } else {
                        val errDetail = try {
                            JSONObject(responseStr).optString("detail", "Failed to send code")
                        } catch (_: Exception) {
                            "Failed to send verification code"
                        }
                        onResult(false, null, errDetail)
                        return@thread
                    }
                } catch (e: Exception) {
                    Log.w("FloodGuardApi", "sendOtp failed on $baseUrl: ${e.message}")
                }
            }
            onResult(false, null, "Could not connect to server. Please check your internet connection.")
        }
    }

    fun findAccountInBackend(
        identifier: String,
        onResult: (success: Boolean, username: String?, mobile: String?, maskedMobile: String?) -> Unit
    ) {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val url = URL("$baseUrl/auth/find-account")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.doOutput = true

                    val payload = JSONObject().apply {
                        put("identifier", identifier)
                    }

                    OutputStreamWriter(conn.outputStream).use { writer ->
                        writer.write(payload.toString())
                        writer.flush()
                    }

                    val responseCode = conn.responseCode
                    val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                    val responseStr = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    conn.disconnect()

                    if (responseCode in 200..299) {
                        val json = JSONObject(responseStr)
                        val uname = json.optString("username", "")
                        val mob = json.optString("mobile", "")
                        val masked = json.optString("maskedMobile", mob)
                        onResult(true, uname, mob, masked)
                        return@thread
                    }
                } catch (e: Exception) {
                    Log.w("FloodGuardApi", "findAccount failed on $baseUrl: ${e.message}")
                }
            }
            onResult(false, null, null, null)
        }
    }

    fun resetPasswordInBackend(
        identifier: String,
        newPassword: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val url = URL("$baseUrl/auth/reset-password")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.doOutput = true

                    val payload = JSONObject().apply {
                        put("identifier", identifier)
                        put("newPassword", newPassword)
                    }

                    OutputStreamWriter(conn.outputStream).use { writer ->
                        writer.write(payload.toString())
                        writer.flush()
                    }

                    val responseCode = conn.responseCode
                    val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                    val responseStr = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    conn.disconnect()

                    if (responseCode in 200..299) {
                        val json = JSONObject(responseStr)
                        val msg = json.optString("message", "Password reset successfully")
                        onResult(true, msg)
                        return@thread
                    } else {
                        val errDetail = try {
                            JSONObject(responseStr).optString("detail", "Failed to reset password")
                        } catch (_: Exception) {
                            "Failed to reset password on server"
                        }
                        onResult(false, errDetail)
                        return@thread
                    }
                } catch (e: Exception) {
                    Log.w("FloodGuardApi", "resetPassword failed on $baseUrl: ${e.message}")
                }
            }
            onResult(false, "Could not reach server to reset password.")
        }
    }

    fun verifyOtpInBackend(mobile: String, code: String, onResult: (Boolean, String) -> Unit) {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val url = URL("$baseUrl/auth/verify-otp")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.doOutput = true

                    val payload = JSONObject().apply {
                        put("mobile", mobile)
                        put("code", code)
                    }

                    OutputStreamWriter(conn.outputStream).use { writer ->
                        writer.write(payload.toString())
                        writer.flush()
                    }

                    val responseCode = conn.responseCode
                    val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                    val responseStr = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    conn.disconnect()

                    if (responseCode in 200..299) {
                        val json = JSONObject(responseStr)
                        val msg = json.optString("message", "Verified successfully")
                        onResult(true, msg)
                        return@thread
                    } else {
                        val errDetail = try {
                            JSONObject(responseStr).optString("detail", "Verification failed")
                        } catch (_: Exception) {
                            "Incorrect or expired verification code."
                        }
                        onResult(false, errDetail)
                        return@thread
                    }
                } catch (e: Exception) {
                    Log.w("FloodGuardApi", "verifyOtp failed on $baseUrl: ${e.message}")
                }
            }
            onResult(false, "Could not reach server to verify code.")
        }
    }

}

data class ReportSubmissionStatus(
    val submittedToServer: Boolean,
    val pushAccepted: Boolean,
    val smsBroadcastTriggered: Boolean = false,
    val smsBroadcast: FloodBroadcast? = null,
)

data class FloodBroadcast(
    val id: String,
    val location: String,
    val severity: String,
    val notes: String,
    val reporterName: String,
    val timestamp: Long,
    val authorUsername: String = "",
    val statusText: String = "Not Passable",
    val similarReportCount: Int = 2,
    val lat: Double = 14.6958,
    val lon: Double = 121.1207,
    val radiusKm: Double = 5.0,
    val smsMessage: String = "",
    val smsProvider: String = "Textbelt Free SMS API",
    val smsRecipientsCount: Int = 1,
    val recipientPhones: List<String> = emptyList(),
)

