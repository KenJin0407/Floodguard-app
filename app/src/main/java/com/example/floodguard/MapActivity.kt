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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.concurrent.thread

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

        fullMapView.loadDataWithBaseURL("https://basemaps.cartocdn.com", htmlContent, "text/html", "UTF-8", null)
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
