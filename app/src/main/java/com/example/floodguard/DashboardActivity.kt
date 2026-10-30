package com.example.floodguard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.LayoutInflater
import android.widget.Button
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.location.Geocoder
import android.location.LocationManager
import android.webkit.JavascriptInterface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

@SuppressLint("SetTextI18n")
class DashboardActivity : ComponentActivity() {

    enum class AlertFilter {
        ALL, NOT_PASSABLE, CAUTION, CLEARED
    }
    private var currentFilter: AlertFilter = AlertFilter.ALL

    private lateinit var mapWebView: WebView
    private var weatherAnimationView: WeatherAnimationView? = null
    private lateinit var mainTempText: TextView
    private lateinit var weatherDescriptionText: TextView
    private lateinit var rainMeterText: TextView
    private lateinit var searchLocationInput: EditText
    private lateinit var clearSearchIcon: ImageView

    private lateinit var hourlyTemp1: TextView
    private lateinit var hourlyTemp2: TextView
    private lateinit var hourlyTemp3: TextView
    private lateinit var hourlyTemp4: TextView
    private lateinit var hourlyTemp5: TextView
    private lateinit var hourlyTemp6: TextView

    private lateinit var chipDashboardAll: TextView
    private lateinit var chipDashboardNotPassable: TextView
    private lateinit var chipDashboardCaution: TextView
    private lateinit var chipDashboardCleared: TextView
    private lateinit var tvDashboardAlertsCountBadge: TextView

