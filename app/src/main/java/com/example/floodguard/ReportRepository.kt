package com.example.floodguard

import android.content.Context
import java.util.UUID

data class FloodReport(
    val id: String = UUID.randomUUID().toString(),
    var location: String,
    var severity: String,
    var statusText: String,
    var notes: String,
    val timestamp: Long = System.currentTimeMillis(),
    var severityLevel: SeverityLevel,
    var photoUri: String? = null,
    var lat: Double = 14.6958,
    var lon: Double = 121.1207,
    var authorUsername: String = "",
    var reporterName: String = "",
) {
    fun getFormattedTimeAgo(): String {
        val diffMs = System.currentTimeMillis() - timestamp
        val diffMins = diffMs / (1000 * 60)
        val diffHours = diffMins / 60
        val diffDays = diffHours / 24

        return when {
            diffMins < 1 -> "Submitted just now"
            diffMins < 60 -> "Submitted $diffMins ${if (diffMins == 1L) "minute" else "minutes"} ago"
            diffHours < 24 -> "Submitted $diffHours ${if (diffHours == 1L) "hour" else "hours"} ago"
            else -> "Submitted $diffDays ${if (diffDays == 1L) "day" else "days"} ago"
        }
    }

    fun isAuthoredBy(username: String, fullName: String = ""): Boolean {
        val cleanUser = username.trim()
        val cleanName = fullName.trim()
        if (authorUsername.isNotBlank()) {
            if (cleanUser.isNotEmpty() && authorUsername.equals(cleanUser, ignoreCase = true)) {
                return true
            }
            if (cleanName.isNotEmpty() && authorUsername.equals(cleanName, ignoreCase = true)) {
                return true
            }
            if (authorUsername.equals(reporterName, ignoreCase = true) &&
                reporterName.isNotBlank() &&
                ((cleanName.isNotEmpty() && reporterName.equals(cleanName, ignoreCase = true)) ||
                    (cleanUser.isNotEmpty() && reporterName.equals(cleanUser, ignoreCase = true)))
            ) {
                return true
            }
            return false
        }
        if (reporterName.isNotBlank()) {
            return (cleanName.isNotEmpty() && reporterName.equals(cleanName, ignoreCase = true)) ||
                (cleanUser.isNotEmpty() && reporterName.equals(cleanUser, ignoreCase = true))
        }
        return false
    }

    fun getDisplayReporter(currentUsername: String, currentFullName: String = ""): String {
        val label = when {
            reporterName.isNotBlank() -> reporterName
            authorUsername.isNotBlank() -> authorUsername
            else -> "Community Reporter"
        }
        return if (isAuthoredBy(currentUsername, currentFullName)) {
            "$label (You)"
        } else {
            label
        }
    }
}

enum class SeverityLevel {
    NOT_PASSABLE,
    HEAVY_VEHICLES_ONLY,
    CLEARED,
}

object ReportRepository {

    private var cachedReports: MutableList<FloodReport>? = null

    @Suppress("unused")
    val reports: MutableList<FloodReport>
        get() = cachedReports ?: mutableListOf()

    fun loadReports(context: Context, onSynced: (() -> Unit)? = null): List<FloodReport> {
        val db = FloodGuardDatabase(context)
        val loaded = db.getAllReports().toMutableList()

        val previousCache = cachedReports
        if (previousCache != null) {
            for (cached in previousCache) {
                if (loaded.none { it.id == cached.id }) {
                    db.insertReport(cached)
                    loaded.add(cached)
                }
            }
        }

        if (UserSession.isLoggedIn(context)) {
            val currentUsername = UserSession.getUsername(context).trim()
            val currentFullName = UserSession.getUserName(context).trim()
            if (currentUsername.isNotEmpty()) {
                for (report in loaded) {
                    if (report.isAuthoredBy(currentUsername, currentFullName) &&
                        !report.authorUsername.equals(currentUsername, ignoreCase = true)
                    ) {
                        report.authorUsername = currentUsername
                        if (report.reporterName.isBlank() && currentFullName.isNotEmpty()) {
                            report.reporterName = currentFullName
                        }
                        db.updateReport(
                            report.id,
                            report.location,
                            report.severity,
                            report.statusText,
                            report.notes,
                            report.severityLevel,
                            report.authorUsername,
                            report.reporterName,
                        )
                    }
                }
            }
        }

        loaded.sortByDescending { it.timestamp }
        cachedReports = loaded

        FloodGuardApiService.syncReportsFromBackend { remoteList ->
            var updated = false
            for (remoteReport in remoteList) {
                val existing = loaded.find { it.id == remoteReport.id }
                if (existing == null) {
                    db.insertReport(remoteReport)
                    loaded.add(0, remoteReport)
                    updated = true
                } else if (existing.location != remoteReport.location ||
                    existing.severity != remoteReport.severity ||
                    existing.notes != remoteReport.notes ||
                    (remoteReport.authorUsername.isNotBlank() && existing.authorUsername != remoteReport.authorUsername) ||
                    (remoteReport.reporterName.isNotBlank() && existing.reporterName != remoteReport.reporterName) ||
                    (!remoteReport.photoUri.isNullOrBlank() && existing.photoUri != remoteReport.photoUri)
                ) {
                    val mergedAuthor = remoteReport.authorUsername.ifBlank { existing.authorUsername }
                    val mergedReporter = remoteReport.reporterName.ifBlank { existing.reporterName }
                    val mergedPhoto = if (!remoteReport.photoUri.isNullOrBlank()) remoteReport.photoUri else existing.photoUri
                    db.updateReport(
                        remoteReport.id,
                        remoteReport.location,
                        remoteReport.severity,
                        remoteReport.statusText,
                        remoteReport.notes,
                        remoteReport.severityLevel,
                        mergedAuthor,
                        mergedReporter,
                        mergedPhoto,
                    )
                    existing.location = remoteReport.location
                    existing.severity = remoteReport.severity
                    existing.notes = remoteReport.notes
                    existing.statusText = remoteReport.statusText
                    existing.severityLevel = remoteReport.severityLevel
                    existing.authorUsername = mergedAuthor
                    existing.reporterName = mergedReporter
                    existing.photoUri = mergedPhoto
                    updated = true
                }
            }
            loaded.sortByDescending { it.timestamp }
            if (updated || loaded.size != cachedReports?.size) {
                cachedReports = loaded
                onSynced?.invoke()
            }
        }

        return loaded
    }

