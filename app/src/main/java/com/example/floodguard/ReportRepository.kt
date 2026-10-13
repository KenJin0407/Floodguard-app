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
                    existing.notes != remoteReport.notes) {
                    db.updateReport(
                        remoteReport.id,
                        remoteReport.location,
                        remoteReport.severity,
                        remoteReport.statusText,
                        remoteReport.notes,
                        remoteReport.severityLevel
                    )
                    existing.location = remoteReport.location
                    existing.severity = remoteReport.severity
                    existing.notes = remoteReport.notes
                    existing.statusText = remoteReport.statusText
                    existing.severityLevel = remoteReport.severityLevel
                    updated = true
                }
            }
            if (updated || loaded.size != cachedReports?.size) {
                cachedReports = loaded
                onSynced?.invoke()
            }
        }

        return loaded
    }

    fun addReport(context: Context, report: FloodReport) {
        val db = FloodGuardDatabase(context)
        db.insertReport(report)
        if (cachedReports == null) {
            loadReports(context)
        } else {
            cachedReports?.add(0, report)
        }

        FloodGuardApiService.submitReportToBackend(report)
    }

    fun removeReport(context: Context, id: String) {
        val db = FloodGuardDatabase(context)
        db.deleteReport(id)
        cachedReports?.removeAll { it.id == id }

        FloodGuardApiService.deleteReportInBackend(id)
    }

    fun updateReport(context: Context, id: String, location: String, severity: String, notes: String) {
        val (status, level) = when (severity) {
            "Impassable", "Waist-Deep" -> Pair("Not Passable", SeverityLevel.NOT_PASSABLE)
            "Knee-Deep" -> Pair("Heavy vehicles only", SeverityLevel.HEAVY_VEHICLES_ONLY)
            else -> Pair("Cleared", SeverityLevel.CLEARED)
        }

        val db = FloodGuardDatabase(context)
        db.updateReport(id, location, severity, status, notes, level)

        val report = cachedReports?.find { it.id == id }
        if (report != null) {
            report.location = location
            report.severity = severity
            report.notes = notes
            report.statusText = status
            report.severityLevel = level
        }

        FloodGuardApiService.updateReportInBackend(id, location, severity, notes)
    }
}