    private var cardDashboardActiveRoute: LinearLayout? = null
    private var tvDashboardRouteBadge: TextView? = null
    private var tvDashboardRouteEtaDist: TextView? = null
    private var tvDashboardRouteTitle: TextView? = null
    private var tvDashboardRouteHazardAlert: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        FloodGuardPushNotifications.initialize(this)
        initViews()
        setupRoadMap()
        setupBottomNavigation()
        fetchRealTimeWeather()
        populateCommunityReports()
    }

    override fun onResume() {
        super.onResume()
        FloatingAlertManager.onActivityResumed(this)
        weatherAnimationView?.resumeAnimation()
        populateCommunityReports(searchLocationInput.text.toString().trim())
        syncRoadMapWithState()
    }

    override fun onPause() {
        super.onPause()
        FloatingAlertManager.onActivityPaused(this)
        weatherAnimationView?.pauseAnimation()
    }

    private fun initViews() {
        mapWebView = findViewById(R.id.mapWebView)
        weatherAnimationView = findViewById(R.id.weatherAnimationView)
        mainTempText = findViewById(R.id.mainTempText)
        weatherDescriptionText = findViewById(R.id.weatherDescriptionText)
        rainMeterText = findViewById(R.id.rainMeterText)
        searchLocationInput = findViewById(R.id.searchLocationInput)
        clearSearchIcon = findViewById(R.id.clearSearchIcon)

        cardDashboardActiveRoute = findViewById(R.id.cardDashboardActiveRoute)
        tvDashboardRouteBadge = findViewById(R.id.tvDashboardRouteBadge)
        tvDashboardRouteEtaDist = findViewById(R.id.tvDashboardRouteEtaDist)
        tvDashboardRouteTitle = findViewById(R.id.tvDashboardRouteTitle)
        tvDashboardRouteHazardAlert = findViewById(R.id.tvDashboardRouteHazardAlert)

        cardDashboardActiveRoute?.setOnClickListener {
            openFullMapFromDashboard()
        }
        findViewById<View>(R.id.dashboardMapContainer)?.setOnClickListener {
            openFullMapFromDashboard()
        }

        hourlyTemp1 = findViewById(R.id.hourlyTemp1)
        hourlyTemp2 = findViewById(R.id.hourlyTemp2)
        hourlyTemp3 = findViewById(R.id.hourlyTemp3)
        hourlyTemp4 = findViewById(R.id.hourlyTemp4)
        hourlyTemp5 = findViewById(R.id.hourlyTemp5)
        hourlyTemp6 = findViewById(R.id.hourlyTemp6)

        clearSearchIcon.setOnClickListener {
            searchLocationInput.text.clear()
            populateCommunityReports("")
            searchAndLocateMapArea("San Mateo Town Center")
        }

        searchLocationInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString().orEmpty().trim()
                populateCommunityReports(q)
                if (q.length >= 3) {
                    searchAndLocateMapArea(q)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        searchLocationInput.setOnEditorActionListener { _, _, _ ->
            val q = searchLocationInput.text.toString().trim()
            if (q.isNotEmpty()) {
                searchAndLocateMapArea(q)
            }
            true
        }

        findViewById<TextView>(R.id.btnViewAllAlerts)?.setOnClickListener {
            val intent = Intent(this, AlertsActivity::class.java)
            startActivity(intent)
        }

        chipDashboardAll = findViewById(R.id.chipDashboardAll)
        chipDashboardNotPassable = findViewById(R.id.chipDashboardNotPassable)
        chipDashboardCaution = findViewById(R.id.chipDashboardCaution)
        chipDashboardCleared = findViewById(R.id.chipDashboardCleared)
        tvDashboardAlertsCountBadge = findViewById(R.id.tvDashboardAlertsCountBadge)

        chipDashboardAll.setOnClickListener { setDashboardFilter(AlertFilter.ALL) }
        chipDashboardNotPassable.setOnClickListener { setDashboardFilter(AlertFilter.NOT_PASSABLE) }
        chipDashboardCaution.setOnClickListener { setDashboardFilter(AlertFilter.CAUTION) }
        chipDashboardCleared.setOnClickListener { setDashboardFilter(AlertFilter.CLEARED) }
    }

    private fun setDashboardFilter(filter: AlertFilter) {
        currentFilter = filter
        updateDashboardChipStyles()
        populateCommunityReports(searchLocationInput.text.toString().trim())
    }

    private fun updateDashboardChipStyles() {
        val selectedBg = R.drawable.bg_chip_selected
        val unselectedBg = R.drawable.bg_chip_unselected
        val selectedColor = Color.WHITE
        val unselectedColor = Color.parseColor("#475569")

        chipDashboardAll.setBackgroundResource(if (currentFilter == AlertFilter.ALL) selectedBg else unselectedBg)
        chipDashboardAll.setTextColor(if (currentFilter == AlertFilter.ALL) selectedColor else unselectedColor)

        chipDashboardNotPassable.setBackgroundResource(if (currentFilter == AlertFilter.NOT_PASSABLE) selectedBg else unselectedBg)
        chipDashboardNotPassable.setTextColor(if (currentFilter == AlertFilter.NOT_PASSABLE) selectedColor else unselectedColor)

        chipDashboardCaution.setBackgroundResource(if (currentFilter == AlertFilter.CAUTION) selectedBg else unselectedBg)
        chipDashboardCaution.setTextColor(if (currentFilter == AlertFilter.CAUTION) selectedColor else unselectedColor)

        chipDashboardCleared.setBackgroundResource(if (currentFilter == AlertFilter.CLEARED) selectedBg else unselectedBg)
        chipDashboardCleared.setTextColor(if (currentFilter == AlertFilter.CLEARED) selectedColor else unselectedColor)
    }

    private fun populateCommunityReports(searchQuery: String = "") {
        val container = findViewById<LinearLayout>(R.id.communityReportsContainer) ?: return
        container.removeAllViews()

        val currentUsername = UserSession.getUsername(this)
        val currentFullName = UserSession.getUserName(this)

        val allReports = ReportRepository.loadCommunityReports(this) {
            runOnUiThread {
                populateCommunityReports(searchLocationInput.text.toString().trim())
            }
        }

        updateMapRadius(allReports)
        updateBellNotificationBadge(allReports)

        val searchedReports = if (searchQuery.isBlank()) {
            allReports
        } else {
            allReports.filter {
                it.location.contains(searchQuery, ignoreCase = true) ||
                    it.notes.contains(searchQuery, ignoreCase = true) ||
                    it.statusText.contains(searchQuery, ignoreCase = true)
            }
        }

        // Update live counts on filter chips
        val notPassableCount = searchedReports.count { it.severityLevel == SeverityLevel.NOT_PASSABLE }
        val cautionCount = searchedReports.count { it.severityLevel == SeverityLevel.HEAVY_VEHICLES_ONLY }
        val clearedCount = searchedReports.count { it.severityLevel == SeverityLevel.CLEARED }

        chipDashboardAll.text = "All (${searchedReports.size})"
        chipDashboardNotPassable.text = "🔴 Not Passable ($notPassableCount)"
        chipDashboardCaution.text = "🟡 Caution ($cautionCount)"
        chipDashboardCleared.text = "🟢 Cleared ($clearedCount)"

        val reportsList = searchedReports.filter { report ->
            when (currentFilter) {
                AlertFilter.ALL -> true
                AlertFilter.NOT_PASSABLE -> report.severityLevel == SeverityLevel.NOT_PASSABLE
                AlertFilter.CAUTION -> report.severityLevel == SeverityLevel.HEAVY_VEHICLES_ONLY
                AlertFilter.CLEARED -> report.severityLevel == SeverityLevel.CLEARED
            }
        }

        tvDashboardAlertsCountBadge.text = "${reportsList.size}"

        if (reportsList.isEmpty()) {
            val emptyText = TextView(this).apply {
                val filterDesc = when (currentFilter) {
                    AlertFilter.ALL -> "reports"
                    AlertFilter.NOT_PASSABLE -> "Not Passable alerts"
                    AlertFilter.CAUTION -> "Caution alerts"
                    AlertFilter.CLEARED -> "Cleared alerts"
                }
                text = if (searchQuery.isBlank()) "No $filterDesc at this moment." else "No $filterDesc found for \"$searchQuery\"."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(16, 48, 16, 48)
            }
            container.addView(emptyText)
            return
        }

        for (report in reportsList) {
            val cardView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
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

                setOnClickListener {
                    val intent = Intent(this@DashboardActivity, AlertsActivity::class.java)
                    startActivity(intent)
                }
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

            // 5. Attached Photo (if any)
            if (!report.photoUri.isNullOrBlank()) {
                val photoBtn = TextView(this).apply {
                    text = "📷 View Attached Flood Photo"
                    setTextColor(Color.parseColor("#0284C7"))
                    textSize = 12f
                    paint.isFakeBoldText = true
                    val topMarginPx = (6 * resources.displayMetrics.density).toInt()
                    setPadding(0, topMarginPx, 0, 0)

                    setOnClickListener {
                        showReportPhotoDialog(report.photoUri!!, report.location)
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

            container.addView(cardView)
        }
    }

    private fun showReportPhotoDialog(photoUriStr: String, location: String) {
        ReportPhotoHelper.showFloodPhotoDialog(this, photoUriStr, location)
    }

    private fun searchAndLocateMapArea(query: String) {
        val q = query.lowercase(Locale.ROOT)
        val (lat, lon, title) = when {
            q.contains("ampid") -> Triple(14.6885, 121.1120, "Ampid 1 & 2, San Mateo")
            q.contains("timberland") || q.contains("malanday") -> Triple(14.6812, 121.1620, "Timberland Heights, San Mateo")
            q.contains("dulong") || q.contains("bayan") -> Triple(14.7120, 121.1280, "Dulong Bayan, San Mateo")
            q.contains("banaba") -> Triple(14.6750, 121.1080, "Banaba, San Mateo")
            q.contains("maly") -> Triple(14.7200, 121.1350, "Maly, San Mateo")
            q.contains("santa ana") || q.contains("sta ana") -> Triple(14.6920, 121.1180, "Santa Ana, San Mateo")
            q.contains("silangan") -> Triple(14.6850, 121.1300, "Silangan, San Mateo")
            q.contains("patiis") -> Triple(14.6980, 121.1380, "Patiis, San Mateo")
            q.contains("guitnang") -> Triple(14.6958, 121.1207, "Guitnang Bayan, San Mateo")
            q.contains("felix") -> Triple(14.6150, 121.1020, "Felix Ave.")
            else -> Triple(0.0, 0.0, query)
        }

        if (lat != 0.0) {
            val js = "javascript:locateArea($lat, $lon, '$title');"
            mapWebView.evaluateJavascript(js, null)
        } else {
            thread {
                try {
                    val geocoder = Geocoder(this@DashboardActivity, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName("$query, San Mateo, Rizal, Philippines", 1)
                    if (!addresses.isNullOrEmpty()) {
                        val foundLat = addresses[0].latitude
                        val foundLon = addresses[0].longitude
                        val displayName = addresses[0].featureName ?: query

                        runOnUiThread {
                            val js = "javascript:locateArea($foundLat, $foundLon, '$displayName');"
                            mapWebView.evaluateJavascript(js, null)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private var lastMapRedirectTimeMs = 0L

    private fun openFullMapFromDashboard() {
        val now = System.currentTimeMillis()
        if (now - lastMapRedirectTimeMs < 600L) return
        lastMapRedirectTimeMs = now

        val intent = Intent(this, MapActivity::class.java).apply {
            val currentSearch = searchLocationInput.text.toString().trim()
            if (currentSearch.isNotEmpty()) {
                putExtra("search_query", currentSearch)
            }
        }
        startActivity(intent)
    }

    private fun updateBellNotificationBadge(reports: List<FloodReport>) {
        val activeCount = reports.count { it.severityLevel != SeverityLevel.CLEARED }
        val tvBadge = findViewById<TextView>(R.id.tvBellBadge) ?: return
        if (activeCount > 0) {
            tvBadge.visibility = View.VISIBLE
            tvBadge.text = if (activeCount > 9) "9+" else activeCount.toString()
        } else {
            tvBadge.visibility = View.GONE
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    @Suppress("DEPRECATION")
    private fun setupRoadMap() {
        mapWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 FloodGuardApp/1.0"
        }

        mapWebView.addJavascriptInterface(DashboardMapInterface(), "Android")

        mapWebView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                syncRoadMapWithState()
            }
        }

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <!-- Leaflet Core -->
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                
                <!-- Leaflet MarkerCluster -->
                <link rel="stylesheet" href="https://unpkg.com/leaflet.markercluster@1.5.3/dist/MarkerCluster.css" />
                <link rel="stylesheet" href="https://unpkg.com/leaflet.markercluster@1.5.3/dist/MarkerCluster.Default.css" />
                <script src="https://unpkg.com/leaflet.markercluster@1.5.3/dist/leaflet.markercluster.js"></script>

                <style>
                    html, body, #map {
                        height: 100%;
                        width: 100%;
                        margin: 0;
                        padding: 0;
                    }
                    .leaflet-container {
                        background: #ffffff;
                    }
                    .leaflet-popup-content-wrapper {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        font-size: 12px;
                        border-radius: 8px;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.25);
                    }
                    
                    /* Custom Cluster Badges */
                    .marker-cluster-custom {
                        background: transparent;
                    }
                    .cluster-bubble {
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        border-radius: 50%;
                        color: #ffffff;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        text-align: center;
                        box-shadow: 0 3px 10px rgba(0, 0, 0, 0.4);
                        border: 2px solid #ffffff;
                        width: 38px;
                        height: 38px;
                    }
                    .cluster-count {
                        font-size: 13px;
                        font-weight: 800;
                        line-height: 1;
                    }
                    .cluster-sub {
                        font-size: 8px;
                        font-weight: 600;
                        text-transform: uppercase;
                        letter-spacing: 0.4px;
                        opacity: 0.95;
                    }
                    .cluster-danger { background: linear-gradient(135deg, #DC2626, #7F1D1D); }
                    .cluster-warning { background: linear-gradient(135deg, #EA580C, #9A3412); }
                    .cluster-caution { background: linear-gradient(135deg, #EAB308, #A16207); }
                    .cluster-safe { background: linear-gradient(135deg, #16A34A, #14532D); }

                    /* Custom Pin for Individual Markers */
                    .flood-pin-marker {
                        background: transparent;
                    }
                    .flood-pin-badge {
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        width: 28px;
                        height: 28px;
                        border-radius: 50%;
                        border: 2px solid #ffffff;
                        box-shadow: 0 2px 6px rgba(0,0,0,0.3);
                        font-size: 14px;
                    }
                    .pin-danger { background: #DC2626; }
                    .pin-warning { background: #EA580C; }
                    .pin-caution { background: #EAB308; }
                    .pin-safe { background: #16A34A; }

                    /* User Live GPS Pulse Marker */
                    .user-live-location-pin {
                        background: transparent;
                    }
                    .user-live-pulse-container {
                        position: relative;
                        width: 26px;
                        height: 26px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    .user-live-pulse {
                        position: absolute;
                        width: 24px;
                        height: 24px;
                        border-radius: 50%;
                        background: rgba(2, 132, 199, 0.45);
                        animation: livePulse 2s infinite ease-out;
                    }
                    .user-live-core {
                        position: absolute;
                        width: 12px;
                        height: 12px;
                        border-radius: 50%;
                        background: #0284C7;
                        border: 2px solid #FFFFFF;
                        box-shadow: 0 2px 5px rgba(0,0,0,0.3);
                    }
                    @keyframes livePulse {
                        0% { transform: scale(0.6); opacity: 1; }
                        100% { transform: scale(2.2); opacity: 0; }
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var lat = 14.6958;
                    var lon = 121.1207;
                    var map = L.map('map', { 
                        zoomControl: false,
                        attributionControl: false,
                        doubleClickZoom: false
                    }).setView([lat, lon], 14);
                    
                    L.tileLayer('https://{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
                        maxZoom: 20,
                        subdomains: ['mt0', 'mt1', 'mt2', 'mt3']
                    }).addTo(map);

                    var radiusLayer = L.layerGroup().addTo(map);
                    var safeRouteLayer = L.layerGroup().addTo(map);
                    var hazardRouteLayer = L.layerGroup().addTo(map);
                    var routePinsLayer = L.layerGroup().addTo(map);

                    // Marker Cluster Group setup with fallback
                    var floodClusterGroup = (typeof L.markerClusterGroup === 'function') ? 
                        L.markerClusterGroup({
                            maxClusterRadius: 40,
                            spiderfyOnMaxZoom: true,
                            showCoverageOnHover: false,
                            zoomToBoundsOnClick: false,
                            iconCreateFunction: function(cluster) {
                                var markers = cluster.getAllChildMarkers();
                                var maxRank = 1;
                                var count = markers.length;
                                for (var i = 0; i < count; i++) {
                                    var rank = markers[i].reportRank || 1;
                                    if (rank > maxRank) maxRank = rank;
                                }
                                var cClass = "cluster-safe";
                                if (maxRank >= 4) cClass = "cluster-danger";
                                else if (maxRank === 3) cClass = "cluster-warning";
                                else if (maxRank === 2) cClass = "cluster-caution";

                                return L.divIcon({
                                    html: '<div class="cluster-bubble ' + cClass + '">' +
                                          '<span class="cluster-count">' + count + '</span>' +
                                          '<span class="cluster-sub">alerts</span></div>',
                                    className: 'marker-cluster-custom',
                                    iconSize: [38, 38],
                                    iconAnchor: [19, 19]
                                });
                            }
                        }) : L.layerGroup();
                    map.addLayer(floodClusterGroup);

                    // User Live GPS Marker
                    var userLocation = {
                        lat: 14.6958,
                        lon: 121.1207,
                        title: "San Mateo Town Center",
                        isLive: false
                    };
                    var userLiveMarker = null;

                    window.setUserLocation = function(uLat, uLon, uTitle, isLive) {
                        userLocation.lat = uLat;
                        userLocation.lon = uLon;
                        userLocation.title = uTitle || "My Current Location";
                        userLocation.isLive = !!isLive;

                        if (userLiveMarker) {
                            map.removeLayer(userLiveMarker);
                        }

                        var liveHtml = '<div class="user-live-pulse-container">' +
                                       '<div class="user-live-pulse"></div>' +
                                       '<div class="user-live-core"></div>' +
                                       '</div>';

                        var userIcon = L.divIcon({
                            className: 'user-live-location-pin',
                            html: liveHtml,
                            iconSize: [26, 26],
                            iconAnchor: [13, 13]
                        });

                        userLiveMarker = L.marker([uLat, uLon], { icon: userIcon, zIndexOffset: 1000 }).addTo(map)
                            .bindPopup("<b>📍 " + userLocation.title + "</b><br>" + (isLive ? "<span style='color:#0284C7;font-size:11px;font-weight:bold;'>● Real-time Device Location</span>" : "Default Town Center"));
                    };

                    var activeHazards = [];

                    function getSeverityStyle(waterDepth) {
                        var d = (waterDepth || "").toLowerCase();
                        if (d.indexOf("impassable") >= 0) {
                            return { rank: 4, color: "#8B0000", pinClass: "pin-danger", icon: "⛔" };
                        } else if (d.indexOf("waist") >= 0) {
                            return { rank: 3, color: "#DC2626", pinClass: "pin-warning", icon: "🌊" };
                        } else if (d.indexOf("knee") >= 0) {
                            return { rank: 2, color: "#EA580C", pinClass: "pin-caution", icon: "⚠️" };
                        } else {
                            return { rank: 1, color: "#16A34A", pinClass: "pin-safe", icon: "🟢" };
                        }
                    }

                    function getDistanceM(lat1, lon1, lat2, lon2) {
                        var R = 6371000;
                        var dLat = (lat2 - lat1) * Math.PI / 180;
                        var dLon = (lon2 - lon1) * Math.PI / 180;
                        var a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                                Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
                                Math.sin(dLon/2) * Math.sin(dLon/2);
                        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
                    }

                    window.renderFloodRadius = function(reports) {
                        radiusLayer.clearLayers();
                        floodClusterGroup.clearLayers();
                        if (!reports || reports.length === 0) return;

                        activeHazards = [];
                        var now = Date.now();
                        var maxAgeMs = 2 * 60 * 60 * 1000; // 2-Hour Rolling Heartbeat TTL

                        for (var i = 0; i < reports.length; i++) {
                            var r = reports[i];
                            if (!r.lat || !r.lon) continue;
                            var elapsed = now - (r.timestamp || now);
                            if (elapsed > maxAgeMs) {
                                continue;
                            }

                            var style = getSeverityStyle(r.waterDepth);
                            var stat = (r.statusText || "").toLowerCase();

                            if (style.rank >= 2 || stat.indexOf("not passable") >= 0 || stat.indexOf("caution") >= 0) {
                                activeHazards.push({
                                    lat: r.lat,
                                    lon: r.lon,
                                    location: r.location,
                                    waterDepth: r.waterDepth || "Flooded",
                                    radius: 220
                                });
                            }

                            L.circle([r.lat, r.lon], {
                                radius: 220,
                                color: style.color,
                                fillColor: style.color,
                                fillOpacity: 0.32,
                                weight: 1.5
                            }).addTo(radiusLayer);

                            var pinIcon = L.divIcon({
                                className: 'flood-pin-marker',
                                html: '<div class="flood-pin-badge ' + style.pinClass + '">' + style.icon + '</div>',
                                iconSize: [28, 28],
                                iconAnchor: [14, 14],
                                popupAnchor: [0, -14]
                            });

                            var minutesAgo = Math.max(0, Math.round(elapsed / 60000));
                            var timeAgoText = minutesAgo < 1 ? "Just now" : (minutesAgo < 60 ? minutesAgo + "m ago" : Math.round(minutesAgo/60) + "h ago");
                            var remainingMins = Math.max(0, Math.round((maxAgeMs - elapsed) / 60000));
                            var ttlLabel = remainingMins < 60 ? remainingMins + "m left" : Math.floor(remainingMins/60) + "h " + (remainingMins%60) + "m left";

                            var rId = r.id || "";
                            var flooded = r.stillFloodedCount || 0;
                            var subsided = r.waterSubsidedCount || 0;
                            var isCleared = (r.severityLevel === "CLEARED") || (stat.indexOf("cleared") >= 0);

                            var popupHtml = "<b>" + (isCleared ? "🟢 " : "🚨 ") + r.location + "</b><br>";
                            popupHtml += "<b>Depth:</b> " + (r.waterDepth || "Reported") + " <span style='font-size:11px;color:#64748B;'>(" + timeAgoText + " • " + ttlLabel + ")</span><br>";
                            popupHtml += "<b>Passability:</b> " + (r.statusText || "Active Alert") + "<br>";
                            if (r.notes) {
                                popupHtml += "<small style='color:#334155;'>" + r.notes + "</small>";
                            }

                            var marker = L.marker([r.lat, r.lon], { icon: pinIcon });
                            marker.reportRank = style.rank;
                            marker.bindPopup(popupHtml);
                            floodClusterGroup.addLayer(marker);
                        }
                    };

                    window.renderFlompyGeoJson = function(geojsonData) {
                        if (!geojsonData || !geojsonData.features || geojsonData.features.length === 0) return;
                        radiusLayer.clearLayers();
                        L.geoJSON(geojsonData, {
                            pointToLayer: function(feature, latlng) {
                                var p = feature.properties || {};
                                return L.circle(latlng, {
                                    radius: p.radius || 220,
                                    color: p.color || "#8B0000",
                                    fillColor: p.color || "#8B0000",
                                    fillOpacity: p.fillOpacity || 0.24,
                                    weight: p.strokeWeight || 1.5
                                });
                            },
                            onEachFeature: function(feature, layer) {
                                var p = feature.properties || {};
                                var html = "<b>🚨 " + (p.name || "Flood Zone") + "</b><br>";
                                html += "<b>Latest Status:</b> " + (p.severity || "") + "<br>";
                                html += "<b>Reports:</b> " + (p.reportCount || 1) + " active report(s)<br>";
                                layer.bindPopup(html);
                            }
                        }).addTo(radiusLayer);
                    };

                    // Navigation Route Synchronization
                    window.clearRoute = function() {
                        safeRouteLayer.clearLayers();
                        hazardRouteLayer.clearLayers();
                        routePinsLayer.clearLayers();
                    };

                    function checkCollisions(coords) {
                        var collisions = [];
                        for (var i = 0; i < coords.length; i++) {
                            var ptLat = coords[i][1];
                            var ptLon = coords[i][0];
                            for (var h = 0; h < activeHazards.length; h++) {
                                var hz = activeHazards[h];
                                if (getDistanceM(ptLat, ptLon, hz.lat, hz.lon) <= (hz.radius + 60)) {
                                    var found = collisions.some(function(c) { return c.location === hz.location; });
                                    if (!found) collisions.push(hz);
                                }
                            }
                        }
                        return collisions;
                    }

                    function renderSafePolyline(coords) {
                        safeRouteLayer.clearLayers();
                        var latLngs = coords.map(function(c) { return [c[1], c[0]]; });
                        var polyline = L.polyline(latLngs, {
                            color: '#10B981',
                            weight: 5,
                            opacity: 0.9,
                            lineJoin: 'round',
                            lineCap: 'round'
                        }).addTo(safeRouteLayer);

                        map.fitBounds(polyline.getBounds(), { padding: [25, 25], maxZoom: 15 });
                    }

                    function renderHazardPolyline(coords, collisions) {
                        hazardRouteLayer.clearLayers();
                        if (!coords || coords.length === 0) return;
                        var latLngs = coords.map(function(c) { return [c[1], c[0]]; });
                        L.polyline(latLngs, {
                            color: '#EF4444',
                            weight: 4,
                            opacity: 0.65,
                            dashArray: '8, 8'
                        }).addTo(hazardRouteLayer);

                        for (var i = 0; i < collisions.length; i++) {
                            var hz = collisions[i];
                            L.marker([hz.lat, hz.lon], {
                                icon: L.divIcon({
                                    className: 'hazard-blocked-pin',
                                    html: '<div style="background:#EF4444;color:white;border-radius:50%;width:24px;height:24px;display:flex;align-items:center;justify-content:center;border:2px solid white;box-shadow:0 2px 6px rgba(0,0,0,0.35);font-size:11px;">⛔</div>',
                                    iconSize: [24, 24],
                                    iconAnchor: [12, 12]
                                })
                            }).addTo(hazardRouteLayer);
                        }
                    }

                    window.findSafeRoute = function(destLat, destLon, destTitle) {
                        safeRouteLayer.clearLayers();
                        hazardRouteLayer.clearLayers();
                        routePinsLayer.clearLayers();

                        var startLat = userLocation.lat;
                        var startLon = userLocation.lon;
                        var originLabel = userLocation.isLive ? "My Current Location (Live GPS)" : "San Mateo Town Center";

                        // Origin Pin
                        L.marker([startLat, startLon], {
                            icon: L.divIcon({
                                className: 'route-nav-pin',
                                html: '<div style="background:#0284C7;color:white;border-radius:50%;width:26px;height:26px;display:flex;align-items:center;justify-content:center;border:2px solid white;box-shadow:0 3px 8px rgba(0,0,0,0.4);font-size:12px;font-weight:bold;">📍</div>',
                                iconSize: [26, 26],
                                iconAnchor: [13, 26]
                            })
                        }).addTo(routePinsLayer).bindPopup("<b>Origin:</b> " + originLabel);

                        // Destination Pin
                        L.marker([destLat, destLon], {
                            icon: L.divIcon({
                                className: 'route-nav-pin',
                                html: '<div style="background:#10B981;color:white;border-radius:50%;width:26px;height:26px;display:flex;align-items:center;justify-content:center;border:2px solid white;box-shadow:0 3px 8px rgba(0,0,0,0.4);font-size:12px;font-weight:bold;">🏁</div>',
                                iconSize: [26, 26],
                                iconAnchor: [13, 26]
                            })
                        }).addTo(routePinsLayer).bindPopup("<b>Destination:</b> " + (destTitle || "Target"));

                        var osrmUrl = "https://router.project-osrm.org/route/v1/driving/" +
                            startLon + "," + startLat + ";" + destLon + "," + destLat +
                            "?overview=full&geometries=geojson&alternatives=true";

                        fetch(osrmUrl)
                            .then(function(res) { return res.json(); })
                            .then(function(data) {
                                if (data.routes && data.routes.length > 0) {
                                    var directRoute = data.routes[0];
                                    var directCoords = directRoute.geometry.coordinates;
                                    var collisions = checkCollisions(directCoords);
                                    if (collisions.length === 0) {
                                        renderSafePolyline(directCoords);
                                    } else {
                                        var cleanAlt = null;
                                        for (var a = 1; a < data.routes.length; a++) {
                                            var altCoords = data.routes[a].geometry.coordinates;
                                            if (checkCollisions(altCoords).length === 0) {
                                                cleanAlt = data.routes[a];
                                                break;
                                            }
                                        }
                                        if (cleanAlt) {
                                            renderHazardPolyline(directCoords, collisions);
                                            renderSafePolyline(cleanAlt.geometry.coordinates);
                                        } else {
                                            var dx = destLon - startLon;
                                            var dy = destLat - startLat;
                                            var len = Math.sqrt(dx * dx + dy * dy) || 1.0;
                                            var nx = -dy / len;
                                            var ny = dx / len;
                                            var offsetDeg = 0.007;
                                            var detourLat = collisions[0].lat + ny * offsetDeg;
                                            var detourLon = collisions[0].lon + nx * offsetDeg;
                                            var detourUrl = "https://router.project-osrm.org/route/v1/driving/" +
                                                startLon + "," + startLat + ";" + detourLon + "," + detourLat + ";" + destLon + "," + destLat +
                                                "?overview=full&geometries=geojson";
                                            fetch(detourUrl)
                                                .then(function(r2) { return r2.json(); })
                                                .then(function(d2) {
                                                    if (d2.routes && d2.routes.length > 0) {
                                                        renderHazardPolyline(directCoords, collisions);
                                                        renderSafePolyline(d2.routes[0].geometry.coordinates);
                                                    }
                                                });
                                        }
                                    }
                                }
                            })
                            .catch(function() {});
                    };

                    function locateArea(newLat, newLon, title) {
                        map.setView([newLat, newLon], 15);
                    }

                    // Tap on map opens Road Map Activity
                    map.on('click', function(e) {
                        if (window.Android && window.Android.openFullMap) {
                            window.Android.openFullMap();
                        }
                    });
                </script>
            </body>
            </html>
        """.trimIndent()

        mapWebView.loadDataWithBaseURL("https://unpkg.com", htmlContent, "text/html", "UTF-8", null)
    }

    private fun syncRoadMapWithState() {
        if (!::mapWebView.isInitialized) return

        // 1. Check if device location is available
        try {
            val fineGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarseGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (fineGranted || coarseGranted) {
                val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val loc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (loc != null) {
                    MapStateManager.updateUserLocation(loc.latitude, loc.longitude, "My Current Location", true)
                }
            }
        } catch (_: Exception) {}

        // 2. Sync User Location on Map
        val userLat = MapStateManager.userLat
        val userLon = MapStateManager.userLon
        val isLive = MapStateManager.isLiveGps
        val userTitle = MapStateManager.userLocationName.replace("'", "\\'")
        mapWebView.evaluateJavascript(
            "javascript:if(window.setUserLocation) window.setUserLocation($userLat, $userLon, '$userTitle', $isLive);",
            null
        )

        // 3. Sync Active Flood Reports & Marker Clusters
        updateMapRadius(ReportRepository.loadReports(this))

        // 4. Sync Active Safe Route
        if (MapStateManager.isRouteActive && MapStateManager.destLat != null && MapStateManager.destLon != null) {
            val dLat = MapStateManager.destLat!!
            val dLon = MapStateManager.destLon!!
            val dTitle = (MapStateManager.destTitle ?: "Destination").replace("'", "\\'")
            mapWebView.evaluateJavascript(
                "javascript:if(window.findSafeRoute) window.findSafeRoute($dLat, $dLon, '$dTitle');",
                null
            )
            updateDashboardRouteBanner(true)
        } else {
            mapWebView.evaluateJavascript(
                "javascript:if(window.clearRoute) window.clearRoute();",
                null
            )
            updateDashboardRouteBanner(false)
        }
    }

    private fun updateDashboardRouteBanner(isActive: Boolean) {
        runOnUiThread {
            if (isActive && MapStateManager.isRouteActive) {
                cardDashboardActiveRoute?.visibility = View.VISIBLE
                val originLabel = if (MapStateManager.isLiveGps) "My Location" else "Town Center"
                val dest = MapStateManager.destTitle ?: "Destination"
                tvDashboardRouteTitle?.text = "📍 $originLabel → $dest"
                tvDashboardRouteEtaDist?.text = "⏱️ ${MapStateManager.durationMin.toInt()}m • 📏 ${String.format(Locale.US, "%.1f", MapStateManager.distanceKm)}km"
                if (MapStateManager.hazardAvoided) {
                    tvDashboardRouteBadge?.text = "⚠️ FLOOD HAZARD DETOURED"
                    tvDashboardRouteBadge?.setBackgroundResource(R.drawable.badge_ai_warning)
                    tvDashboardRouteBadge?.setTextColor(Color.parseColor("#9A3412"))
                    tvDashboardRouteHazardAlert?.visibility = View.VISIBLE
                    tvDashboardRouteHazardAlert?.text = "⚠️ ${MapStateManager.hazardSummary ?: "Hazard avoided"} - Safe alternate route active"
                } else {
                    tvDashboardRouteBadge?.text = "🛡️ FLOOD-SAFE ROUTE ACTIVE"
                    tvDashboardRouteBadge?.setBackgroundResource(R.drawable.badge_verified_green)
                    tvDashboardRouteBadge?.setTextColor(Color.parseColor("#047857"))
                    tvDashboardRouteHazardAlert?.visibility = View.GONE
                }
            } else {
                cardDashboardActiveRoute?.visibility = View.GONE
            }
        }
    }

    inner class DashboardMapInterface {
        @JavascriptInterface
        fun openFullMap() {
            runOnUiThread {
                openFullMapFromDashboard()
            }
        }
    }

    private fun updateMapRadius(reports: List<FloodReport>) {
        if (!::mapWebView.isInitialized) return
        val activeWindowMs = 2 * 60 * 60 * 1000L // 2-Hour Rolling Heartbeat TTL
        val now = System.currentTimeMillis()
        val activeReports = reports.filter { r ->
            val age = now - r.timestamp
            age in -120_000L..activeWindowMs
        }
        val jsonArray = JSONArray()
        for (r in activeReports) {
            val obj = JSONObject().apply {
                put("id", r.id)
                put("location", r.location)
                put("waterDepth", r.severity)
                put("statusText", r.statusText)
                put("notes", r.notes)
                put("lat", r.lat)
                put("lon", r.lon)
                put("timestamp", r.timestamp)
                put("reportCount", r.reportCount)
                put("severityLevel", r.severityLevel.name)
            }
            jsonArray.put(obj)
        }
        mapWebView.evaluateJavascript(
            "javascript:if(window.renderFloodRadius) window.renderFloodRadius($jsonArray);",
            null
        )

        FloodGuardApiService.fetchFloodGeoJsonFromBackend { geoJsonStr ->
            runOnUiThread {
                mapWebView.evaluateJavascript(
                    "javascript:if(window.renderFlompyGeoJson) window.renderFlompyGeoJson($geoJsonStr);",
                    null
                )
            }
        }
    }

    private fun setupBottomNavigation() {
        findViewById<LinearLayout>(R.id.logoLayout)?.setOnClickListener {
            Toast.makeText(this, "You are on Home Screen", Toast.LENGTH_SHORT).show()
        }
        val openNotificationAlerts = View.OnClickListener {
            val intent = Intent(this, AlertsActivity::class.java).apply {
                putExtra("title", "Notification Alerts")
            }
            startActivity(intent)
        }
        findViewById<View>(R.id.btnNotificationBell)?.setOnClickListener(openNotificationAlerts)
        findViewById<View>(R.id.bellIcon)?.setOnClickListener(openNotificationAlerts)
        findViewById<ImageView>(R.id.menuIcon)?.setOnClickListener { view ->
            NavigationMenuHelper.showMenu(this, view)
        }
        EdgeNavigationHelper.attachEdgeNavigation(this, EdgeNavigationHelper.NavTab.HOME)
    }

    private fun fetchRealTimeWeather() {
        val lat = 14.6958
        val lon = 121.1207
        val apiUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,precipitation,weather_code&hourly=temperature_2m,precipitation_probability,weather_code&timezone=Asia%2FManila"

        thread {
            try {
                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "FloodGuardApp/1.0 (com.example.floodguard)")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val json = JSONObject(response.toString())
                    val current = json.optJSONObject("current")

                    if (current != null) {
                        val temp = current.optDouble("temperature_2m", 29.0)
                        val precip = current.optDouble("precipitation", 0.0)
                        val humidity = current.optInt("relative_humidity_2m", 80)
                        val weatherCode = current.optInt("weather_code", 0)

                        val weatherCondition = getWeatherCondition(weatherCode)
                        val isSevereWeather = (precip >= 7.5 || weatherCode in listOf(65, 82, 95, 96, 99))
                        if (isSevereWeather) {
                            checkAndPromptSevereWeatherReport(weatherCondition, precip)
                        }

                        // Traffic conditions are determined strictly by real-time meteorological data
                        val rainStatus = when {
                            precip >= 15.0 || weatherCode in listOf(95, 96, 99, 65, 82) -> {
                                val label = if (precip > 0.0) String.format(Locale.US, "Heavy Rain: %.1f mm/h", precip) else weatherCondition
                                "$label • Expect Traffic Delays"
                            }
                            precip >= 2.5 -> String.format(Locale.US, "Moderate Rain: %.1f mm/h • Caution", precip)
                            precip > 0.0 -> String.format(Locale.US, "Light Rain: %.1f mm/h • Normal Flow", precip)
                            else -> "No Rain (0.0 mm/h) • Normal Traffic Flow"
                        }

                        val hourly = json.optJSONObject("hourly")
                        val temps = hourly?.optJSONArray("temperature_2m")

                        val animCondition = when {
                            weatherCondition.contains("Thunderstorm", ignoreCase = true) -> WeatherAnimationView.Condition.THUNDERSTORM
                            weatherCondition.contains("Rain", ignoreCase = true) || weatherCondition.contains("Shower", ignoreCase = true) -> WeatherAnimationView.Condition.RAIN
                            weatherCondition.contains("Drizzle", ignoreCase = true) -> WeatherAnimationView.Condition.DRIZZLE
                            weatherCondition.contains("Cloud", ignoreCase = true) || weatherCondition.contains("Fog", ignoreCase = true) -> WeatherAnimationView.Condition.CLOUDY
                            else -> WeatherAnimationView.Condition.CLEAR
                        }

                        runOnUiThread {
                            weatherAnimationView?.setCondition(animCondition)
                            mainTempText.text = "${temp.roundToInt()}°"
                            weatherDescriptionText.text = "$weatherCondition  Humidity: $humidity%  Air quality: Good"
                            rainMeterText.text = rainStatus

                            if (temps != null && (temps.length() >= 6)) {
                                hourlyTemp1.text = "${temps.getDouble(0).roundToInt()}°"
                                hourlyTemp2.text = "${temps.getDouble(1).roundToInt()}°"
                                hourlyTemp3.text = "${temps.getDouble(2).roundToInt()}°"
                                hourlyTemp4.text = "${temps.getDouble(3).roundToInt()}°"
                                hourlyTemp5.text = "${temps.getDouble(4).roundToInt()}°"
                                hourlyTemp6.text = "${temps.getDouble(5).roundToInt()}°"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    weatherAnimationView?.setCondition(WeatherAnimationView.Condition.THUNDERSTORM)
                    mainTempText.text = "29°"
                    weatherDescriptionText.text = "Thunderstorm  Humidity: 82%  Air quality: Moderate"
                    rainMeterText.text = "No Rain (0.0 mm/h) • Normal Traffic Flow"
                }
            }
        }
    }

    private fun getWeatherCondition(code: Int): String {
        return when (code) {
            0 -> "Clear Sky"
            1, 2, 3 -> "Partly Cloudy"
            45, 48 -> "Foggy"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            80, 81, 82 -> "Rain Showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Thunderstorm"
        }
    }

    private fun checkAndPromptSevereWeatherReport(condition: String, precip: Double) {
        val prefs = getSharedPreferences("floodguard_weather_prompt", Context.MODE_PRIVATE)
        val lastPromptTime = prefs.getLong("last_prompt_time", 0L)
        val now = System.currentTimeMillis()
        val cooldownMs = 2 * 60 * 60 * 1000L // Prompt at most once every 2 hours

        if (now - lastPromptTime < cooldownMs) {
            return
        }
        prefs.edit().putLong("last_prompt_time", now).apply()

        runOnUiThread {
            showSevereWeatherPromptDialog(condition, precip)
            sendSevereWeatherSystemNotification(condition, precip)
        }
    }

    private fun showSevereWeatherPromptDialog(condition: String, precipRate: Double) {
        if (isFinishing || isDestroyed) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_floodguard_confirm, null)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.dialogHeaderSubtitle)
        val tvTitle = dialogView.findViewById<TextView>(R.id.dialogConfirmTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.dialogConfirmMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnDialogConfirm)

        tvSubtitle?.text = "Severe Weather Advisory"
        tvTitle?.text = "⛈️ Severe Weather Detected in San Mateo"
        tvMessage?.text = String.format(
            Locale.US,
            "Heavy rainfall (%s, %.1f mm/h) is currently recorded in your area.\n\nAre you observing flooded streets, rising water levels, or impassable roads nearby? Help keep fellow residents safe by submitting a real-time report.",
            condition,
            precipRate
        )

        btnCancel?.text = "Not Now"
        btnConfirm?.apply {
            text = "Report Flood Now"
            setBackgroundResource(R.drawable.btn_yellow)
            setTextColor(Color.parseColor("#0A192F"))
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm?.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, ReportFloodActivity::class.java)
            startActivity(intent)
        }

        dialog.show()
    }

    private fun sendSevereWeatherSystemNotification(condition: String, precipRate: Double) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val channelId = "floodguard_severe_weather"
        val notificationManager = NotificationManagerCompat.from(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Severe Weather Advisories",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies users during heavy rain and storm conditions to crowdsource flood reports"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val intent = Intent(this, ReportFloodActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "⛈️ Severe Weather Alert — San Mateo"
        val message = String.format(
            Locale.US,
            "Heavy rain (%s, %.1f mm/h) detected. Are there flooded streets near you? Tap to submit a report.",
            condition,
            precipRate
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            notificationManager.notify(7001, notification)
        } catch (_: SecurityException) {
        }
    }
}
