package com.example.floodguard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Filter
import android.widget.Filterable
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.concurrent.thread

data class LocationSuggestionItem(
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
    val isDeviceLocation: Boolean = false,
) {
    override fun toString(): String = title
}

class ReportFloodActivity : ComponentActivity() {

    private lateinit var photoPickerBox: FrameLayout
    private lateinit var photoPreview: ImageView
    private lateinit var photoPlaceholder: LinearLayout
    private lateinit var locationInput: AutoCompleteTextView
    private lateinit var btnRefreshLocation: TextView
    private lateinit var btnClearLocation: TextView
    private lateinit var btnLocationDropdown: TextView
    private lateinit var locationCoordsText: TextView
    private lateinit var notesInput: EditText
    private lateinit var submitReportButton: Button

    private lateinit var btnAnkleDeep: Button
    private lateinit var btnKneeDeep: Button
    private lateinit var btnWaistDeep: Button
    private lateinit var btnImpassable: Button

    private var selectedSeverity = "Waist-Deep"
    private var selectedImageUri: Uri? = null
    private var persistedPhotoData: String? = null

    private var currentLat: Double = SmsAlertBroadcastManager.DEFAULT_USER_LAT
    private var currentLon: Double = SmsAlertBroadcastManager.DEFAULT_USER_LON
    private var detectedDeviceSuggestion: LocationSuggestionItem? = null
    private lateinit var locationAdapter: LocationDropdownAdapter
    private var activeLocationListener: LocationListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var userChoseLocation = false
    private var programmaticTextChange = false

    private fun setLocationTextProgrammatically(text: String) {
        programmaticTextChange = true
        locationInput.setText(text, false)
        programmaticTextChange = false
    }

