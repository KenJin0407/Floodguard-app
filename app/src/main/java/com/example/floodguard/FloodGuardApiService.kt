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

    private val HOST_URLS = listOf(BuildConfig.API_BASE_URL)

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
                        val notificationAccepted = JSONObject(response)
                            .optJSONObject("notification")
                            ?.optBoolean("accepted", false) == true
                        conn.disconnect()
                        onResult(ReportSubmissionStatus(submittedToServer = true, pushAccepted = notificationAccepted))
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

}

data class ReportSubmissionStatus(
    val submittedToServer: Boolean,
    val pushAccepted: Boolean,
)

data class FloodBroadcast(
    val id: String,
    val location: String,
    val severity: String,
    val notes: String,
    val reporterName: String,
    val timestamp: Long,
    val authorUsername: String = "",
)
