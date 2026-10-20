package com.example.floodguard

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * HTTP client service for communicating with the FloodGuard REST backend.
 * Provides synchronization for flood reports, user registration, authentication,
 * weather data, and satellite GeoJSON flood hazard mapping layers.
 */
object FloodGuardApiService {

    private val HOST_URLS = listOf(
        "http://10.0.2.2:8000/api",
        "http://127.0.0.1:8000/api",
        "http://localhost:8000/api",
        "http://192.168.1.9:8000/api",
        "http://10.0.2.2:3000/api",
        "http://127.0.0.1:3000/api",
        "http://localhost:3000/api",
        "http://192.168.1.9:3000/api",
    )

    fun fetchFloodGeoJsonFromBackend(onSuccess: (String) -> Unit) {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val url = URL("$baseUrl/mapping/flood-geojson")
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
                        onSuccess(response.toString())
                        return@thread
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w("FloodGuardApi", "GeoJSON fetch failed on $baseUrl: ${e.message}")
                }
            }
        }
    }

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
                                val reporterUsername = item.optString("reporterUsername", "")

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
                                        reporterUsername = reporterUsername,
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

                    val authorUser = report.authorUsername.ifBlank { report.reporterUsername.ifBlank { "anonymous" } }
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
                        put("username", authorUser)
                        put("authorUsername", authorUser)
                        put("reporterName", report.reporterName)
                    }

                    val writer = OutputStreamWriter(conn.outputStream)
                    writer.write(payload.toString())
                    writer.flush()
                    writer.close()

                    val code = conn.responseCode
                    Log.d("FloodGuardApi", "Submit report to $baseUrl code: $code")
                    if (code in 200..299) {
                        val response = conn.inputStream?.bufferedReader()?.use { it.readText() } ?: "{}"
                        val respJson = JSONObject(response)
                        val notificationAccepted = respJson
                            .optJSONObject("notification")
                            ?.optBoolean("accepted", false) == true
                        val smsBcastObj = respJson.optJSONObject("smsBroadcast")
                        val parsedBroadcast = if (smsBcastObj != null) {
                            SmsAlertBroadcastManager.jsonToBroadcast(smsBcastObj)
                        } else null

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

    fun submitReportToBackend(report: FloodReport, username: String) {
        if (report.authorUsername.isBlank()) {
            report.authorUsername = username
        }
        submitReportToBackend(report) {}
    }

    fun updateReportInBackend(
        id: String,
        location: String,
        severity: String,
        notes: String,
        username: String = ""
    ) {
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
                        if (username.isNotBlank()) put("username", username)
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

    fun deleteReportInBackend(id: String, username: String = "") {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val query = if (username.isNotBlank()) "?username=${URLEncoder.encode(username, "UTF-8")}" else ""
                    val url = URL("$baseUrl/reports/$id$query")
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

    fun updatePasswordInBackend(
        username: String,
        currentPassword: String,
        newPassword: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val url = URL("$baseUrl/auth/password")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "PUT"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.doOutput = true

                    val payload = JSONObject().apply {
                        put("username", username)
                        put("currentPassword", currentPassword)
                        put("newPassword", newPassword)
                    }

                    val writer = OutputStreamWriter(conn.outputStream)
                    writer.write(payload.toString())
                    writer.flush()
                    writer.close()

                    val code = conn.responseCode
                    Log.d("FloodGuardApi", "Update password on $baseUrl code: $code")
                    if (code in 200..299) {
                        conn.inputStream?.readBytes()
                        conn.disconnect()
                        onResult?.invoke(true, "Password updated successfully!")
                        return@thread
                    } else {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        val detail = try {
                            JSONObject(err).optString("detail", "Failed to update password on server")
                        } catch (_: Exception) {
                            "Failed to update password on server"
                        }
                        conn.disconnect()
                        onResult?.invoke(false, detail)
                        return@thread
                    }
                } catch (e: Exception) {
                    Log.w("FloodGuardApi", "Update password failed on $baseUrl: ${e.message}")
                }
            }
            onResult?.invoke(false, "Server unreachable")
        }
    }

    fun resetPasswordInBackend(
        username: String,
        newPassword: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        thread {
            for (baseUrl in HOST_URLS) {
                try {
                    val url = URL("$baseUrl/auth/reset-password")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "PUT"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.doOutput = true

                    val payload = JSONObject().apply {
                        put("username", username)
                        put("newPassword", newPassword)
                    }

                    val writer = OutputStreamWriter(conn.outputStream)
                    writer.write(payload.toString())
                    writer.flush()
                    writer.close()

                    val code = conn.responseCode
                    Log.d("FloodGuardApi", "Reset password on $baseUrl code: $code")
                    if (code in 200..299) {
                        conn.inputStream?.readBytes()
                        conn.disconnect()
                        onResult?.invoke(true, "Password reset successfully!")
                        return@thread
                    } else {
                        val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        val detail = try {
                            JSONObject(err).optString("detail", "Failed to reset password on server")
                        } catch (_: Exception) {
                            "Failed to reset password on server"
                        }
                        conn.disconnect()
                        onResult?.invoke(false, detail)
                        return@thread
                    }
                } catch (e: Exception) {
                    Log.w("FloodGuardApi", "Reset password failed on $baseUrl: ${e.message}")
                }
            }
            onResult?.invoke(false, "Server unreachable")
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

