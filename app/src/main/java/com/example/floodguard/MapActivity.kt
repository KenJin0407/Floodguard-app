package com.example.floodguard

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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

/**
 * Activity for the full-screen interactive Leaflet flood map.
 * Displays real-time location heatmaps, flood depth markers, preset barangay quick-zoom chips,
 * and geocoded search capabilities.
 */
class MapActivity : ComponentActivity() {

    private lateinit var fullMapView: WebView
    private lateinit var mapSearchInput: EditText
    private lateinit var mapClearSearch: ImageView
    private lateinit var btnRecenterMap: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        initViews()
        setupFullMap()
        setupPresetChips()
        setupBottomNavigation()

        val searchQuery = intent.getStringExtra("search_query")
        if (!searchQuery.isNullOrBlank()) {
            mapSearchInput.setText(searchQuery)
            searchAndCenterLocation(searchQuery)
        }
    }

    override fun onResume() {
        super.onResume()
        FloatingAlertManager.onActivityResumed(this)
        updateMapRadius()
    }

    override fun onPause() {
        super.onPause()
        FloatingAlertManager.onActivityPaused(this)
    }

    private fun initViews() {
        fullMapView = findViewById(R.id.fullMapView)
        mapSearchInput = findViewById(R.id.mapSearchInput)
        mapClearSearch = findViewById(R.id.mapClearSearch)
        btnRecenterMap = findViewById(R.id.btnRecenterMap)

        mapClearSearch.setOnClickListener {
            mapSearchInput.text.clear()
            recenterMap(14.6958, 121.1207, "San Mateo Town Center")
        }

        btnRecenterMap.setOnClickListener {
            recenterMap(14.6958, 121.1207, "San Mateo, Rizal Town Center")
        }

        mapSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty().trim()
                if (query.length >= 3) {
                    searchAndCenterLocation(query)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        mapSearchInput.setOnEditorActionListener { _, _, _ ->
            val query = mapSearchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                searchAndCenterLocation(query)
            }
            true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun setupFullMap() {
        fullMapView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 FloodGuardApp/1.0"
        }

        fullMapView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                updateMapRadius()
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
                    .leaflet-popup-content-wrapper {
                        font-family: sans-serif;
                        font-size: 13px;
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = L.map('map', { 
                        zoomControl: true,
                        attributionControl: false 
                    }).setView([14.6958, 121.1207], 14);
                    
                    L.tileLayer('https://{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
                        maxZoom: 20,
                        subdomains: ['mt0', 'mt1', 'mt2', 'mt3']
                    }).addTo(map);

                    var activeMarker = L.marker([14.6958, 121.1207]).addTo(map)
                        .bindPopup("<b>San Mateo Town Center</b><br>Google Maps Traffic Navigation");

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
                            // Kumakapal ang kulay at border habang dumadami ang report sa lugar
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

                    function setLocation(lat, lon, title) {
                        map.setView([lat, lon], 15);
                        if (activeMarker) {
                            map.removeLayer(activeMarker);
                        }
                        activeMarker = L.marker([lat, lon]).addTo(map)
                            .bindPopup("<b>" + title + "</b><br>San Mateo, Rizal")
                            .openPopup();
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        fullMapView.loadDataWithBaseURL("http://localhost", htmlContent, "text/html", "UTF-8", null)
    }

    private fun updateMapRadius() {
        if (!::fullMapView.isInitialized) return
        val reports = ReportRepository.loadReports(this) {
            runOnUiThread { updateMapRadius() }
        }
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
        fullMapView.evaluateJavascript(
            "javascript:if(window.renderFloodRadius) window.renderFloodRadius($jsonArray);",
            null
        )

        FloodGuardApiService.fetchFloodGeoJsonFromBackend { geoJsonStr ->
            runOnUiThread {
                fullMapView.evaluateJavascript(
                    "javascript:if(window.renderFlompyGeoJson) window.renderFlompyGeoJson($geoJsonStr);",
                    null
                )
            }
        }
    }

    private fun recenterMap(lat: Double, lon: Double, title: String) {
        val js = "javascript:setLocation($lat, $lon, '$title');"
        fullMapView.evaluateJavascript(js, null)
    }

    private fun searchAndCenterLocation(query: String) {
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
            else -> Triple(0.0, 0.0, query)
        }

        if (lat != 0.0) {
            recenterMap(lat, lon, title)
        } else {
            thread {
                try {
                    val geocoder = Geocoder(this@MapActivity, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName("$query, San Mateo, Rizal, Philippines", 1)
                    if (!addresses.isNullOrEmpty()) {
                        val foundLat = addresses[0].latitude
                        val foundLon = addresses[0].longitude
                        val displayName = addresses[0].featureName ?: query

                        runOnUiThread {
                            recenterMap(foundLat, foundLon, displayName)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun setupPresetChips() {
        findViewById<TextView>(R.id.chipSanMateo)?.setOnClickListener {
            recenterMap(14.6958, 121.1207, "San Mateo Town Center")
        }
        findViewById<TextView>(R.id.chipAmpid)?.setOnClickListener {
            recenterMap(14.6885, 121.1120, "Ampid 1, San Mateo")
        }
        findViewById<TextView>(R.id.chipTimberland)?.setOnClickListener {
            recenterMap(14.6812, 121.1620, "Timberland Heights, San Mateo")
        }
        findViewById<TextView>(R.id.chipDulongBayan)?.setOnClickListener {
            recenterMap(14.7120, 121.1280, "Dulong Bayan, San Mateo")
        }
        findViewById<TextView>(R.id.chipBanaba)?.setOnClickListener {
            recenterMap(14.6750, 121.1080, "Banaba, San Mateo")
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
        findViewById<LinearLayout>(R.id.navHome)?.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish()
        }
        findViewById<LinearLayout>(R.id.navMap)?.setOnClickListener {
            // Already on Map
        }
        findViewById<LinearLayout>(R.id.navAlerts)?.setOnClickListener {
            val intent = Intent(this, AlertsActivity::class.java)
            startActivity(intent)
            finish()
        }
        findViewById<LinearLayout>(R.id.navProfile)?.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
