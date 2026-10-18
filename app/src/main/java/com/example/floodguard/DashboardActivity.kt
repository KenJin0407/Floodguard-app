package com.example.floodguard

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.location.Geocoder
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

    private lateinit var mapWebView: WebView
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
        populateCommunityReports(searchLocationInput.text.toString().trim())
    }

    override fun onPause() {
        super.onPause()
        FloatingAlertManager.onActivityPaused(this)
    }

    private fun initViews() {
        mapWebView = findViewById(R.id.mapWebView)
        mainTempText = findViewById(R.id.mainTempText)
        weatherDescriptionText = findViewById(R.id.weatherDescriptionText)
        rainMeterText = findViewById(R.id.rainMeterText)
        searchLocationInput = findViewById(R.id.searchLocationInput)
        clearSearchIcon = findViewById(R.id.clearSearchIcon)

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

        val reportsList = if (searchQuery.isBlank()) {
            allReports
        } else {
            allReports.filter {
                it.location.contains(searchQuery, ignoreCase = true) ||
                    it.notes.contains(searchQuery, ignoreCase = true) ||
                    it.statusText.contains(searchQuery, ignoreCase = true)
            }
        }

        if (reportsList.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = if (searchQuery.isBlank()) "No community flood reports from other users at this moment." else "No flood reports found for \"$searchQuery\"."
                setTextColor(resources.getColor(android.R.color.darker_gray, theme))
                textSize = 13f
                setPadding(12, 12, 12, 12)
            }
            container.addView(emptyText)
            return
        }

        for (report in reportsList) {
            val cardView = RelativeLayout(this).apply {
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = (12 * resources.displayMetrics.density).toInt()
                }
                layoutParams = params
                val paddingPx = (14 * resources.displayMetrics.density).toInt()
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

            val timeText = TextView(this).apply {
                id = View.generateViewId()
                text = report.getFormattedTimeAgo()
                setTextColor(resources.getColor(android.R.color.darker_gray, theme))
                textSize = 11f
                val params = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                }
                layoutParams = params
            }

            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val params = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_START)
                    addRule(RelativeLayout.START_OF, timeText.id)
                }
                layoutParams = params
            }

            val titleText = TextView(this).apply {
                text = "${report.location} - ${report.statusText}"
                setTextColor(resources.getColor(android.R.color.black, theme))
                textSize = 14f
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

            if (!report.photoUri.isNullOrBlank()) {
                val photoBtn = TextView(this).apply {
                    text = "View Flood Photo"
                    setTextColor(resources.getColor(android.R.color.holo_blue_dark, theme))
                    textSize = 12f
                    paint.isFakeBoldText = true
                    val topMarginPx = (6 * resources.displayMetrics.density).toInt()
                    setPadding(0, topMarginPx, 0, 0)

                    setOnClickListener {
                        showReportPhotoDialog(report.photoUri!!, report.location)
                    }
                }
                textLayout.addView(photoBtn)
            }

            cardView.addView(textLayout)
            cardView.addView(timeText)

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

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    @Suppress("DEPRECATION")
    private fun setupRoadMap() {
        val gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    openFullMapFromDashboard()
                    return true
                }
            }
        )

        mapWebView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        mapWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 FloodGuardApp/1.0"
        }

        mapWebView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                updateMapRadius(ReportRepository.loadReports(this@DashboardActivity))
            }
        }

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
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

                    var activeMarker = L.marker([lat, lon]).addTo(map);
                    activeMarker.bindPopup("<b>San Mateo, Rizal</b><br>Google Maps Traffic Navigation").openPopup();

                    var radiusLayer = L.layerGroup().addTo(map);

                    function getSeverityStyle(waterDepth) {
                        var d = (waterDepth || "").toLowerCase();
                        if (d.indexOf("impassable") >= 0) {
                            return { rank: 4, color: "#8B0000" }; // Dark Red
                        } else if (d.indexOf("waist") >= 0) {
                            return { rank: 3, color: "#D32F2F" }; // Red
                        } else if (d.indexOf("knee") >= 0) {
                            return { rank: 2, color: "#FFA000" }; // Yellow/Orange
                        } else {
                            return { rank: 1, color: "#388E3C" }; // Green (Ankle-Deep)
                        }
                    }

                    window.renderFloodRadius = function(reports) {
                        radiusLayer.clearLayers();
                        if (!reports || reports.length === 0) return;

                        var grouped = {};
                        for (var i = 0; i < reports.length; i++) {
                            var r = reports[i];
                            if (!r.lat || !r.lon) continue;
                            var key = r.lat.toFixed(4) + "," + r.lon.toFixed(4);
                            var style = getSeverityStyle(r.waterDepth);

                            if (!grouped[key]) {
                                grouped[key] = {
                                    lat: r.lat,
                                    lon: r.lon,
                                    location: r.location,
                                    waterDepth: r.waterDepth,
                                    notes: r.notes,
                                    count: 1,
                                    rank: style.rank,
                                    color: style.color
                                };
                            } else {
                                grouped[key].count += 1;
                                if (style.rank >= grouped[key].rank) {
                                    grouped[key].rank = style.rank;
                                    grouped[key].color = style.color;
                                    grouped[key].waterDepth = r.waterDepth;
                                    grouped[key].notes = r.notes;
                                }
                            }
                        }

                        for (var k in grouped) {
                            var g = grouped[k];
                            var fillOpacity = Math.min(0.22 + (g.count - 1) * 0.16, 0.78);
                            var strokeWeight = Math.min(1.5 + (g.count - 1) * 1.2, 6.0);

                            var circle = L.circle([g.lat, g.lon], {
                                radius: 220,
                                color: g.color,
                                fillColor: g.color,
                                fillOpacity: fillOpacity,
                                weight: strokeWeight
                            }).addTo(radiusLayer);

                            circle.bindPopup(
                                "<b>🚨 " + g.location + "</b><br>" +
                                "<b>Severity:</b> " + g.waterDepth + "<br>" +
                                "<b>Reports:</b> " + g.count + " report(s)<br>" +
                                "<small>" + (g.notes || "") + "</small>"
                            );
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
                                    fillOpacity: p.fillOpacity || 0.22,
                                    weight: p.strokeWeight || 1.5
                                });
                            },
                            onEachFeature: function(feature, layer) {
                                var p = feature.properties || {};
                                layer.bindPopup(
                                    "<b>🚨 " + (p.name || "Flood Zone") + "</b><br>" +
                                    "<b>Severity:</b> " + (p.severity || "") + "<br>" +
                                    "<b>Reports:</b> " + (p.reportCount || 1) + " report(s)<br>" +
                                    "<small>" + (p.notes || p.source || "") + "</small>"
                                );
                            }
                        }).addTo(radiusLayer);
                    };

                    function locateArea(newLat, newLon, title) {
                        map.setView([newLat, newLon], 15);
                        if (activeMarker) {
                            map.removeLayer(activeMarker);
                        }
                        activeMarker = L.marker([newLat, newLon]).addTo(map)
                            .bindPopup("<b>" + title + "</b><br>San Mateo, Rizal")
                            .openPopup();
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        mapWebView.loadDataWithBaseURL("https://basemaps.cartocdn.com", htmlContent, "text/html", "UTF-8", null)
    }

    private fun updateMapRadius(reports: List<FloodReport>) {
        if (!::mapWebView.isInitialized) return
        val jsonArray = JSONArray()
        for (r in reports) {
            val obj = JSONObject().apply {
                put("location", r.location)
                put("waterDepth", r.severity)
                put("statusText", r.statusText)
                put("notes", r.notes)
                put("lat", r.lat)
                put("lon", r.lon)
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
        findViewById<LinearLayout>(R.id.navHome)?.setOnClickListener {
            // Already on Home
        }
        findViewById<LinearLayout>(R.id.navMap)?.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            startActivity(intent)
        }
        findViewById<LinearLayout>(R.id.navAlerts)?.setOnClickListener {
            val intent = Intent(this, AlertsActivity::class.java)
            startActivity(intent)
        }
        findViewById<LinearLayout>(R.id.navProfile)?.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }
        findViewById<ImageView>(R.id.bellIcon)?.setOnClickListener {
            val intent = Intent(this, AlertsActivity::class.java)
            startActivity(intent)
        }
        findViewById<ImageView>(R.id.menuIcon)?.setOnClickListener { view ->
            NavigationMenuHelper.showMenu(this, view)
        }
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

                        val reports = ReportRepository.loadReports(this)
                        val hasNotPassableReport = reports.any { it.severityLevel == SeverityLevel.NOT_PASSABLE }

                        val weatherCondition = getWeatherCondition(weatherCode)
                        val rainStatus = when {
                            precip >= 25.0 -> String.format(Locale.US, "Heavy Rain: %.1f mm/h Critical Traffic Gridlock (Main Roads)", precip)
                            hasNotPassableReport -> String.format(Locale.US, "Rainfall: %.1f mm/h Active Flood Reports (Main Roads Gridlock)", precip)
                            precip >= 2.5 -> String.format(Locale.US, "Moderate Rain: %.1f mm/h Caution", precip)
                            precip > 0.0 -> String.format(Locale.US, "Light Rain: %.1f mm/h Normal Flow", precip)
                            else -> "No Rain (0.0 mm/h) Normal Traffic Flow"
                        }

                        val hourly = json.optJSONObject("hourly")
                        val temps = hourly?.optJSONArray("temperature_2m")

                        runOnUiThread {
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
                    mainTempText.text = "29°"
                    weatherDescriptionText.text = "Thunderstorm  Humidity: 82%  Air quality: Moderate"
                    rainMeterText.text = "Rainfall: 0.0 mm/h Normal Traffic Flow"
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
}
