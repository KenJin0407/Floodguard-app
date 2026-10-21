package com.example.floodguard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object UserSession {
    private const val PREF_NAME = "floodguard_session"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_IS_PHONE_VERIFIED = "is_phone_verified"
    private const val KEY_USERNAME = "username"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_GENDER = "user_gender"
    private const val KEY_USER_CONTACT = "user_contact"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isLoggedIn(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun isPhoneVerified(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_PHONE_VERIFIED, true)
    }

    fun setPhoneVerified(context: Context, verified: Boolean) {
        getPrefs(context).edit {
            putBoolean(KEY_IS_PHONE_VERIFIED, verified)
        }
    }

    fun setLoggedIn(
        context: Context,
        loggedIn: Boolean,
        username: String = "juan",
        userName: String = "Juan Dela Cruz",
        userGender: String = "Male",
        userContact: String = "+63 915 245 6879",
        phoneVerified: Boolean = true,
    ) {
        val cleanUser = username.trim()
        val cleanName = userName.trim()
        val userKey = cleanUser.lowercase()
        getPrefs(context).edit {
            putBoolean(KEY_IS_LOGGED_IN, loggedIn)
            putBoolean(KEY_IS_PHONE_VERIFIED, phoneVerified)
            putString(KEY_USERNAME, cleanUser)
            putString(KEY_USER_NAME, cleanName)
            putString(KEY_USER_GENDER, userGender)
            putString(KEY_USER_CONTACT, userContact)
            if (userKey.isNotEmpty()) {
                putString("saved_name_$userKey", cleanName)
                putString("saved_gender_$userKey", userGender)
                putString("saved_contact_$userKey", userContact)
            }
        }
    }

    fun logout(context: Context) {
        getPrefs(context).edit {
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_USERNAME)
            remove(KEY_USER_NAME)
            remove(KEY_USER_GENDER)
            remove(KEY_USER_CONTACT)
        }
    }

    fun getUsername(context: Context): String {
        val storedUsername = getPrefs(context).getString(KEY_USERNAME, null)?.trim()
        if (!storedUsername.isNullOrEmpty()) {
            return storedUsername
        }
        val fallbackName = getUserName(context).trim()
        if (fallbackName.equals("Juan Dela Cruz", ignoreCase = true)) {
            return "juan"
        }
        val matchedUser = try {
            FloodGuardDatabase(context).getUserByFullName(fallbackName)
        } catch (_: Exception) {
            null
        }
        if (matchedUser != null && matchedUser.username.isNotBlank()) {
            getPrefs(context).edit {
                putString(KEY_USERNAME, matchedUser.username.trim())
            }
            return matchedUser.username.trim()
        }
        return fallbackName
    }

    fun getUserName(context: Context): String {
        return getPrefs(context).getString(KEY_USER_NAME, "Juan Dela Cruz") ?: "Juan Dela Cruz"
    }

    fun getUserGender(context: Context): String {
        return getPrefs(context).getString(KEY_USER_GENDER, "Male") ?: "Male"
    }

    fun getUserContact(context: Context): String {
        return getPrefs(context).getString(KEY_USER_CONTACT, "+63 915 245 6879") ?: "+63 915 245 6879"
    }

    fun getSavedNameForUser(context: Context, username: String): String? {
        return getPrefs(context).getString("saved_name_${username.trim().lowercase()}", null)
    }

    fun getSavedGenderForUser(context: Context, username: String): String? {
        return getPrefs(context).getString("saved_gender_${username.trim().lowercase()}", null)
    }

    fun getSavedContactForUser(context: Context, username: String): String? {
        return getPrefs(context).getString("saved_contact_${username.trim().lowercase()}", null)
    }

    fun updateProfile(context: Context, name: String, gender: String, contact: String) {
        val currentUserKey = getUsername(context).trim().lowercase()
        getPrefs(context).edit {
            putString(KEY_USER_NAME, name.trim())
            putString(KEY_USER_GENDER, gender)
            putString(KEY_USER_CONTACT, contact)
            if (currentUserKey.isNotEmpty()) {
                putString("saved_name_$currentUserKey", name.trim())
                putString("saved_gender_$currentUserKey", gender)
                putString("saved_contact_$currentUserKey", contact)
            }
        }
    }

    fun setUserAvatar(context: Context, avatarData: String) {
        val currentUserKey = getUsername(context).trim().lowercase()
        if (currentUserKey.isNotEmpty()) {
            getPrefs(context).edit {
                putString("saved_avatar_$currentUserKey", avatarData)
            }
        }
    }

    fun getUserAvatar(context: Context): String? {
        val currentUserKey = getUsername(context).trim().lowercase()
        if (currentUserKey.isEmpty()) return null
        return getPrefs(context).getString("saved_avatar_$currentUserKey", null)
    }

    fun hashPassword(password: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    const val MAX_DAILY_REPORTS = 4

    private fun getTodayDateKey(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return sdf.format(java.util.Date())
    }

    fun getDailyReportCount(context: Context): Int {
        val prefs = getPrefs(context)
        val userKey = getUsername(context).lowercase(java.util.Locale.ROOT).trim()
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
        val userKey = getUsername(context).lowercase(java.util.Locale.ROOT).trim()
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
