package com.example.floodguard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object UserSession {
    private const val PREF_NAME = "floodguard_session"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
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

    fun setLoggedIn(
        context: Context,
        loggedIn: Boolean,
        username: String = "juan",
        userName: String = "Juan Dela Cruz",
        userGender: String = "Male",
        userContact: String = "+63 915 245 6879",
    ) {
        val cleanUser = username.trim()
        val cleanName = userName.trim()
        val userKey = cleanUser.lowercase()
        getPrefs(context).edit {
            putBoolean(KEY_IS_LOGGED_IN, loggedIn)
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
}
