package com.example.floodguard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object UserSession {
    private const val PREF_NAME = "floodguard_session"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USERNAME = "username"
    private const val KEY_USER_GENDER = "user_gender"
    private const val KEY_USER_CONTACT = "user_contact"
    private const val KEY_USER_EMAIL = "user_email"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isLoggedIn(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun hashPassword(password: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setLoggedIn(
        context: Context,
        loggedIn: Boolean,
        userName: String = "Juan Dela Cruz",
        username: String = "juan",
        userGender: String = "Male",
        userContact: String = "+63 915 245 6879",
        userEmail: String = "juan@example.com",
    ) {
        getPrefs(context).edit {
            putBoolean(KEY_IS_LOGGED_IN, loggedIn)
            putString(KEY_USER_NAME, userName)
            putString(KEY_USERNAME, username)
            putString(KEY_USER_GENDER, userGender)
            putString(KEY_USER_CONTACT, userContact)
            putString(KEY_USER_EMAIL, userEmail)
        }
    }

    fun logout(context: Context) {
        getPrefs(context).edit {
            putBoolean(KEY_IS_LOGGED_IN, false)
        }
    }

    fun getUserName(context: Context): String {
        return getPrefs(context).getString(KEY_USER_NAME, "Juan Dela Cruz") ?: "Juan Dela Cruz"
    }

    fun getUserUsername(context: Context): String {
        return getPrefs(context).getString(KEY_USERNAME, "juan") ?: "juan"
    }

    fun getUserGender(context: Context): String {
        return getPrefs(context).getString(KEY_USER_GENDER, "Male") ?: "Male"
    }

    fun getUserContact(context: Context): String {
        return getPrefs(context).getString(KEY_USER_CONTACT, "+63 915 245 6879") ?: "+63 915 245 6879"
    }

    fun getUserEmail(context: Context): String {
        return getPrefs(context).getString(KEY_USER_EMAIL, "juan@example.com") ?: "juan@example.com"
    }

    fun updateProfile(context: Context, name: String, gender: String, contact: String, email: String = "") {
        getPrefs(context).edit {
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_GENDER, gender)
            putString(KEY_USER_CONTACT, contact)
            if (email.isNotEmpty()) putString(KEY_USER_EMAIL, email)
        }
    }

    const val MAX_DAILY_REPORTS = 4

    private fun getTodayDateKey(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return sdf.format(java.util.Date())
    }

    fun getDailyReportCount(context: Context): Int {
        val prefs = getPrefs(context)
        val userKey = getUserEmail(context).lowercase(java.util.Locale.ROOT).trim()
        val today = getTodayDateKey()
        val savedDate = prefs.getString("report_date_$userKey", "") ?: ""

        if (savedDate != today) {
            prefs.edit {
                putString("report_date_$userKey", today)
                putInt("report_count_$userKey", 0)
            }
            return 0
        }
        return prefs.getInt("report_count_$userKey", 0)
    }

    fun getRemainingReportsToday(context: Context): Int {
        val used = getDailyReportCount(context)
        return (MAX_DAILY_REPORTS - used).coerceAtLeast(0)
    }

    fun incrementDailyReportCount(context: Context): Int {
        val prefs = getPrefs(context)
        val userKey = getUserEmail(context).lowercase(java.util.Locale.ROOT).trim()
        val today = getTodayDateKey()
        val currentUsed = getDailyReportCount(context)
        val newUsed = currentUsed + 1

        prefs.edit {
            putString("report_date_$userKey", today)
            putInt("report_count_$userKey", newUsed)
        }
        return (MAX_DAILY_REPORTS - newUsed).coerceAtLeast(0)
    }
}
