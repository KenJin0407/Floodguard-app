package com.example.floodguard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object UserSession {
    private const val PREF_NAME = "floodguard_session"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_GENDER = "user_gender"
    private const val KEY_USER_CONTACT = "user_contact"
    private const val KEY_USER_EMAIL = "user_email"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isLoggedIn(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun setLoggedIn(
        context: Context,
        loggedIn: Boolean,
        userName: String = "Juan Dela Cruz",
        userGender: String = "Male",
        userContact: String = "+63 915 245 6879",
        userEmail: String = "juan@example.com",
    ) {
        getPrefs(context).edit {
            putBoolean(KEY_IS_LOGGED_IN, loggedIn)
            putString(KEY_USER_NAME, userName)
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
}
