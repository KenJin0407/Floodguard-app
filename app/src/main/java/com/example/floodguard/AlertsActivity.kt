package com.example.floodguard

import android.content.Intent
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

    private lateinit var alertsContainer: LinearLayout
    private lateinit var callHotlineButton: LinearLayout
    private lateinit var addReportButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alerts)

        initViews()
        setupListeners()
        populateAlerts()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        FloatingAlertManager.onActivityResumed(this)
        populateAlerts()
    }

    override fun onPause() {
        super.onPause()
        FloatingAlertManager.onActivityPaused(this)
    }

    private fun initViews() {
        alertsContainer = findViewById(R.id.alertsContainer)
        callHotlineButton = findViewById(R.id.callHotlineButton)
        addReportButton = findViewById(R.id.addReportButton)
    }

    private fun setupListeners() {
        callHotlineButton.setOnClickListener {
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:0289111406"))
                startActivity(dialIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open dialer", Toast.LENGTH_SHORT).show()
            }
        }

        addReportButton.setOnClickListener {
            val intent = Intent(this, ReportFloodActivity::class.java)
            startActivity(intent)
        }
    }

    private fun populateAlerts() {
        val currentUsername = UserSession.getUsername(this).trim()
        val currentFullName = UserSession.getUserName(this).trim()
        val reports = ReportRepository.loadReports(this) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    populateAlerts()
                }
            }
        }

        alertsContainer.removeAllViews()

        if (reports.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No flood reports available."
                setTextColor(resources.getColor(android.R.color.darker_gray, theme))
                textSize = 14f
                val paddingPx = (16 * resources.displayMetrics.density).toInt()
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            }
            alertsContainer.addView(emptyView)
            return
        }

        for (report in reports) {
            val similarReports = SmsAlertBroadcastManager.findSimilarReportsInVicinity(this, report)
            val cardView = RelativeLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, (12 * resources.displayMetrics.density).toInt())
                }
                background = ContextCompat.getDrawable(
                    this@AlertsActivity,
                    when (report.severityLevel) {
                        SeverityLevel.NOT_PASSABLE -> R.drawable.alert_card_red
                        SeverityLevel.HEAVY_VEHICLES -> R.drawable.alert_card_yellow
                        SeverityLevel.LIGHT_VEHICLES -> R.drawable.alert_card_yellow
                        SeverityLevel.PASSABLE -> R.drawable.alert_card_green
                    }
                )
                val padPx = (16 * resources.displayMetrics.density).toInt()
                setPadding(padPx, padPx, padPx, padPx)
            }

            val timeText = TextView(this).apply {
                id = View.generateViewId()
                layoutParams = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_TOP)
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                }
                text = report.getFormattedTimeAgo()
                setTextColor(resources.getColor(android.R.color.darker_gray, theme))
                textSize = 11f
            }

            val textLayout = LinearLayout(this).apply {
                layoutParams = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    addRule(RelativeLayout.START_OF, timeText.id)
                    addRule(RelativeLayout.ALIGN_PARENT_START)
                }
                orientation = LinearLayout.VERTICAL
            }

            val titleText = TextView(this).apply {
                text = "${report.location} (${report.severity})"
                setTextColor(resources.getColor(android.R.color.black, theme))
                textSize = 15f
                paint.isFakeBoldText = true
            }

            val reporterLabel = report.getDisplayReporter(currentUsername, currentFullName)
            val subtitleText = TextView(this).apply {
                text = "${report.notes}\nReported by: $reporterLabel"
                setTextColor(resources.getColor(android.R.color.darker_gray, theme))
                textSize = 12f
                val topMarginPx = (4 * resources.displayMetrics.density).toInt()
                setPadding(0, topMarginPx, 0, 0)
            }

            textLayout.addView(titleText)
            textLayout.addView(subtitleText)

            if (similarReports.size >= 2) {
                val smsBadgeBtn = TextView(this).apply {
                    text = "🚨 SMS Broadcast Active (${similarReports.size} similar reports near area) • View Alert"
                    setTextColor(android.graphics.Color.parseColor("#DC2626"))
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
                textLayout.addView(smsBadgeBtn)
            }

            if (!report.photoUri.isNullOrBlank()) {
                val photoBtn = TextView(this).apply {
                    text = "View Flood Photo"
                    setTextColor(resources.getColor(android.R.color.holo_blue_dark, theme))
                    textSize = 12f
                    paint.isFakeBoldText = true
                    val topMarginPx = (6 * resources.displayMetrics.density).toInt()
                    setPadding(0, topMarginPx, 0, 0)
                    setOnClickListener {
                        ReportPhotoHelper.showFloodPhotoDialog(this@AlertsActivity, report.photoUri!!, report.location)
                    }
                }
                textLayout.addView(photoBtn)
            }

            cardView.addView(textLayout)
            cardView.addView(timeText)

            alertsContainer.addView(cardView)
        }
    }

    private fun setupBottomNavigation() {
        findViewById<LinearLayout>(R.id.navHome)?.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish()
        }
        findViewById<LinearLayout>(R.id.navMap)?.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            startActivity(intent)
            finish()
        }
        findViewById<LinearLayout>(R.id.navAlerts)?.setOnClickListener {
            // Already on Alerts screen
        }
        findViewById<LinearLayout>(R.id.navProfile)?.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
