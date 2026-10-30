package com.example.floodguard

import android.content.Context

/**
 * Tracks community verification votes per user and incident report.
 * Prevents multiple conflicting votes from the same user session.
 */
object UserVotesManager {
    private const val PREFS_NAME = "floodguard_user_votes"

    fun getUserVote(context: Context, reportId: String, username: String): VoteType? {
        val effectiveUser = username.ifBlank { "guest_device" }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val voteStr = prefs.getString("${effectiveUser}_$reportId", null) ?: return null
        return try {
            VoteType.valueOf(voteStr)
        } catch (_: Exception) {
            null
        }
    }

    fun recordVote(context: Context, reportId: String, username: String, voteType: VoteType) {
        val effectiveUser = username.ifBlank { "guest_device" }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("${effectiveUser}_$reportId", voteType.name).apply()
    }
}