    private val presetLocations = listOf(
        LocationSuggestionItem("Gen. Luna Ave, Guitnang Bayan, San Mateo, Rizal", "San Mateo Main Road • Flood Monitoring Zone", 14.6958, 121.1207),
        LocationSuggestionItem("M.H. Del Pilar St, Guitnang Bayan, San Mateo, Rizal", "Guitnang Bayan • Commercial & Residential Road", 14.6965, 121.1195),
        LocationSuggestionItem("Ampid 1, San Mateo, Rizal", "Brgy. Ampid 1 • Low-lying Riverbank Area", 14.6885, 121.1120),
        LocationSuggestionItem("Ampid 2, San Mateo, Rizal", "Brgy. Ampid 2 • San Mateo Boundary", 14.6865, 121.1145),
        LocationSuggestionItem("Banaba, San Mateo, Rizal", "Brgy. Banaba • Marikina-San Mateo River Corridor", 14.6750, 121.1080),
        LocationSuggestionItem("Dulong Bayan 1, San Mateo, Rizal", "Brgy. Dulong Bayan 1 • Northern San Mateo", 14.7110, 121.1270),
        LocationSuggestionItem("Dulong Bayan 2, San Mateo, Rizal", "Brgy. Dulong Bayan 2 • San Mateo", 14.7135, 121.1305),
        LocationSuggestionItem("Guitnang Bayan 1, San Mateo, Rizal", "Brgy. Guitnang Bayan 1 • Town Proper", 14.6960, 121.1215),
        LocationSuggestionItem("Guitnang Bayan 2, San Mateo, Rizal", "Brgy. Guitnang Bayan 2 • Town Proper", 14.6948, 121.1235),
        LocationSuggestionItem("Gulod Malaya, San Mateo, Rizal", "Brgy. Gulod Malaya • Eastern Slope Area", 14.6910, 121.1360),
        LocationSuggestionItem("Malanday / Timberland Heights, San Mateo, Rizal", "Brgy. Malanday • Upland & Access Road", 14.6812, 121.1620),
        LocationSuggestionItem("Maly, San Mateo, Rizal", "Brgy. Maly • San Mateo-Rodriguez Road", 14.7200, 121.1350),
        LocationSuggestionItem("Pintong Bukawe, San Mateo, Rizal", "Brgy. Pintong Bukawe • San Mateo", 14.7050, 121.1580),
        LocationSuggestionItem("Santa Ana, San Mateo, Rizal", "Brgy. Santa Ana • Near Marikina River", 14.6920, 121.1180),
        LocationSuggestionItem("Santo Niño, San Mateo, Rizal", "Brgy. Santo Niño • San Mateo", 14.6990, 121.1290),
        LocationSuggestionItem("Silangan, San Mateo, Rizal", "Brgy. Silangan • San Mateo", 14.6850, 121.1300),
        LocationSuggestionItem("Patiis Road, San Mateo, Rizal", "Patiis Road • Brgy. Malanday / Dulong Bayan", 14.6980, 121.1380),
        LocationSuggestionItem("Batasan-San Mateo Bridge, San Mateo, Rizal", "Major Bridge Crossing • Critical Water Level Point", 14.6805, 121.1135),
        LocationSuggestionItem("B. Mariano St, Santa Ana, San Mateo, Rizal", "Brgy. Santa Ana • Flood-Prone Street", 14.6928, 121.1172),
        LocationSuggestionItem("Kambal Road, Guitnang Bayan, San Mateo, Rizal", "Guitnang Bayan • Interior Road", 14.6975, 121.1245),
        LocationSuggestionItem("San Mateo Town Center, San Mateo, Rizal", "Municipal Hall & Plaza Area", 14.6958, 121.1207),
        LocationSuggestionItem("Tumana Bridge, Marikina City", "Marikina-San Mateo Border Corridor", 14.6575, 121.0970),
        LocationSuggestionItem("Nangka, Marikina City", "Marikina-San Mateo Boundary", 14.6715, 121.1065),
        LocationSuggestionItem("Felix Ave, Cainta, Rizal", "Cainta-Marcos Highway Corridor", 14.6150, 121.1020),
        LocationSuggestionItem("Burgos, Rodriguez (Montalban), Rizal", "Adjacent Municipality • Northern Corridor", 14.7310, 121.1420),
    )

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            persistedPhotoData = ReportPhotoHelper.persistPickedPhoto(this, uri)
            val bitmap = ReportPhotoHelper.loadBitmap(this, persistedPhotoData ?: uri.toString())
            if (bitmap != null) {
                photoPreview.setImageBitmap(bitmap)
            } else {
                photoPreview.setImageURI(uri)
            }
            photoPreview.visibility = View.VISIBLE
            photoPlaceholder.visibility = View.GONE
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchDeviceLocation()
        } else {
            resolveAndApplyCoordinates(
                SmsAlertBroadcastManager.DEFAULT_USER_LAT,
                SmsAlertBroadcastManager.DEFAULT_USER_LON,
                isFallback = true
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_flood)

        initViews()
        setupLocationDropdown()
        setupSeverityButtons()
        setupPhotoPicker()
        requestAndAutoDetectDeviceLocation()

        submitReportButton.setOnClickListener {
            submitReport()
        }

        btnRefreshLocation.setOnClickListener {
            requestAndAutoDetectDeviceLocation()
        }

        findViewById<ImageView>(R.id.backButton)?.setOnClickListener {
            navigateBack()
        }

        findViewById<Button>(R.id.backButtonBottom)?.setOnClickListener {
            navigateBack()
        }

        findViewById<ImageView>(R.id.menuIcon)?.setOnClickListener { view ->
            NavigationMenuHelper.showMenu(this, view)
        }
    }

    private fun navigateBack() {
        if (isTaskRoot) {
            startActivity(Intent(this, AlertsActivity::class.java))
        }
        finish()
    }

    override fun onResume() {
        super.onResume()
        FloatingAlertManager.onActivityResumed(this)
    }

    override fun onPause() {
        super.onPause()
        FloatingAlertManager.onActivityPaused(this)
        stopActiveLocationUpdates()
    }

    private fun initViews() {
        photoPickerBox = findViewById(R.id.photoPickerBox)
        photoPreview = findViewById(R.id.photoPreview)
        photoPlaceholder = findViewById(R.id.photoPlaceholder)
        locationInput = findViewById(R.id.locationInput)
        btnRefreshLocation = findViewById(R.id.btnRefreshLocation)
        btnClearLocation = findViewById(R.id.btnClearLocation)
        btnLocationDropdown = findViewById(R.id.btnLocationDropdown)
        locationCoordsText = findViewById(R.id.locationCoordsText)
        notesInput = findViewById(R.id.notesInput)
        submitReportButton = findViewById(R.id.submitReportButton)

        btnAnkleDeep = findViewById(R.id.btnAnkleDeep)
        btnKneeDeep = findViewById(R.id.btnKneeDeep)
        btnWaistDeep = findViewById(R.id.btnWaistDeep)
        btnImpassable = findViewById(R.id.btnImpassable)
    }

    @SuppressLint("SetTextI18n")
    private fun setupLocationDropdown() {
        locationAdapter = LocationDropdownAdapter(this, presetLocations)
        locationInput.setAdapter(locationAdapter)

        locationInput.setOnItemClickListener { _, _, position, _ ->
            val item = locationAdapter.getItem(position) ?: return@setOnItemClickListener
            userChoseLocation = true
            currentLat = item.lat
            currentLon = item.lon
            setLocationTextProgrammatically(item.title)
            locationInput.setSelection(item.title.length)
            val sourceLabel = if (item.isDeviceLocation) "Device GPS" else "Selected Area"
            locationCoordsText.text = String.format(
                Locale.US,
                "📍 %s: %.4f, %.4f • %s",
                sourceLabel,
                item.lat,
                item.lon,
                item.subtitle
            )
        }

        locationInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString().orEmpty()
                btnClearLocation.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
                if (!programmaticTextChange && locationInput.hasFocus()) {
                    userChoseLocation = true
                    if (text.isNotEmpty() && !text.contains("Detecting device location")) {
                        locationAdapter.filter.filter(text)
                        locationInput.post {
                            if (!isFinishing && !isDestroyed && !locationInput.isPopupShowing) {
                                locationInput.showDropDown()
                            }
                        }
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearLocation.setOnClickListener {
            locationInput.setText("", false)
            locationInput.requestFocus()
            locationAdapter.filter.filter("")
            locationInput.post {
                if (!isFinishing && !isDestroyed) {
                    locationInput.showDropDown()
                }
            }
        }

        btnLocationDropdown.setOnClickListener {
            if (locationInput.isPopupShowing) {
                locationInput.dismissDropDown()
            } else {
                locationInput.requestFocus()
                locationAdapter.filter.filter("")
                locationInput.showDropDown()
            }
        }

        locationInput.setOnClickListener {
            if (!locationInput.isPopupShowing) {
                locationAdapter.filter.filter(locationInput.text?.toString().orEmpty())
                locationInput.showDropDown()
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAndAutoDetectDeviceLocation() {
        if (hasLocationPermission()) {
            fetchDeviceLocation()
        } else {
            locationInput.setText("Detecting device location...", false)
            locationCoordsText.text = "Requesting device location permission..."
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission", "SetTextI18n")
    private fun fetchDeviceLocation() {
        locationInput.setText("Detecting device location...", false)
        locationCoordsText.text = "Scanning GPS & network providers for device location..."

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            resolveAndApplyCoordinates(
                SmsAlertBroadcastManager.DEFAULT_USER_LAT,
                SmsAlertBroadcastManager.DEFAULT_USER_LON,
                isFallback = true
            )
            return
        }

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
            resolveAndApplyCoordinates(
                bestLastLocation!!.latitude,
                bestLastLocation!!.longitude,
                isFallback = false
            )
        }

        // Also request a live single location update from enabled providers
        stopActiveLocationUpdates()
        var receivedLiveFix = false
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                receivedLiveFix = true
                stopActiveLocationUpdates()
                resolveAndApplyCoordinates(location.latitude, location.longitude, isFallback = false)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        activeLocationListener = listener

        var registeredAny = false
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    registeredAny = true
                }
            } catch (_: Exception) {
            }
        }

        mainHandler.postDelayed({
            if (!receivedLiveFix) {
                stopActiveLocationUpdates()
                if (bestLastLocation == null) {
                    resolveAndApplyCoordinates(
                        SmsAlertBroadcastManager.DEFAULT_USER_LAT,
                        SmsAlertBroadcastManager.DEFAULT_USER_LON,
                        isFallback = true
                    )
                }
            }
        }, if (registeredAny) 3500L else 300L)
    }

    private fun stopActiveLocationUpdates() {
        val listener = activeLocationListener ?: return
        activeLocationListener = null
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            locationManager?.removeUpdates(listener)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("SetTextI18n")
    private fun resolveAndApplyCoordinates(lat: Double, lon: Double, isFallback: Boolean) {
        currentLat = lat
        currentLon = lon

        thread {
            var resolvedAddress: String? = null

            // 1. Try Android Geocoder
            try {
                val geocoder = Geocoder(this@ReportFloodActivity, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val parts = mutableListOf<String>()
                    val street = addr.thoroughfare ?: addr.featureName
                    val subLoc = addr.subLocality
                    val locality = addr.locality
                    val admin = addr.subAdminArea ?: addr.adminArea
                    if (!street.isNullOrBlank()) parts.add(street)
                    if (!subLoc.isNullOrBlank() && subLoc != street) parts.add(subLoc)
                    if (!locality.isNullOrBlank() && locality != subLoc) parts.add(locality)
                    if (!admin.isNullOrBlank() && admin != locality) parts.add(admin)
                    if (parts.isNotEmpty()) {
                        resolvedAddress = parts.joinToString(", ")
                    }
                }
            } catch (_: Exception) {
            }

            // 2. Fallback to OpenStreetMap Nominatim Reverse Geocoding if Geocoder is unavailable
            if (resolvedAddress.isNullOrBlank()) {
                try {
                    val url = URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=18&addressdetails=1")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "FloodGuardApp/1.0")
                    conn.connectTimeout = 3500
                    conn.readTimeout = 3500
                    if (conn.responseCode == 200) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(resp)
                        val addrObj = json.optJSONObject("address")
                        if (addrObj != null) {
                            val road = addrObj.optString("road").ifBlank {
                                addrObj.optString("neighbourhood").ifBlank { addrObj.optString("suburb") }
                            }
                            val suburb = addrObj.optString("suburb").ifBlank { addrObj.optString("village") }
                            val town = addrObj.optString("town").ifBlank {
                                addrObj.optString("city").ifBlank { addrObj.optString("municipality", "San Mateo") }
                            }
                            val state = addrObj.optString("state", "Rizal")
                            val parts = listOf(road, suburb, town, state).filter { it.isNotBlank() }.distinct()
                            if (parts.isNotEmpty()) {
                                resolvedAddress = parts.joinToString(", ")
                            }
                        }
                    }
                    conn.disconnect()
                } catch (_: Exception) {
                }
            }

            val finalLocationName = resolvedAddress ?: "Gen. Luna Ave, Guitnang Bayan, San Mateo, Rizal"

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val deviceItem = LocationSuggestionItem(
                    title = finalLocationName,
                    subtitle = String.format(Locale.US, "Current Device Location (%.4f, %.4f)", lat, lon),
                    lat = lat,
                    lon = lon,
                    isDeviceLocation = true
                )
                detectedDeviceSuggestion = deviceItem
                locationAdapter.setDeviceLocationItem(deviceItem)

                locationInput.setText(finalLocationName, false)
                btnClearLocation.visibility = View.VISIBLE
                locationCoordsText.text = if (isFallback) {
                    String.format(
                        Locale.US,
                        "📍 Default Area GPS: %.4f, %.4f • Type or tap ▼ to select location",
                        lat,
                        lon
                    )
                } else {
                    String.format(
                        Locale.US,
                        "📍 Device GPS Detected: %.4f, %.4f • Type or tap ▼ to change",
                        lat,
                        lon
                    )
                }
            }
        }
    }

    private fun setupPhotoPicker() {
        photoPickerBox.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }
    }

    private fun setupSeverityButtons() {
        updateSeverityUI(btnWaistDeep)

        btnAnkleDeep.setOnClickListener {
            selectedSeverity = "Ankle-Deep"
            updateSeverityUI(btnAnkleDeep)
        }
        btnKneeDeep.setOnClickListener {
            selectedSeverity = "Knee-Deep"
            updateSeverityUI(btnKneeDeep)
        }
        btnWaistDeep.setOnClickListener {
            selectedSeverity = "Waist-Deep"
            updateSeverityUI(btnWaistDeep)
        }
        btnImpassable.setOnClickListener {
            selectedSeverity = "Impassable"
            updateSeverityUI(btnImpassable)
        }
    }

    private fun updateSeverityUI(selectedButton: Button) {
        val buttons = listOf(btnAnkleDeep, btnKneeDeep, btnWaistDeep, btnImpassable)
        for (btn in buttons) {
            if (btn == selectedButton) {
                btn.setBackgroundResource(R.drawable.btn_severity_selected)
                btn.setTextColor(resources.getColor(android.R.color.white, theme))
            } else {
                btn.setBackgroundResource(R.drawable.btn_severity_default)
                btn.setTextColor(resources.getColor(android.R.color.black, theme))
            }
        }
    }

    private fun submitReport() {
        val rawLocation = locationInput.text.toString()
            .replace("Auto Detected - ", "")
            .replace("Detecting device location...", "")
            .trim()
        val loc = if (rawLocation.isBlank() || rawLocation.contains("Detecting device")) {
            detectedDeviceSuggestion?.title ?: "Gen. Luna Ave, San Mateo, Rizal"
        } else {
            rawLocation
        }
        val notes = notesInput.text.toString().trim()

        val (statusText, level) = when (selectedSeverity) {
            "Impassable", "Waist-Deep" -> Pair("Not Passable", SeverityLevel.NOT_PASSABLE)
            "Knee-Deep" -> Pair("Heavy vehicles only", SeverityLevel.HEAVY_VEHICLES_ONLY)
            else -> Pair("Cleared", SeverityLevel.CLEARED)
        }

        // Match typed text against preset/device coordinates or resolve by name
        val matchedPreset = presetLocations.firstOrNull { it.title.equals(loc, ignoreCase = true) }
        val (resolvedLat, resolvedLon) = when {
            matchedPreset != null -> Pair(matchedPreset.lat, matchedPreset.lon)
            detectedDeviceSuggestion != null && (detectedDeviceSuggestion!!.title.equals(loc, ignoreCase = true) || rawLocation.isBlank() || rawLocation.contains("Detecting device")) ->
                Pair(detectedDeviceSuggestion!!.lat, detectedDeviceSuggestion!!.lon)
            else -> {
                val (byNameLat, byNameLon) = SmsAlertBroadcastManager.resolveCoordinatesForLocation(loc)
                if (byNameLat != SmsAlertBroadcastManager.DEFAULT_USER_LAT || byNameLon != SmsAlertBroadcastManager.DEFAULT_USER_LON) {
                    Pair(byNameLat, byNameLon)
                } else {
                    Pair(currentLat, currentLon)
                }
            }
        }

        val newReport = FloodReport(
            location = loc,
            severity = selectedSeverity,
            statusText = statusText,
            notes = if (notes.isBlank()) "Water level reported: $selectedSeverity" else notes,
            timestamp = System.currentTimeMillis(),
            severityLevel = level,
            photoUri = persistedPhotoData ?: selectedImageUri?.toString(),
            lat = resolvedLat,
            lon = resolvedLon,
            authorUsername = UserSession.getUsername(this),
            reporterName = UserSession.getUserName(this),
        )

        submitReportButton.isEnabled = false
        ReportRepository.addReport(applicationContext, newReport) { result ->
            runOnUiThread {
                val message = when {
                    result.smsBroadcastTriggered ->
                        "Multiple similar reports verified! Free SMS API warning broadcasted to nearby users."
                    result.submittedToServer ->
                        "Flood report submitted successfully."
                    else ->
                        "Report saved on this device."
                }
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            }
        }

        val intent = Intent(this, ProfileActivity::class.java)
        startActivity(intent)
        finish()
    }

    private class LocationDropdownAdapter(
        private val context: Context,
        private val presets: List<LocationSuggestionItem>
    ) : BaseAdapter(), Filterable {

        private var deviceItem: LocationSuggestionItem? = null
        private var displayedItems: List<LocationSuggestionItem> = presets

        fun setDeviceLocationItem(item: LocationSuggestionItem) {
            deviceItem = item
            displayedItems = buildFullList()
            notifyDataSetChanged()
        }

        private fun buildFullList(): List<LocationSuggestionItem> {
            val list = mutableListOf<LocationSuggestionItem>()
            deviceItem?.let { list.add(it) }
            for (p in presets) {
                if (deviceItem == null || !p.title.equals(deviceItem!!.title, ignoreCase = true)) {
                    list.add(p)
                }
            }
            return list
        }

        override fun getCount(): Int = displayedItems.size

        override fun getItem(position: Int): LocationSuggestionItem? =
            displayedItems.getOrNull(position)

        override fun getItemId(position: Int): Long = position.toLong()

        @SuppressLint("SetTextI18n")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val density = context.resources.displayMetrics.density
            val item = displayedItems[position]

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val hPad = (14 * density).toInt()
                val vPad = (10 * density).toInt()
                setPadding(hPad, vPad, hPad, vPad)
            }

            val titleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val badgeText = TextView(context).apply {
                text = if (item.isDeviceLocation) "🎯 " else "📍 "
                textSize = 13f
            }

            val titleText = TextView(context).apply {
                text = item.title
                setTextColor(if (item.isDeviceLocation) Color.parseColor("#0066CC") else Color.parseColor("#0A192F"))
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
            }

            titleRow.addView(badgeText)
            titleRow.addView(titleText)

            val subtitleText = TextView(context).apply {
                text = if (item.isDeviceLocation) {
                    "⭐ ${item.subtitle}"
                } else {
                    item.subtitle
                }
                setTextColor(Color.parseColor("#64748B"))
                textSize = 11f
                setPadding((20 * density).toInt(), (2 * density).toInt(), 0, 0)
            }

            row.addView(titleRow)
            row.addView(subtitleText)
            return row
        }

        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val query = constraint?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
                    val baseList = buildFullList()

                    val filtered = if (query.isEmpty()) {
                        baseList
                    } else {
                        val matchedLocal = baseList.filter {
                            it.title.lowercase(Locale.ROOT).contains(query) ||
                                it.subtitle.lowercase(Locale.ROOT).contains(query)
                        }.toMutableList()

                        // Also fetch live OpenStreetMap Nominatim suggestions when typing >= 3 chars
                        if (query.length >= 3) {
                            try {
                                val encoded = URLEncoder.encode("$query, Philippines", "UTF-8")
                                val url = URL("https://nominatim.openstreetmap.org/search?format=json&q=$encoded&limit=4")
                                val conn = url.openConnection() as HttpURLConnection
                                conn.requestMethod = "GET"
                                conn.setRequestProperty("User-Agent", "FloodGuardApp/1.0")
                                conn.connectTimeout = 2000
                                conn.readTimeout = 2000
                                if (conn.responseCode == 200) {
                                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                                    val arr = JSONArray(body)
                                    for (i in 0 until arr.length()) {
                                        val obj = arr.getJSONObject(i)
                                        val displayName = obj.optString("display_name")
                                        val lat = obj.optDouble("lat", SmsAlertBroadcastManager.DEFAULT_USER_LAT)
                                        val lon = obj.optDouble("lon", SmsAlertBroadcastManager.DEFAULT_USER_LON)
                                        if (displayName.isNotBlank()) {
                                            val shortTitle = displayName.split(",")
                                                .map { it.trim() }
                                                .take(3)
                                                .joinToString(", ")
                                            if (matchedLocal.none { it.title.equals(shortTitle, ignoreCase = true) }) {
                                                matchedLocal.add(
                                                    LocationSuggestionItem(
                                                        title = shortTitle,
                                                        subtitle = String.format(Locale.US, "Map Search Result (%.4f, %.4f)", lat, lon),
                                                        lat = lat,
                                                        lon = lon
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                conn.disconnect()
                            } catch (_: Exception) {
                            }
                        }

                        matchedLocal
                    }

                    return FilterResults().apply {
                        values = filtered
                        count = filtered.size
                    }
                }

                @Suppress("UNCHECKED_CAST")
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    displayedItems = (results?.values as? List<LocationSuggestionItem>) ?: buildFullList()
                    notifyDataSetChanged()
                }
            }
        }
    }
}