    fun loadUserReports(context: Context, onSynced: (() -> Unit)? = null): List<FloodReport> {
        val currentUsername = UserSession.getUsername(context)
        val currentFullName = UserSession.getUserName(context)
        val allReports = loadReports(context, onSynced)
        return allReports.filter { it.isAuthoredBy(currentUsername, currentFullName) }
    }

    fun loadCommunityReports(context: Context, onSynced: (() -> Unit)? = null): List<FloodReport> {
        val currentUsername = UserSession.getUsername(context)
        val currentFullName = UserSession.getUserName(context)
        val allReports = loadReports(context, onSynced)
        return allReports.filter { !it.isAuthoredBy(currentUsername, currentFullName) }
    }

    fun addReport(
        context: Context,
        report: FloodReport,
        onSubmitted: (ReportSubmissionStatus) -> Unit = {},
    ) {
        if (report.authorUsername.isBlank()) {
            report.authorUsername = UserSession.getUsername(context)
        }
        if (report.reporterName.isBlank()) {
            report.reporterName = UserSession.getUserName(context)
        }

        val db = FloodGuardDatabase(context)
        db.insertReport(report)
        if (cachedReports == null) {
            loadReports(context)
        } else {
            cachedReports?.removeAll { it.id == report.id }
            cachedReports?.add(0, report)
        }

        FloodGuardApiService.submitReportToBackend(report, onSubmitted)
    }

    fun removeReport(context: Context, id: String): Boolean {
        val currentUsername = UserSession.getUsername(context)
        val currentFullName = UserSession.getUserName(context)
        val db = FloodGuardDatabase(context)
        val existing = (cachedReports ?: db.getAllReports()).find { it.id == id }
        if (existing != null && !existing.isAuthoredBy(currentUsername, currentFullName)) {
            return false
        }

        db.deleteReport(id)
        cachedReports?.removeAll { it.id == id }

        FloodGuardApiService.deleteReportInBackend(id, currentUsername)
        return true
    }

    fun updateReport(context: Context, id: String, location: String, severity: String, notes: String): Boolean {
        val currentUsername = UserSession.getUsername(context)
        val currentFullName = UserSession.getUserName(context)
        val db = FloodGuardDatabase(context)
        val existing = (cachedReports ?: db.getAllReports()).find { it.id == id }
        if (existing != null && !existing.isAuthoredBy(currentUsername, currentFullName)) {
            return false
        }

        val (status, level) = when (severity) {
            "Impassable", "Waist-Deep" -> Pair("Not Passable", SeverityLevel.NOT_PASSABLE)
            "Knee-Deep" -> Pair("Heavy vehicles only", SeverityLevel.HEAVY_VEHICLES_ONLY)
            else -> Pair("Cleared", SeverityLevel.CLEARED)
        }

        db.updateReport(id, location, severity, status, notes, level)

        val report = cachedReports?.find { it.id == id }
        if (report != null) {
            report.location = location
            report.severity = severity
            report.notes = notes
            report.statusText = status
            report.severityLevel = level
        }

        FloodGuardApiService.updateReportInBackend(id, location, severity, notes, currentUsername)
        return true
    }
}
