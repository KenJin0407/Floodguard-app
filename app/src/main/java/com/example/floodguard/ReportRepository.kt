package com.example.floodguard

import android.content.Context
import java.util.UUID

enum class VoteType {
    STILL_FLOODED,
    WATER_SUBSIDED
}

data class VoteResult(
    val report: FloodReport,
    val autoResolved: Boolean,
    val message: String
)

data class FloodReport(
    val id: String = UUID.randomUUID().toString(),
    var location: String,
    var severity: String,
    var statusText: String,
    var notes: String,
    var timestamp: Long = System.currentTimeMillis(),
    var severityLevel: SeverityLevel,
    var photoUri: String? = null,
    var lat: Double = 14.6958,
    var lon: Double = 121.1207,
    var authorUsername: String = "",
    var reporterName: String = "",
    var reporterUsername: String = "",
    var stillFloodedCount: Int = 0,
    var waterSubsidedCount: Int = 0,
    var reportCount: Int = 1,
) {
    init {
        if (authorUsername.isBlank() && reporterUsername.isNotBlank()) {
            authorUsername = reporterUsername
        } else if (reporterUsername.isBlank() && authorUsername.isNotBlank()) {
            reporterUsername = authorUsername
        }
    }

    fun getFormattedTimeAgo(): String {
        val diffMs = System.currentTimeMillis() - timestamp
        val diffMins = diffMs / (1000 * 60)
        val diffHours = diffMins / 60
        val diffDays = diffHours / 24

        return when {
            diffMins < 1 -> "Updated just now"
            diffMins < 60 -> "Updated ${diffMins}m ago"
            diffHours < 24 -> "Updated ${diffHours}h ago"
            else -> "Updated ${diffDays}d ago"
        }
    }

    fun getTimeRemainingTtl(): String {
        val ttlMs = 2 * 60 * 60 * 1000L // 2-Hour Rolling Heartbeat TTL
        val remainingMs = (timestamp + ttlMs) - System.currentTimeMillis()
        if (remainingMs <= 0) return "Expires soon"
        val remMins = remainingMs / (1000 * 60)
        return if (remMins < 60) {
            "${remMins}m left"
        } else {
            val h = remMins / 60
            val m = remMins % 60
            "${h}h ${m}m left"
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

/**
 * Central repository managing flood incident reports.
 * Coordinates local SQLite storage, backend synchronization, report caching, and author mapping.
 */
object ReportRepository {

    private var cachedReports: MutableList<FloodReport>? = null

    @Suppress("unused")
    val reports: MutableList<FloodReport>
        get() = cachedReports ?: mutableListOf()

    @Synchronized
    fun loadReports(context: Context, onSynced: (() -> Unit)? = null): List<FloodReport> {
        val db = FloodGuardDatabase.getInstance(context)
        val loaded = db.getAllReports().toMutableList()

        if (loaded.isEmpty()) {
            val defaultSeedReports = listOf(
                FloodReport(
                    location = "Banaba, San Mateo",
                    severity = "Waist-Deep",
                    statusText = "Not Passable",
                    notes = "Severe road flooding near riverbank. Water waist deep, not passable to light vehicles.",
                    severityLevel = SeverityLevel.NOT_PASSABLE,
                    lat = 14.6750,
                    lon = 121.1080,
                    reporterName = "MDRRMO San Mateo",
                    authorUsername = "mdrrmo"
                ),
                FloodReport(
                    location = "Banaba Riverside, San Mateo",
                    severity = "Knee-Deep",
                    statusText = "Caution",
                    notes = "Flooding spilling over from Banaba road. Heavy vehicles only.",
                    severityLevel = SeverityLevel.HEAVY_VEHICLES_ONLY,
                    lat = 14.6770,
                    lon = 121.1095,
                    reporterName = "Barangay Banaba Desk",
                    authorUsername = "banaba_desk"
                ),
                FloodReport(
                    location = "Ampid 1 (Near Puregold)",
                    severity = "Knee-Deep",
                    statusText = "Caution",
                    notes = "Gutter overflowing into traffic lane. Passable to SUVs and trucks only.",
                    severityLevel = SeverityLevel.HEAVY_VEHICLES_ONLY,
                    lat = 14.6885,
                    lon = 121.1120,
                    reporterName = "Traffic Marshal Leo",
                    authorUsername = "traffic_leo"
                ),
                FloodReport(
                    location = "Ampid 2 (J.P. Rizal St.)",
                    severity = "Ankle-Deep",
                    statusText = "Cleared",
                    notes = "Water receded significantly. Passable to all vehicles.",
                    severityLevel = SeverityLevel.CLEARED,
                    lat = 14.6905,
                    lon = 121.1140,
                    reporterName = "Rizal Commuter Group",
                    authorUsername = "commuter_rizal"
                ),
                FloodReport(
                    location = "Dulong Bayan 2, San Mateo",
                    severity = "Ankle-Deep",
                    statusText = "Cleared",
                    notes = "Drainage clearing completed. Road fully open.",
                    severityLevel = SeverityLevel.CLEARED,
                    lat = 14.7120,
                    lon = 121.1280,
                    reporterName = "San Mateo Rescue 911",
                    authorUsername = "sm_rescue"
                )
            )
            for (seed in defaultSeedReports) {
                db.insertReport(seed)
                loaded.add(seed)
            }
        }

        val previousCache = synchronized(this) { cachedReports?.toList() ?: emptyList() }
        for (cached in previousCache) {
            if (loaded.none { it.id == cached.id }) {
                db.insertReport(cached)
                loaded.add(cached)
            }
        }

        if (UserSession.isLoggedIn(context)) {
            val currentUsername = UserSession.getUsername(context).trim()
            val currentFullName = UserSession.getUserName(context).trim()
            if (currentUsername.isNotEmpty()) {
                val reportsToIterate = loaded.toList()
                for (report in reportsToIterate) {
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
        synchronized(this) {
            cachedReports = loaded.toMutableList()
        }

        FloodGuardApiService.syncReportsFromBackend { remoteList ->
            var updated = false
            val localWorkingCopy = synchronized(this) { cachedReports?.toList() ?: loaded.toList() }.toMutableList()
            for (remoteReport in remoteList) {
                val existing = localWorkingCopy.find { it.id == remoteReport.id }
                if (existing == null) {
                    db.insertReport(remoteReport)
                    localWorkingCopy.add(0, remoteReport)
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
            localWorkingCopy.sortByDescending { it.timestamp }
            val shouldNotify = updated || localWorkingCopy.size != (synchronized(this) { cachedReports?.size })
            if (shouldNotify) {
                synchronized(this) {
                    cachedReports = localWorkingCopy
                }
                onSynced?.invoke()
            }
        }

        return synchronized(this) { loaded.toList() }
    }

    fun loadUserReports(context: Context, onSynced: (() -> Unit)? = null): List<FloodReport> {
        val currentUsername = UserSession.getUsername(context)
        val currentFullName = UserSession.getUserName(context)
        val allReports = loadReports(context, onSynced)
        return allReports.filter { it.isAuthoredBy(currentUsername, currentFullName) }
    }

    fun loadCommunityReports(context: Context, onSynced: (() -> Unit)? = null): List<FloodReport> {
        return loadReports(context, onSynced)
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

        val (resolvedLat, resolvedLon) = SmsAlertBroadcastManager.resolveCoordinatesForLocation(report.location)
        if (report.lat == 14.6958 && report.lon == 121.1207) {
            report.lat = resolvedLat
            report.lon = resolvedLon
        }

        val db = FloodGuardDatabase.getInstance(context)
        val allCurrentReports = (cachedReports?.toList() ?: db.getAllReports()).toMutableList()

        // 1. Rolling Heartbeat & Pin Consolidation: Check for an active report in the same flood area
        val existingNearby = allCurrentReports.find { candidate ->
            candidate.id != report.id &&
            candidate.severityLevel != SeverityLevel.CLEARED &&
            SmsAlertBroadcastManager.areReportsSimilar(report, candidate)
        }

        val effectiveReport: FloodReport
        if (existingNearby != null) {
            // Heartbeat extension: Refresh timestamp to now (+2 hours life)
            val now = System.currentTimeMillis()
            existingNearby.timestamp = now
            existingNearby.reportCount += 1
            existingNearby.stillFloodedCount += 1

            // Escalate severity if new report is more severe
            val isNewMoreSevere = when {
                report.severityLevel == SeverityLevel.NOT_PASSABLE -> true
                report.severityLevel == SeverityLevel.HEAVY_VEHICLES_ONLY && existingNearby.severityLevel == SeverityLevel.CLEARED -> true
                else -> false
            }
            if (isNewMoreSevere || existingNearby.severityLevel == SeverityLevel.CLEARED) {
                existingNearby.severity = report.severity
                existingNearby.severityLevel = report.severityLevel
                existingNearby.statusText = report.statusText
            }

            if (report.notes.isNotBlank()) {
                existingNearby.notes = report.notes
            }
            if (!report.photoUri.isNullOrBlank()) {
                existingNearby.photoUri = report.photoUri
            }

            db.updateReport(
                existingNearby.id,
                existingNearby.location,
                existingNearby.severity,
                existingNearby.statusText,
                existingNearby.notes,
                existingNearby.severityLevel,
                existingNearby.authorUsername,
                existingNearby.reporterName,
                existingNearby.photoUri,
                existingNearby.timestamp
            )
            db.updateReportVotes(
                existingNearby.id,
                existingNearby.stillFloodedCount,
                existingNearby.waterSubsidedCount,
                existingNearby.severityLevel.name,
                existingNearby.statusText,
                existingNearby.severity,
                existingNearby.timestamp
            )

            // Re-order so updated hazard is at the top
            allCurrentReports.removeAll { it.id == existingNearby.id }
            allCurrentReports.add(0, existingNearby)
            synchronized(this) {
                cachedReports = allCurrentReports
            }
            effectiveReport = existingNearby
        } else {
            db.insertReport(report)
            synchronized(this) {
                if (cachedReports == null) {
                    loadReports(context)
                } else {
                    val list = cachedReports?.toMutableList() ?: mutableListOf()
                    list.removeAll { it.id == report.id }
                    list.add(0, report)
                    cachedReports = list
                }
            }
            effectiveReport = report
        }

        val similarActive = SmsAlertBroadcastManager.findSimilarActiveReports(effectiveReport, cachedReports?.toList() ?: allCurrentReports)
        var localBroadcast: FloodBroadcast? = null
        if (similarActive.size >= 2) {
            SmsAlertBroadcastManager.evaluateAndBroadcastIfCorroborated(
                context.applicationContext,
                effectiveReport,
                cachedReports?.toList() ?: allCurrentReports
            ) { triggeredBroadcast ->
                localBroadcast = triggeredBroadcast
            }
        }

        FloodGuardApiService.submitReportToBackend(effectiveReport) { backendStatus ->
            if (backendStatus.rejectedByServer) {
                db.deleteReport(effectiveReport.id)
                synchronized(this) {
                    cachedReports?.removeAll { it.id == effectiveReport.id }
                }
                onSubmitted(backendStatus)
                return@submitReportToBackend
            }
            val effectiveBroadcast = backendStatus.smsBroadcast ?: localBroadcast
            if (backendStatus.smsBroadcast != null) {
                SmsAlertBroadcastManager.saveBroadcastLocally(context.applicationContext, backendStatus.smsBroadcast)
                SmsAlertBroadcastManager.markBroadcastSeen(context.applicationContext, backendStatus.smsBroadcast.id)
            }
            if (localBroadcast != null) {
                SmsAlertBroadcastManager.markBroadcastSeen(context.applicationContext, localBroadcast!!.id)
            }
            onSubmitted(
                ReportSubmissionStatus(
                    submittedToServer = backendStatus.submittedToServer,
                    pushAccepted = backendStatus.pushAccepted || similarActive.size >= 2,
                    smsBroadcastTriggered = backendStatus.smsBroadcastTriggered || similarActive.size >= 2,
                    smsBroadcast = effectiveBroadcast,
                )
            )
        }
    }

    fun removeReport(context: Context, id: String): Boolean {
        val currentUsername = UserSession.getUsername(context)
        val currentFullName = UserSession.getUserName(context)
        val db = FloodGuardDatabase.getInstance(context)
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
        val db = FloodGuardDatabase.getInstance(context)
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

    @Synchronized
    fun voteReport(
        context: Context,
        reportId: String,
        voteType: VoteType,
        username: String
    ): VoteResult? {
        val previousVote = UserVotesManager.getUserVote(context, reportId, username)
        val db = FloodGuardDatabase.getInstance(context)
        val all = loadReports(context)
        val report = all.find { it.id == reportId } ?: return null

        if (previousVote == voteType) {
            val voteName = if (voteType == VoteType.STILL_FLOODED) "Still Flooded" else "Water Subsided"
            return VoteResult(report, false, "You already marked this report as $voteName.")
        }

        // Adjust counts when switching vote
        if (previousVote == VoteType.STILL_FLOODED) {
            report.stillFloodedCount = maxOf(0, report.stillFloodedCount - 1)
        } else if (previousVote == VoteType.WATER_SUBSIDED) {
            report.waterSubsidedCount = maxOf(0, report.waterSubsidedCount - 1)
        }

        if (voteType == VoteType.STILL_FLOODED) {
            report.stillFloodedCount += 1
        } else {
            report.waterSubsidedCount += 1
        }

        UserVotesManager.recordVote(context, reportId, username, voteType)

        var autoResolved = false
        val msg: String

        // Auto-resolve check:
        // When water subsided votes reach 2+ and exceed still-flooded votes, or reach 3+
        if (voteType == VoteType.WATER_SUBSIDED &&
            ((report.waterSubsidedCount >= 2 && report.waterSubsidedCount > report.stillFloodedCount) || report.waterSubsidedCount >= 3)
        ) {
            if (report.severityLevel != SeverityLevel.CLEARED) {
                report.severityLevel = SeverityLevel.CLEARED
                report.statusText = "Water Subsided • Cleared"
                report.severity = "Cleared (Subsided)"
                autoResolved = true
                msg = "Consensus reached! Hazard at ${report.location} auto-resolved as Water Subsided."
            } else {
                msg = "Water subsided verified! (${report.waterSubsidedCount} community confirmations)"
            }
        } else if (voteType == VoteType.STILL_FLOODED) {
            // Rolling Heartbeat: Tapping "Still Flooded" resets the hazard timer by +2 hours
            report.timestamp = System.currentTimeMillis()

            if (report.severityLevel == SeverityLevel.CLEARED &&
                report.stillFloodedCount >= 2 && report.stillFloodedCount > report.waterSubsidedCount
            ) {
                report.severityLevel = SeverityLevel.NOT_PASSABLE
                report.statusText = "Not Passable (Re-activated)"
                report.severity = "Waist-Deep"
                msg = "Alert re-activated: multiple citizens confirmed road is still flooded."
            } else {
                msg = "Marked as Still Flooded (${report.stillFloodedCount} confirmations • Hazard extended by 2 hours)"
            }
        } else {
            val label = if (voteType == VoteType.STILL_FLOODED) "Still Flooded (${report.stillFloodedCount})" else "Water Subsided (${report.waterSubsidedCount})"
            msg = "Vote recorded: $label"
        }

        db.updateReportVotes(
            report.id,
            report.stillFloodedCount,
            report.waterSubsidedCount,
            report.severityLevel.name,
            report.statusText,
            report.severity,
            report.timestamp
        )

        return VoteResult(report, autoResolved, msg)
    }
}
