package com.example.floodguard

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Activity for displaying real-time community flood alerts,
 * emergency hotline shortcuts, and severity-colored warnings.
 */
class AlertsActivity : ComponentActivity() {

    enum class AlertFilter {
        ALL, NOT_PASSABLE, CAUTION, CLEARED
    }
    private var currentFilter: AlertFilter = AlertFilter.ALL

    private lateinit var alertsContainer: LinearLayout
    private lateinit var callHotlineButton: LinearLayout
    private lateinit var addReportButton: TextView

    private lateinit var chipAlertsAll: TextView
    private lateinit var chipAlertsNotPassable: TextView
    private lateinit var chipAlertsCaution: TextView
    private lateinit var chipAlertsCleared: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alerts)

        initViews()
        setupListeners()
        populateAlerts()
        setupBottomNavigation()
    }

    private var isFirstResume = true

    override fun onResume() {
        super.onResume()
        FloatingAlertManager.onActivityResumed(this)
        if (!isFirstResume) {
            populateAlerts()
        }
        isFirstResume = false
    }

    override fun onPause() {
        super.onPause()
        FloatingAlertManager.onActivityPaused(this)
    }

    private fun initViews() {
        alertsContainer = findViewById(R.id.alertsContainer)
        callHotlineButton = findViewById(R.id.callHotlineButton)
        addReportButton = findViewById(R.id.addReportButton)

        chipAlertsAll = findViewById(R.id.chipAlertsAll)
        chipAlertsNotPassable = findViewById(R.id.chipAlertsNotPassable)
        chipAlertsCaution = findViewById(R.id.chipAlertsCaution)
        chipAlertsCleared = findViewById(R.id.chipAlertsCleared)

        chipAlertsAll.setOnClickListener { setAlertsFilter(AlertFilter.ALL) }
        chipAlertsNotPassable.setOnClickListener { setAlertsFilter(AlertFilter.NOT_PASSABLE) }
        chipAlertsCaution.setOnClickListener { setAlertsFilter(AlertFilter.CAUTION) }
        chipAlertsCleared.setOnClickListener { setAlertsFilter(AlertFilter.CLEARED) }
    }

    private fun setAlertsFilter(filter: AlertFilter) {
        currentFilter = filter
        updateAlertsChipStyles()
        populateAlerts()
    }

    private fun updateAlertsChipStyles() {
        val selectedBg = R.drawable.bg_chip_selected
        val unselectedBg = R.drawable.bg_chip_unselected
        val selectedColor = Color.WHITE
        val unselectedColor = Color.parseColor("#475569")

        chipAlertsAll.setBackgroundResource(if (currentFilter == AlertFilter.ALL) selectedBg else unselectedBg)
        chipAlertsAll.setTextColor(if (currentFilter == AlertFilter.ALL) selectedColor else unselectedColor)

        chipAlertsNotPassable.setBackgroundResource(if (currentFilter == AlertFilter.NOT_PASSABLE) selectedBg else unselectedBg)
        chipAlertsNotPassable.setTextColor(if (currentFilter == AlertFilter.NOT_PASSABLE) selectedColor else unselectedColor)

        chipAlertsCaution.setBackgroundResource(if (currentFilter == AlertFilter.CAUTION) selectedBg else unselectedBg)
        chipAlertsCaution.setTextColor(if (currentFilter == AlertFilter.CAUTION) selectedColor else unselectedColor)

        chipAlertsCleared.setBackgroundResource(if (currentFilter == AlertFilter.CLEARED) selectedBg else unselectedBg)
        chipAlertsCleared.setTextColor(if (currentFilter == AlertFilter.CLEARED) selectedColor else unselectedColor)
    }

    private fun setupListeners() {
        callHotlineButton.setOnClickListener {
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:0289111406"))
                startActivity(dialIntent)
            } catch (_: Exception) {
                Toast.makeText(this, "Calling NDRRMC Hotline: (02) 8911-1406", Toast.LENGTH_LONG).show()
            }
        }

        addReportButton.setOnClickListener {
            val intent = Intent(this, ReportFloodActivity::class.java)
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.logoLayout)?.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish()
        }

        findViewById<ImageView>(R.id.menuIcon)?.setOnClickListener { view ->
            NavigationMenuHelper.showMenu(this, view)
        }
    }

    private fun populateAlerts() {
        alertsContainer.removeAllViews()

        val currentUsername = UserSession.getUsername(this)
        val currentFullName = UserSession.getUserName(this)

        val allReports = ReportRepository.loadReports(this) {
            runOnUiThread { populateAlerts() }
        }.toList()

        // Update live counts on filter chips
        val notPassableCount = allReports.count { it.severityLevel == SeverityLevel.NOT_PASSABLE }
        val cautionCount = allReports.count { it.severityLevel == SeverityLevel.HEAVY_VEHICLES_ONLY }
        val clearedCount = allReports.count { it.severityLevel == SeverityLevel.CLEARED }

        chipAlertsAll.text = "All (${allReports.size})"
        chipAlertsNotPassable.text = "🔴 Not Passable ($notPassableCount)"
        chipAlertsCaution.text = "🟡 Caution ($cautionCount)"
        chipAlertsCleared.text = "🟢 Cleared ($clearedCount)"

        val reportsList = allReports.filter { report ->
            when (currentFilter) {
                AlertFilter.ALL -> true
                AlertFilter.NOT_PASSABLE -> report.severityLevel == SeverityLevel.NOT_PASSABLE
                AlertFilter.CAUTION -> report.severityLevel == SeverityLevel.HEAVY_VEHICLES_ONLY
                AlertFilter.CLEARED -> report.severityLevel == SeverityLevel.CLEARED
            }
        }

        val count = reportsList.size
        findViewById<TextView>(R.id.tvAlertsHeaderTitle)?.text = "Submitted Flood Alerts ($count)"

        if (reportsList.isEmpty()) {
            val emptyText = TextView(this).apply {
                val filterDesc = when (currentFilter) {
                    AlertFilter.ALL -> "community flood alerts"
                    AlertFilter.NOT_PASSABLE -> "Not Passable alerts"
                    AlertFilter.CAUTION -> "Caution alerts"
                    AlertFilter.CLEARED -> "Cleared alerts"
                }
                text = "No $filterDesc at this moment."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(16, 48, 16, 48)
            }
            alertsContainer.addView(emptyText)
            return
        }

        for (report in reportsList) {
            val similarReports = SmsAlertBroadcastManager.findSimilarActiveReports(report, allReports)
            val cardView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (14 * resources.displayMetrics.density).toInt()
                }
                layoutParams = params
                val paddingPx = (15 * resources.displayMetrics.density).toInt()
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)

                val bgDrawable = when (report.severityLevel) {
                    SeverityLevel.NOT_PASSABLE -> R.drawable.card_alert_red
                    SeverityLevel.HEAVY_VEHICLES_ONLY -> R.drawable.card_alert_yellow
                    SeverityLevel.CLEARED -> R.drawable.card_alert_green
                }
                setBackgroundResource(bgDrawable)
            }

            // 1. Header Row: Status Badge (Left) + Time Ago (Right)
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams = params
            }

            val (badgeBg, badgeText, badgeColor) = when (report.severityLevel) {
                SeverityLevel.NOT_PASSABLE -> Triple(R.drawable.badge_status_red, "⛔ NOT PASSABLE", Color.parseColor("#B91C1C"))
                SeverityLevel.HEAVY_VEHICLES_ONLY -> Triple(R.drawable.badge_status_yellow, "⚠️ CAUTION", Color.parseColor("#B45309"))
                SeverityLevel.CLEARED -> Triple(R.drawable.badge_status_green, "🟢 WATER SUBSIDED • CLEARED", Color.parseColor("#15803D"))
            }

            val badgeView = TextView(this).apply {
                text = badgeText
                setTextColor(badgeColor)
                textSize = 10.5f
                paint.isFakeBoldText = true
                setBackgroundResource(badgeBg)
                val padH = (8 * resources.displayMetrics.density).toInt()
                val padV = (3 * resources.displayMetrics.density).toInt()
                setPadding(padH, padV, padH, padV)
            }

            val spacerHeader = View(this).apply {
                val params = LinearLayout.LayoutParams(0, 0, 1f)
                layoutParams = params
            }

            val timeText = TextView(this).apply {
                val timeAgo = report.getFormattedTimeAgo()
                val ttl = report.getTimeRemainingTtl()
                text = "⏱️ $timeAgo • $ttl"
                setTextColor(Color.parseColor("#64748B"))
                textSize = 11.5f
            }

            headerRow.addView(badgeView)
            headerRow.addView(spacerHeader)
            headerRow.addView(timeText)
            cardView.addView(headerRow)

            // 2. Location Title
            val titleText = TextView(this).apply {
                text = "📍 ${report.location}"
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 15f
                paint.isFakeBoldText = true
                val topMarginPx = (10 * resources.displayMetrics.density).toInt()
                setPadding(0, topMarginPx, 0, 0)
            }
            cardView.addView(titleText)

            // 3. Water Depth & Notes
            val detailText = TextView(this).apply {
                val depthLabel = if (report.severity.isNotBlank()) "Depth: ${report.severity}" else ""
                val notesText = report.notes.trim()
                val fullText = when {
                    depthLabel.isNotEmpty() && notesText.isNotEmpty() -> "$depthLabel • $notesText"
                    depthLabel.isNotEmpty() -> depthLabel
                    else -> notesText
                }
                text = fullText
                setTextColor(Color.parseColor("#334155"))
                textSize = 13f
                val topMarginPx = (4 * resources.displayMetrics.density).toInt()
                setPadding(0, topMarginPx, 0, 0)
            }
            cardView.addView(detailText)

            // 4. Reporter Meta
            val reporterLabel = report.getDisplayReporter(currentUsername, currentFullName)
            val reporterMeta = TextView(this).apply {
                text = "👤 Reported by $reporterLabel"
                setTextColor(Color.parseColor("#64748B"))
                textSize = 11.5f
                val topMarginPx = (4 * resources.displayMetrics.density).toInt()
                setPadding(0, topMarginPx, 0, 0)
            }
            cardView.addView(reporterMeta)

            // 5. SMS Broadcast Banner (if corroborated by 2+ reports)
            if (similarReports.size >= 2) {
                val smsBadgeBtn = TextView(this).apply {
                    text = "🚨 SMS Broadcast Active (${similarReports.size} similar reports near area) • View Alert"
                    setTextColor(Color.parseColor("#DC2626"))
                    textSize = 11.5f
                    paint.isFakeBoldText = true
                    val topMarginPx = (6 * resources.displayMetrics.density).toInt()
                    setPadding(0, topMarginPx, 0, 0)
                    setOnClickListener {
                        val (lat, lon) = SmsAlertBroadcastManager.resolveCoordinatesForLocation(report.location)
                        val phones = SmsAlertBroadcastManager.collectNearbyRecipientPhones(this@AlertsActivity)
                        val reporters = similarReports
                            .map { it.reporterName.ifBlank { it.authorUsername.ifBlank { "Community Reporter" } } }
                            .distinct()
                            .take(3)
                            .joinToString(", ")
                        val smsMsg = "[FLOODGUARD SMS ALERT] URGENT: ${similarReports.size} similar flood reports verified near ${report.location}. Water Level: ${report.severity} (${report.statusText}). ${report.notes}. Avoid flooded roads and move to safer ground."
                        val broadcast = FloodBroadcast(
                            id = "sms_bcast_${report.id}_${similarReports.size}",
                            location = report.location,
                            severity = report.severity,
                            notes = report.notes,
                            reporterName = reporters,
                            timestamp = report.timestamp,
                            authorUsername = report.authorUsername,
                            statusText = report.statusText,
                            similarReportCount = similarReports.size,
                            lat = lat,
                            lon = lon,
                            smsMessage = smsMsg,
                            smsRecipientsCount = phones.size,
                            recipientPhones = phones
                        )
                        FloatingAlertManager.showSmsWarningPopUpDialog(this@AlertsActivity, broadcast)
                    }
                }
                cardView.addView(smsBadgeBtn)
            }

            // 6. Attached Photo (if any)
            if (!report.photoUri.isNullOrBlank()) {
                val photoBtn = TextView(this).apply {
                    text = "📷 View Attached Flood Photo"
                    setTextColor(Color.parseColor("#0284C7"))
                    textSize = 12f
                    paint.isFakeBoldText = true
                    val topMarginPx = (6 * resources.displayMetrics.density).toInt()
                    setPadding(0, topMarginPx, 0, 0)
                    setOnClickListener {
                        ReportPhotoHelper.showFloodPhotoDialog(this@AlertsActivity, report.photoUri!!, report.location)
                    }
                }
                cardView.addView(photoBtn)
            }

            // Corroboration Badge (if corroborated by multiple citizen reports)
            if (report.reportCount > 1) {
                val corroborationBadge = TextView(this).apply {
                    text = "👥 Corroborated by ${report.reportCount} citizen reports"
                    setTextColor(Color.parseColor("#0369A1"))
                    textSize = 11.5f
                    paint.isFakeBoldText = true
                    val topMarginPx = (6 * resources.displayMetrics.density).toInt()
                    setPadding(0, topMarginPx, 0, 0)
                }
                cardView.addView(corroborationBadge)
            }

            alertsContainer.addView(cardView)
        }
    }

    private fun setupBottomNavigation() {
        findViewById<LinearLayout>(R.id.logoLayout)?.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish()
        }
        findViewById<ImageView>(R.id.menuIcon)?.setOnClickListener { view ->
            NavigationMenuHelper.showMenu(this, view)
        }
        EdgeNavigationHelper.attachEdgeNavigation(this, EdgeNavigationHelper.NavTab.ALERTS)
    }
}
