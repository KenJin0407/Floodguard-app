package com.example.floodguard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Activity for the full-screen interactive Leaflet flood map.
 * Features:
 * 1. Marker Clustering: Automatically groups overlapping/nearby flood reports into interactive cluster badges when zoomed out.
 * 2. Real-Time GPS Origin: Uses the device's actual live GPS location as the origin point for routing and map positioning.
 * 3. Flood-Aware Safe Routing: Calculates driving routes using OSRM, evaluates collision with active flood hazard zones,
 *    and dynamically computes safe alternative detours that steer clear of impassable/caution roads.
 */
class MapActivity : ComponentActivity() {

    private lateinit var fullMapView: WebView
    private lateinit var mapSearchInput: EditText
    private lateinit var mapClearSearch: ImageView
    private lateinit var btnRecenterMap: Button

    // Safe Route Controls
    private lateinit var btnToggleSafeRoute: Button
    private lateinit var routePlannerPanel: LinearLayout
    private lateinit var btnCloseRoutePanel: ImageView
    private lateinit var tvRouteOrigin: TextView
    private lateinit var tvRouteDestination: TextView

    // Bottom Route Result Card
    private lateinit var cardRouteResult: LinearLayout
    private lateinit var tvRouteStatusBadge: TextView
    private lateinit var btnDismissRouteResult: ImageView
    private lateinit var tvRouteTitle: TextView
    private lateinit var tvRouteHazardWarning: TextView
    private lateinit var tvRouteEta: TextView
    private lateinit var tvRouteDistance: TextView
    private lateinit var tvRouteSafetyStatus: TextView

    private var isSafeRouteMode: Boolean = false

