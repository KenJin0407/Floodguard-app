package com.example.floodguard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UserSession {
    private const val PREF_NAME = "floodguard_session"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USERNAME = "username"
    private const val KEY_USER_GENDER = "user_gender"
    private const val KEY_USER_CONTACT = "user_contact"

    const val MAX_DAILY_REPORTS = 4

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isLoggedIn(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setLoggedIn(
        context: Context,
        loggedIn: Boolean,
        userName: String = "Juan Dela Cruz",
        username: String = "juan",
        userGender: String = "Male",
        userContact: String = "+63 915 245 6879",
    ) {
        val cleanUser = username.trim()
        val cleanName = userName.trim()
        val userKey = cleanUser.lowercase(Locale.ROOT)
        getPrefs(context).edit {
            putBoolean(KEY_IS_LOGGED_IN, loggedIn)
            putString(KEY_USER_NAME, cleanName)
            putString(KEY_USERNAME, cleanUser)
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

    fun getUserUsername(context: Context): String = getUsername(context)

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
        return getPrefs(context).getString("saved_name_${username.trim().lowercase(Locale.ROOT)}", null)
    }

    fun getSavedGenderForUser(context: Context, username: String): String? {
        return getPrefs(context).getString("saved_gender_${username.trim().lowercase(Locale.ROOT)}", null)
    }

    fun getSavedContactForUser(context: Context, username: String): String? {
        return getPrefs(context).getString("saved_contact_${username.trim().lowercase(Locale.ROOT)}", null)
    }

    fun updateProfile(context: Context, name: String, gender: String, contact: String) {
        val currentUserKey = getUsername(context).trim().lowercase(Locale.ROOT)
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
        val currentUserKey = getUsername(context).trim().lowercase(Locale.ROOT)
        if (currentUserKey.isNotEmpty()) {
            getPrefs(context).edit {
                putString("saved_avatar_$currentUserKey", avatarData)
            }
        }
    }

    fun getUserAvatar(context: Context): String? {
        val currentUserKey = getUsername(context).trim().lowercase(Locale.ROOT)
        if (currentUserKey.isEmpty()) return null
        return getPrefs(context).getString("saved_avatar_$currentUserKey", null)
    }

    private fun getTodayDateKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun getDailyReportCount(context: Context): Int {
        val prefs = getPrefs(context)
        val userKey = getUsername(context).lowercase(Locale.ROOT).trim()
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
        val userKey = getUsername(context).lowercase(Locale.ROOT).trim()
        val today = getTodayDateKey()
        val currentUsed = getDailyReportCount(context)
        val newUsed = currentUsed + 1

        prefs.edit {
            putString("report_date_$userKey", today)
            putInt("report_count_$userKey", newUsed)
        }
        return (MAX_DAILY_REPORTS - newUsed).coerceAtLeast(0)
    }

    fun isValidContactNumber(contact: String): Boolean {
        return getContactValidationError(contact) == null
    }

    fun getContactValidationError(contact: String): String? {
        val trimmed = contact.trim()
        if (trimmed.isEmpty()) {
            return "Contact number is required."
        }
        val clean = trimmed.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        if (!clean.startsWith("+63") && !clean.startsWith("09")) {
            return "Contact number must start with +63 or 09."
        }
        if (clean.startsWith("09")) {
            if (clean.length != 11) {
                return "Contact number starting with 09 must have exactly 11 digits (e.g. 09123456789)."
            }
            if (!clean.all { it.isDigit() }) {
                return "Contact number must contain only numbers."
            }
        } else if (clean.startsWith("+63")) {
            val rest = clean.substring(3)
            if (rest.isEmpty() || !rest.all { it.isDigit() }) {
                return "Contact number must contain only numbers after +63."
            }
            if (rest.length != 10 && rest.length != 11) {
                return "Contact number starting with +63 must be followed by a valid number (e.g. +639123456789 or 09123456789)."
            }
        }
        return null
    }
}