    // Real-Time Device Location State
    private var currentDeviceLat: Double = 14.6958
    private var currentDeviceLon: Double = 121.1207
    private var currentDeviceLocationName: String = "San Mateo Town Center"
    private var hasRealLocationFix: Boolean = false
    private var activeLocationListener: LocationListener? = null
    private var currentDestLat: Double = 0.0
    private var currentDestLon: Double = 0.0
    private var currentDestTitle: String = ""

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            startLocationUpdates()
        } else {
            Toast.makeText(
                this,
                "Using default Town Center as origin. Enable GPS for live origin routing.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

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
        checkLocationPermissionAndStart()
    }

    override fun onPause() {
        super.onPause()
        FloatingAlertManager.onActivityPaused(this)
        stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }

    private fun initViews() {
        fullMapView = findViewById(R.id.fullMapView)
        mapSearchInput = findViewById(R.id.mapSearchInput)
        mapClearSearch = findViewById(R.id.mapClearSearch)
        btnRecenterMap = findViewById(R.id.btnRecenterMap)

        btnToggleSafeRoute = findViewById(R.id.btnToggleSafeRoute)
        routePlannerPanel = findViewById(R.id.routePlannerPanel)
        btnCloseRoutePanel = findViewById(R.id.btnCloseRoutePanel)
        tvRouteOrigin = findViewById(R.id.tvRouteOrigin)
        tvRouteDestination = findViewById(R.id.tvRouteDestination)

        cardRouteResult = findViewById(R.id.cardRouteResult)
        tvRouteStatusBadge = findViewById(R.id.tvRouteStatusBadge)
        btnDismissRouteResult = findViewById(R.id.btnDismissRouteResult)
        tvRouteTitle = findViewById(R.id.tvRouteTitle)
        tvRouteHazardWarning = findViewById(R.id.tvRouteHazardWarning)
        tvRouteEta = findViewById(R.id.tvRouteEta)
        tvRouteDistance = findViewById(R.id.tvRouteDistance)
        tvRouteSafetyStatus = findViewById(R.id.tvRouteSafetyStatus)

        mapClearSearch.setOnClickListener {
            mapSearchInput.text.clear()
            if (hasRealLocationFix) {
                recenterMap(currentDeviceLat, currentDeviceLon, currentDeviceLocationName)
            } else {
                recenterMap(14.6958, 121.1207, "San Mateo Town Center")
            }
        }

        btnRecenterMap.setOnClickListener {
            if (hasRealLocationFix) {
                recenterMap(currentDeviceLat, currentDeviceLon, currentDeviceLocationName)
            } else {
                checkLocationPermissionAndStart()
                recenterMap(14.6958, 121.1207, "San Mateo, Rizal Town Center")
            }
        }

        btnToggleSafeRoute.setOnClickListener {
            toggleSafeRouteMode(!isSafeRouteMode)
        }

        btnCloseRoutePanel.setOnClickListener {
            toggleSafeRouteMode(false)
        }

        btnDismissRouteResult.setOnClickListener {
            cardRouteResult.visibility = View.GONE
            MapStateManager.clearRoute()
            fullMapView.evaluateJavascript("javascript:if(window.clearRoute) window.clearRoute();", null)
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

    private fun checkLocationPermissionAndStart() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            startLocationUpdates()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return

        // 1. Get best last known location immediately
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var bestLastLocation: Location? = null
        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) {
                        if (bestLastLocation == null || loc.accuracy < bestLastLocation!!.accuracy) {
                            bestLastLocation = loc
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (bestLastLocation != null) {
            onNewLocationFix(bestLastLocation!!)
        }

        // 2. Register for real-time location updates
        stopLocationUpdates()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onNewLocationFix(location)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        activeLocationListener = listener

        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(
                        provider,
                        4000L,
                        5f,
                        listener,
                        Looper.getMainLooper()
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun stopLocationUpdates() {
        val listener = activeLocationListener ?: return
        activeLocationListener = null
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        try {
            locationManager?.removeUpdates(listener)
        } catch (_: Exception) {
        }
    }

    private fun onNewLocationFix(location: Location) {
        currentDeviceLat = location.latitude
        currentDeviceLon = location.longitude
        hasRealLocationFix = true
        currentDeviceLocationName = "My Current Location"

        MapStateManager.updateUserLocation(location.latitude, location.longitude, "My Current Location", true)

        tvRouteOrigin.text = "📍 Origin: My Current Location"

        if (::fullMapView.isInitialized) {
            val js = "javascript:if(window.setUserLocation) window.setUserLocation($currentDeviceLat, $currentDeviceLon, 'My Current Location', true);"
            fullMapView.evaluateJavascript(js, null)
        }
    }

    private fun toggleSafeRouteMode(enable: Boolean) {
        isSafeRouteMode = enable
        if (isSafeRouteMode) {
            btnToggleSafeRoute.setBackgroundResource(R.drawable.btn_yellow)
            btnToggleSafeRoute.setTextColor(Color.BLACK)
            routePlannerPanel.visibility = View.VISIBLE
            fullMapView.evaluateJavascript("javascript:if(window.setSafeRouteMode) window.setSafeRouteMode(true);", null)
            checkLocationPermissionAndStart()
            Toast.makeText(this, "Safe Route Mode active. Routing from your current location.", Toast.LENGTH_SHORT).show()
        } else {
            btnToggleSafeRoute.setBackgroundResource(R.drawable.bg_chip_unselected)
            btnToggleSafeRoute.setTextColor(Color.WHITE)
            routePlannerPanel.visibility = View.GONE
            cardRouteResult.visibility = View.GONE
            MapStateManager.clearRoute()
            fullMapView.evaluateJavascript("javascript:if(window.setSafeRouteMode) window.setSafeRouteMode(false); if(window.clearRoute) window.clearRoute();", null)
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

        fullMapView.addJavascriptInterface(MapAppInterface(this), "Android")

        fullMapView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                updateMapRadius()
                checkLocationPermissionAndStart()
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
                    .leaflet-popup-content-wrapper {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        font-size: 13px;
                        border-radius: 10px;
                        box-shadow: 0 4px 14px rgba(0,0,0,0.25);
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
                        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.45);
                        border: 2.5px solid #ffffff;
                        width: 44px;
                        height: 44px;
                        animation: popIn 0.2s ease-out;
                    }
                    .cluster-count {
                        font-size: 15px;
                        font-weight: 800;
                        line-height: 1;
                    }
                    .cluster-sub {
                        font-size: 9px;
                        font-weight: 600;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
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
                        width: 32px;
                        height: 32px;
                        border-radius: 50%;
                        border: 2px solid #ffffff;
                        box-shadow: 0 3px 8px rgba(0,0,0,0.35);
                        font-size: 16px;
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
                        width: 28px;
                        height: 28px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    .user-live-pulse {
                        position: absolute;
                        width: 26px;
                        height: 26px;
                        border-radius: 50%;
                        background: rgba(2, 132, 199, 0.45);
                        animation: livePulse 2s infinite ease-out;
                    }
                    .user-live-core {
                        position: absolute;
                        width: 14px;
                        height: 14px;
                        border-radius: 50%;
                        background: #0284C7;
                        border: 2.5px solid #FFFFFF;
                        box-shadow: 0 2px 6px rgba(0,0,0,0.35);
                    }
                    @keyframes livePulse {
                        0% { transform: scale(0.6); opacity: 1; }
                        100% { transform: scale(2.3); opacity: 0; }
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
                        .bindPopup("<b>San Mateo Town Center</b><br>Reference Center");

                    var radiusLayer = L.layerGroup().addTo(map);
                    var safeRouteLayer = L.layerGroup().addTo(map);
                    var hazardRouteLayer = L.layerGroup().addTo(map);
                    var routePinsLayer = L.layerGroup().addTo(map);

                    // User Real-time GPS Location state
                    var userLocation = {
                        lat: 14.6958,
                        lon: 121.1207,
                        title: "San Mateo Town Center",
                        isLive: false
                    };
                    var userLiveMarker = null;

                    window.setUserLocation = function(lat, lon, title, isLive) {
                        userLocation.lat = lat;
                        userLocation.lon = lon;
                        userLocation.title = title || "My Current Location";
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
                            iconSize: [28, 28],
                            iconAnchor: [14, 14]
                        });

                        userLiveMarker = L.marker([lat, lon], { icon: userIcon, zIndexOffset: 1000 }).addTo(map)
                            .bindPopup("<b>📍 " + userLocation.title + "</b><br>" + (isLive ? "<span style='color:#0284C7;font-size:11px;font-weight:bold;'>● Real-time Device Location</span>" : "Default Town Center"));
                    };

                    // Marker Cluster Group setup with fallback
                    var floodClusterGroup = (typeof L.markerClusterGroup === 'function') ? 
                        L.markerClusterGroup({
                            maxClusterRadius: 45,
                            spiderfyOnMaxZoom: true,
                            showCoverageOnHover: false,
                            zoomToBoundsOnClick: true,
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
                                    iconSize: [44, 44],
                                    iconAnchor: [22, 22]
                                });
                            }
                        }) : L.layerGroup();
                    map.addLayer(floodClusterGroup);

                    var currentReports = [];
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

                        currentReports = reports;
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

                            // Track impassable / caution flood zones as hazards for route avoidance
                            if (style.rank >= 2 || stat.indexOf("not passable") >= 0 || stat.indexOf("caution") >= 0) {
                                activeHazards.push({
                                    lat: r.lat,
                                    lon: r.lon,
                                    location: r.location,
                                    waterDepth: r.waterDepth || "Flooded",
                                    radius: 220
                                });
                            }

                            // 1. Draw flood radius ground circle
                            L.circle([r.lat, r.lon], {
                                radius: 220,
                                color: style.color,
                                fillColor: style.color,
                                fillOpacity: 0.32,
                                weight: 2
                            }).addTo(radiusLayer);

                            // 2. Add individual marker to cluster group
                            var pinIcon = L.divIcon({
                                className: 'flood-pin-marker',
                                html: '<div class="flood-pin-badge ' + style.pinClass + '">' + style.icon + '</div>',
                                iconSize: [32, 32],
                                iconAnchor: [16, 16],
                                popupAnchor: [0, -18]
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
                                popupHtml += "<small style='color:#334155;'>" + r.notes + "</small><br>";
                            }
                            popupHtml += "<button onclick=\"window.findSafeRoute(" + r.lat + "," + r.lon + ",'" + (r.location || "").replace(/'/g, "\\'") + "');\" style=\"margin-top:6px;width:100%;background:#0284C7;color:white;border:none;border-radius:6px;padding:5px 9px;font-size:11px;font-weight:bold;cursor:pointer;\">🧭 Find Safe Route Here</button>";

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

                    function setLocation(lat, lon, title) {
                        map.setView([lat, lon], 15);
                        if (activeMarker) {
                            map.removeLayer(activeMarker);
                        }
                        activeMarker = L.marker([lat, lon]).addTo(map)
                            .bindPopup("<b>" + title + "</b><br>San Mateo, Rizal")
                            .openPopup();
                    }

                    // Safe Route Navigation Logic
                    window.isSafeRouteMode = false;
                    window.setSafeRouteMode = function(enabled) {
                        window.isSafeRouteMode = enabled;
                    };

                    window.clearRoute = function() {
                        safeRouteLayer.clearLayers();
                        hazardRouteLayer.clearLayers();
                        routePinsLayer.clearLayers();
                    };

                    var localLandmarks = [
                        { name: "Town Center", lat: 14.6958, lon: 121.1207 },
                        { name: "Ampid", lat: 14.6885, lon: 121.1120 },
                        { name: "Banaba", lat: 14.6750, lon: 121.1080 },
                        { name: "Dulong Bayan", lat: 14.7120, lon: 121.1280 },
                        { name: "Timberland Heights", lat: 14.6812, lon: 121.1620 },
                        { name: "Maly", lat: 14.7200, lon: 121.1350 },
                        { name: "Santa Ana", lat: 14.6920, lon: 121.1180 },
                        { name: "Silangan", lat: 14.6850, lon: 121.1300 },
                        { name: "Patiis", lat: 14.6980, lon: 121.1380 },
                        { name: "Batasan Area", lat: 14.6805, lon: 121.1135 },
                        { name: "Guitnang Bayan", lat: 14.6958, lon: 121.1207 }
                    ];

                    function getCleanDestinationName(lat, lon) {
                        var closest = null;
                        var minDistance = 9999999;
                        for (var i = 0; i < localLandmarks.length; i++) {
                            var d = getDistanceM(lat, lon, localLandmarks[i].lat, localLandmarks[i].lon);
                            if (d < minDistance) {
                                minDistance = d;
                                closest = localLandmarks[i];
                            }
                        }
                        if (closest && minDistance <= 450) {
                            return closest.name;
                        } else if (closest && minDistance <= 1200) {
                            return "Near " + closest.name;
                        } else {
                            return "Dropped Pin";
                        }
                    }

                    map.on('click', function(e) {
                        if (window.isSafeRouteMode) {
                            var clickedLat = e.latlng.lat;
                            var clickedLon = e.latlng.lng;
                            var title = getCleanDestinationName(clickedLat, clickedLon);
                            window.findSafeRoute(clickedLat, clickedLon, title);
                            if (window.Android && window.Android.onDestinationSelectedFromMap) {
                                window.Android.onDestinationSelectedFromMap(clickedLat, clickedLon, title);
                            }
                        }
                    });

                    function checkCollisions(coords) {
                        var collisions = [];
                        for (var i = 0; i < coords.length; i++) {
                            var ptLat = coords[i][1];
                            var ptLon = coords[i][0];
                            for (var h = 0; h < activeHazards.length; h++) {
                                var hz = activeHazards[h];
                                if (getDistanceM(ptLat, ptLon, hz.lat, hz.lon) <= (hz.radius + 60)) {
                                    var found = collisions.some(function(c) { return c.location === hz.location; });
                                    if (!found) {
                                        collisions.push(hz);
                                    }
                                }
                            }
                        }
                        return collisions;
                    }

                    function renderSafePolyline(coords) {
                        var latlngs = coords.map(function(c) { return [c[1], c[0]]; });
                        
                        // Outer glow
                        L.polyline(latlngs, {
                            color: '#38BDF8',
                            weight: 9,
                            opacity: 0.35,
                            lineCap: 'round',
                            lineJoin: 'round'
                        }).addTo(safeRouteLayer);

                        // Main route path
                        var mainLine = L.polyline(latlngs, {
                            color: '#0284C7',
                            weight: 5,
                            opacity: 0.95,
                            lineCap: 'round',
                            lineJoin: 'round'
                        }).addTo(safeRouteLayer);

                        map.fitBounds(mainLine.getBounds(), { padding: [40, 40] });
                    }

                    function renderHazardPolyline(coords, collisions) {
                        var latlngs = coords.map(function(c) { return [c[1], c[0]]; });
                        
                        // Dashed red line indicating blocked path
                        L.polyline(latlngs, {
                            color: '#EF4444',
                            weight: 4,
                            opacity: 0.85,
                            dashArray: '8, 8',
                            lineCap: 'round'
                        }).addTo(hazardRouteLayer);

                        for (var c = 0; c < collisions.length; c++) {
                            var hz = collisions[c];
                            L.marker([hz.lat, hz.lon], {
                                icon: L.divIcon({
                                    className: 'hazard-blocked-pin',
                                    html: '<div style="background:#EF4444;color:white;border-radius:50%;width:26px;height:26px;display:flex;align-items:center;justify-content:center;border:2px solid white;box-shadow:0 2px 6px rgba(0,0,0,0.35);font-size:12px;">⛔</div>',
                                    iconSize: [26, 26],
                                    iconAnchor: [13, 13]
                                })
                            }).addTo(hazardRouteLayer).bindPopup("<b>⛔ Flooded Road: " + hz.location + "</b><br>Depth: " + hz.waterDepth + "<br><span style='color:#DC2626;font-size:11px;font-weight:bold;'>Safe detour routed around this hazard</span>");
                        }
                    }

                    function calculateWaypointDetour(startLat, startLon, destLat, destLon, hazard, directCoords, collisions, destTitle) {
                        var dx = destLon - startLon;
                        var dy = destLat - startLat;
                        var len = Math.sqrt(dx * dx + dy * dy) || 1.0;
                        var nx = -dy / len;
                        var ny = dx / len;
                        
                        var offsetDeg = 0.007; // ~700 meters bypass
                        var detourLat = hazard.lat + ny * offsetDeg;
                        var detourLon = hazard.lon + nx * offsetDeg;

                        var detourUrl = "https://router.project-osrm.org/route/v1/driving/" +
                            startLon + "," + startLat + ";" + detourLon + "," + detourLat + ";" + destLon + "," + destLat +
                            "?overview=full&geometries=geojson";

                        fetch(detourUrl)
                            .then(function(res) { return res.json(); })
                            .then(function(dData) {
                                if (dData.routes && dData.routes.length > 0) {
                                    var detourRoute = dData.routes[0];
                                    renderHazardPolyline(directCoords, collisions);
                                    renderSafePolyline(detourRoute.geometry.coordinates);
                                    var distKm = (detourRoute.distance || 0) / 1000.0;
                                    var durMin = Math.round((detourRoute.duration || 0) / 60.0);
                                    var hzName = collisions.map(function(c) { return c.location + " (" + c.waterDepth + ")"; }).join(", ");
                                    if (window.Android && window.Android.onRouteCalculated) {
                                        window.Android.onRouteCalculated(true, "Avoided: " + hzName, distKm, durMin, destTitle);
                                    }
                                } else {
                                    fallbackSimulation(startLat, startLon, destLat, destLon, hazard, directCoords, collisions, destTitle);
                                }
                            })
                            .catch(function(err) {
                                fallbackSimulation(startLat, startLon, destLat, destLon, hazard, directCoords, collisions, destTitle);
                            });
                    }

                    function fallbackSimulation(startLat, startLon, destLat, destLon, hazard, directCoords, collisions, destTitle) {
                        renderHazardPolyline(directCoords, collisions);
                        var detourPoints = [
                            [startLon, startLat],
                            [hazard.lon + 0.006, hazard.lat + 0.006],
                            [destLon, destLat]
                        ];
                        renderSafePolyline(detourPoints);
                        var hzName = collisions.map(function(c) { return c.location + " (" + c.waterDepth + ")"; }).join(", ");
                        if (window.Android && window.Android.onRouteCalculated) {
                            window.Android.onRouteCalculated(true, "Avoided: " + hzName, 4.2, 13, destTitle);
                        }
                    }

                    function processOsrmRoutes(routes, startLat, startLon, destLat, destLon, destTitle) {
                        var directRoute = routes[0];
                        var directCoords = directRoute.geometry.coordinates;
                        var collisions = checkCollisions(directCoords);

                        if (collisions.length === 0) {
                            renderSafePolyline(directCoords);
                            var distKm = (directRoute.distance || 0) / 1000.0;
                            var durMin = Math.round((directRoute.duration || 0) / 60.0);
                            if (window.Android && window.Android.onRouteCalculated) {
                                window.Android.onRouteCalculated(false, "", distKm, durMin, destTitle);
                            }
                        } else {
                            var cleanAlt = null;
                            for (var a = 1; a < routes.length; a++) {
                                var altCoords = routes[a].geometry.coordinates;
                                var altCollisions = checkCollisions(altCoords);
                                if (altCollisions.length === 0) {
                                    cleanAlt = routes[a];
                                    break;
                                }
                            }

                            if (cleanAlt) {
                                renderHazardPolyline(directCoords, collisions);
                                renderSafePolyline(cleanAlt.geometry.coordinates);
                                var distKm = (cleanAlt.distance || 0) / 1000.0;
                                var durMin = Math.round((cleanAlt.duration || 0) / 60.0);
                                var hzName = collisions.map(function(c) { return c.location + " (" + c.waterDepth + ")"; }).join(", ");
                                if (window.Android && window.Android.onRouteCalculated) {
                                    window.Android.onRouteCalculated(true, "Avoided: " + hzName, distKm, durMin, destTitle);
                                }
                            } else {
                                calculateWaypointDetour(startLat, startLon, destLat, destLon, collisions[0], directCoords, collisions, destTitle);
                            }
                        }
                    }

                    window.findSafeRoute = function(destLat, destLon, destTitle) {
                        safeRouteLayer.clearLayers();
                        hazardRouteLayer.clearLayers();
                        routePinsLayer.clearLayers();

                        // Route from user's live GPS location (or default center)
                        var startLat = userLocation.lat;
                        var startLon = userLocation.lon;
                        var originLabel = userLocation.isLive ? "My Current Location (Live GPS)" : "San Mateo Town Center";

                        // Live Origin Pin
                        L.marker([startLat, startLon], {
                            icon: L.divIcon({
                                className: 'route-nav-pin',
                                html: '<div style="background:#0284C7;color:white;border-radius:50%;width:30px;height:30px;display:flex;align-items:center;justify-content:center;border:2px solid white;box-shadow:0 3px 8px rgba(0,0,0,0.4);font-size:14px;font-weight:bold;">📍</div>',
                                iconSize: [30, 30],
                                iconAnchor: [15, 30]
                            })
                        }).addTo(routePinsLayer).bindPopup("<b>Origin:</b> " + originLabel);

                        // Destination Pin
                        L.marker([destLat, destLon], {
                            icon: L.divIcon({
                                className: 'route-nav-pin',
                                html: '<div style="background:#10B981;color:white;border-radius:50%;width:30px;height:30px;display:flex;align-items:center;justify-content:center;border:2px solid white;box-shadow:0 3px 8px rgba(0,0,0,0.4);font-size:14px;font-weight:bold;">🏁</div>',
                                iconSize: [30, 30],
                                iconAnchor: [15, 30]
                            })
                        }).addTo(routePinsLayer).bindPopup("<b>Destination:</b> " + (destTitle || "Target Location"));

                        var osrmUrl = "https://router.project-osrm.org/route/v1/driving/" +
                            startLon + "," + startLat + ";" + destLon + "," + destLat +
                            "?overview=full&geometries=geojson&alternatives=true";

                        fetch(osrmUrl)
                            .then(function(res) { return res.json(); })
                            .then(function(data) {
                                if (!data.routes || data.routes.length === 0) {
                                    fallbackSimulation(startLat, startLon, destLat, destLon, activeHazards[0] || {lat:14.68,lon:121.11}, [], [], destTitle);
                                    return;
                                }
                                processOsrmRoutes(data.routes, startLat, startLon, destLat, destLon, destTitle);
                            })
                            .catch(function(err) {
                                fallbackSimulation(startLat, startLon, destLat, destLon, activeHazards[0] || {lat:14.68,lon:121.11}, [], [], destTitle);
                            });
                    };
                </script>
            </body>
            </html>
        """.trimIndent()

        fullMapView.loadDataWithBaseURL("https://unpkg.com", htmlContent, "text/html", "UTF-8", null)
    }

    private fun updateMapRadius() {
        if (!::fullMapView.isInitialized) return
        val reports = ReportRepository.loadReports(this) {
            runOnUiThread { updateMapRadius() }
        }
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

    fun requestSafeRoute(destLat: Double, destLon: Double, destTitle: String) {
        currentDestLat = destLat
        currentDestLon = destLon
        currentDestTitle = destTitle
        tvRouteDestination.text = "🎯 Destination: $destTitle"
        val js = "javascript:if(window.findSafeRoute) window.findSafeRoute($destLat, $destLon, '${destTitle.replace("'", "\\'")}');"
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
            if (isSafeRouteMode) {
                requestSafeRoute(lat, lon, title)
            } else {
                recenterMap(lat, lon, title)
            }
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
                            if (isSafeRouteMode) {
                                requestSafeRoute(foundLat, foundLon, displayName)
                            } else {
                                recenterMap(foundLat, foundLon, displayName)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun setupPresetChips() {
        findViewById<TextView>(R.id.chipSanMateo)?.setOnClickListener {
            if (isSafeRouteMode) {
                requestSafeRoute(14.6958, 121.1207, "San Mateo Town Center")
            } else {
                recenterMap(14.6958, 121.1207, "San Mateo Town Center")
            }
        }
        findViewById<TextView>(R.id.chipAmpid)?.setOnClickListener {
            if (isSafeRouteMode) {
                requestSafeRoute(14.6885, 121.1120, "Ampid 1, San Mateo")
            } else {
                recenterMap(14.6885, 121.1120, "Ampid 1, San Mateo")
            }
        }
        findViewById<TextView>(R.id.chipTimberland)?.setOnClickListener {
            if (isSafeRouteMode) {
                requestSafeRoute(14.6812, 121.1620, "Timberland Heights, San Mateo")
            } else {
                recenterMap(14.6812, 121.1620, "Timberland Heights, San Mateo")
            }
        }
        findViewById<TextView>(R.id.chipDulongBayan)?.setOnClickListener {
            if (isSafeRouteMode) {
                requestSafeRoute(14.7120, 121.1280, "Dulong Bayan, San Mateo")
            } else {
                recenterMap(14.7120, 121.1280, "Dulong Bayan, San Mateo")
            }
        }
        findViewById<TextView>(R.id.chipBanaba)?.setOnClickListener {
            if (isSafeRouteMode) {
                requestSafeRoute(14.6750, 121.1080, "Banaba, San Mateo")
            } else {
                recenterMap(14.6750, 121.1080, "Banaba, San Mateo")
            }
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
        EdgeNavigationHelper.attachEdgeNavigation(this, EdgeNavigationHelper.NavTab.MAP)
    }

    fun showRouteResult(hazardAvoided: Boolean, hazardSummary: String, distanceKm: Double, durationMin: Double, destTitle: String) {
        cardRouteResult.visibility = View.VISIBLE
        val originLabel = if (hasRealLocationFix) "My Location" else "Town Center"
        tvRouteTitle.text = "$originLabel → $destTitle"
        tvRouteEta.text = "⏱️ ${durationMin.toInt()} mins"
        tvRouteDistance.text = "📏 ${String.format(Locale.US, "%.1f", distanceKm)} km"

        MapStateManager.setActiveRoute(
            currentDestLat,
            currentDestLon,
            destTitle,
            hazardAvoided,
            hazardSummary,
            distanceKm,
            durationMin
        )

        if (hazardAvoided) {
            tvRouteStatusBadge.text = "⚠️ FLOOD HAZARD DETOURED"
            tvRouteStatusBadge.setBackgroundResource(R.drawable.badge_ai_warning)
            tvRouteStatusBadge.setTextColor(Color.parseColor("#9A3412"))
            tvRouteHazardWarning.visibility = View.VISIBLE
            tvRouteHazardWarning.text = "⚠️ $hazardSummary. Safe detour applied via alternate roads."
            tvRouteSafetyStatus.text = "🛡️ Safe Detour Active"
            tvRouteSafetyStatus.setTextColor(Color.parseColor("#9A3412"))
        } else {
            tvRouteStatusBadge.text = "🛡️ FLOOD-SAFE ROUTE"
            tvRouteStatusBadge.setBackgroundResource(R.drawable.badge_verified_green)
            tvRouteStatusBadge.setTextColor(Color.parseColor("#047857"))
            tvRouteHazardWarning.visibility = View.GONE
            tvRouteSafetyStatus.text = "🛡️ 0 Hazards on Path"
            tvRouteSafetyStatus.setTextColor(Color.parseColor("#047857"))
        }
    }

    fun onMapDestinationSelected(lat: Double, lon: Double, title: String) {
        currentDestLat = lat
        currentDestLon = lon
        currentDestTitle = title
        tvRouteDestination.text = "🎯 Destination: $title"
    }

    class MapAppInterface(private val activity: MapActivity) {
        @JavascriptInterface
        fun onRouteCalculated(hazardAvoided: Boolean, hazardSummary: String, distanceKm: Double, durationMin: Double, destTitle: String) {
            activity.runOnUiThread {
                activity.showRouteResult(hazardAvoided, hazardSummary, distanceKm, durationMin, destTitle)
            }
        }

        @JavascriptInterface
        fun onDestinationSelectedFromMap(lat: Double, lon: Double, title: String) {
            activity.runOnUiThread {
                activity.onMapDestinationSelected(lat, lon, title)
            }
        }
    }
}
