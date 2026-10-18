package com.example.floodguard

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale
import kotlin.concurrent.thread

class ReportFloodActivity : ComponentActivity() {

    private lateinit var photoPickerBox: FrameLayout
    private lateinit var photoPreview: ImageView
    private lateinit var photoPlaceholder: LinearLayout
    private lateinit var locationInput: EditText
    private lateinit var btnRefreshLocation: TextView
    private lateinit var notesInput: EditText
    private lateinit var chkSendSmsOnSubmit: CheckBox
    private lateinit var tvRemainingReports: TextView
    private lateinit var submitReportButton: Button

    private lateinit var btnAnkleDeep: Button
    private lateinit var btnKneeDeep: Button
    private lateinit var btnWaistDeep: Button
    private lateinit var btnImpassable: Button

    private var selectedSeverity = "Waist-Deep"
    private var selectedImageUri: Uri? = null

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            photoPreview.setImageURI(uri)
            photoPreview.visibility = View.VISIBLE
            photoPlaceholder.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_flood)

        initViews()
        setupSeverityButtons()
        setupPhotoPicker()
        autoDetectMapLocation()
        updateRemainingReportsUI()

        submitReportButton.setOnClickListener {
            submitReport()
        }

        btnRefreshLocation.setOnClickListener {
            autoDetectMapLocation()
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

    private fun initViews() {
        photoPickerBox = findViewById(R.id.photoPickerBox)
        photoPreview = findViewById(R.id.photoPreview)
        photoPlaceholder = findViewById(R.id.photoPlaceholder)
        locationInput = findViewById(R.id.locationInput)
        btnRefreshLocation = findViewById(R.id.btnRefreshLocation)
        notesInput = findViewById(R.id.notesInput)
        chkSendSmsOnSubmit = findViewById(R.id.chkSendSmsOnSubmit)
        tvRemainingReports = findViewById(R.id.tvRemainingReports)
        submitReportButton = findViewById(R.id.submitReportButton)

        btnAnkleDeep = findViewById(R.id.btnAnkleDeep)
        btnKneeDeep = findViewById(R.id.btnKneeDeep)
        btnWaistDeep = findViewById(R.id.btnWaistDeep)
        btnImpassable = findViewById(R.id.btnImpassable)
    }

    private fun updateRemainingReportsUI() {
        val remaining = UserSession.getRemainingReportsToday(this)
        val max = UserSession.MAX_DAILY_REPORTS
        if (remaining > 0) {
            tvRemainingReports.text = "Natitirang Report Ngayong Araw: $remaining / $max (Resets daily)"
            tvRemainingReports.setTextColor(Color.parseColor("#0066CC"))
            submitReportButton.text = "SUBMIT REPORT ($remaining LEFT TODAY)"
            submitReportButton.alpha = 1.0f
        } else {
            tvRemainingReports.text = "Naabot na ang limit ngayong araw: 0 / $max natitira (Magre-reset bukas)"
            tvRemainingReports.setTextColor(Color.parseColor("#D32F2F"))
            submitReportButton.text = "DAILY LIMIT REACHED (0 / $max)"
            submitReportButton.alpha = 0.6f
        }
    }

    private fun autoDetectMapLocation() {
        locationInput.setText("Detecting location on map...")
        val lat = 14.6958
        val lon = 121.1207

        thread {
            try {
                val geocoder = Geocoder(this@ReportFloodActivity, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)

                val detectedLocation = if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val feature = addr.thoroughfare ?: addr.featureName ?: "Gen. Luna Ave"
                    val subLocality = addr.subLocality ?: addr.locality ?: "San Mateo"
                    "Auto Detected - $feature, $subLocality, Rizal"
                } else {
                    "Auto Detected - Gen. Luna Ave, San Mateo, Rizal"
                }

                runOnUiThread {
                    locationInput.setText(detectedLocation)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    locationInput.setText("Auto Detected - Gen. Luna Ave, San Mateo, Rizal")
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

    private fun getCoordinatesForLocation(location: String): Pair<Double, Double> {
        val q = location.lowercase(Locale.ROOT)
        return when {
            q.contains("ampid") -> Pair(14.6885, 121.1120)
            q.contains("timberland") || q.contains("malanday") -> Pair(14.6812, 121.1620)
            q.contains("dulong") -> Pair(14.7120, 121.1280)
            q.contains("banaba") -> Pair(14.6750, 121.1080)
            q.contains("maly") -> Pair(14.7200, 121.1350)
            q.contains("santa ana") || q.contains("sta ana") || q.contains("sta. ana") -> Pair(14.6920, 121.1180)
            q.contains("silangan") -> Pair(14.6850, 121.1300)
            q.contains("patiis") -> Pair(14.6980, 121.1380)
            q.contains("guitnang") -> Pair(14.6958, 121.1207)
            q.contains("batasan") -> Pair(14.6818, 121.1095)
            q.contains("del pilar") -> Pair(14.6965, 121.1215)
            q.contains("felix") -> Pair(14.6150, 121.1020)
            else -> Pair(14.6958, 121.1207)
        }
    }

    private fun isGibberishText(text: String): Boolean {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return false

        val lettersOnly = cleaned.filter { it.isLetter() }.lowercase(Locale.ROOT)
        if (lettersOnly.length < 3) return true

        // 4 or more identical characters in a row (e.g. "aaaa", "zzzz", "1111")
        if (Regex("(.)\\1{3,}", RegexOption.IGNORE_CASE).containsMatchIn(cleaned)) {
            return true
        }

        // Common keyboard mash or troll patterns
        val mashPatterns = listOf(
            "asdf", "qwerty", "zxcv", "hjkl", "bnm", "uiop",
            "hahaha", "hehehe", "hihihi", "huhuhu", "lololol",
            "walang kwenta", "trip lang", "joke lang", "testing lang", "test lang", "blablabla", "blahblah"
        )
        val lowerText = cleaned.lowercase(Locale.ROOT)
        if (mashPatterns.any { lowerText.contains(it) }) {
            return true
        }

        // 5 or more consecutive consonants (e.g. "dfghjk", "xzkjhq")
        if (Regex("[bcdfghjklmnpqrstvwxyz]{5,}").containsMatchIn(lettersOnly)) {
            return true
        }

        // Vowel ratio check: Tagalog and English words normally have at least 20% vowels
        val vowelCount = lettersOnly.count { it in "aeiou" }
        val vowelRatio = vowelCount.toDouble() / lettersOnly.length.toDouble()
        if (lettersOnly.length >= 5 && (vowelRatio < 0.18 || vowelRatio > 0.85)) {
            return true
        }

        return false
    }

    private fun validateReportContent(location: String, notes: String): String? {
        val profanityWords = listOf(
            "putangina", "putang ina", "tangina", "taena", "puta", "gago", "gaga",
            "tanga", "bobo", "ulol", "tarantado", "kupal", "kantot", "iyot", "tite",
            "titi", "puke", "pepe", "pekpek", "burat", "tamod", "jakol", "jabol",
            "pokpok", "punyeta", "leche", "pakshet", "buwisit", "bwisit", "hindot",
            "inutil", "hayop ka", "kingina", "shunga", "engot", "supot", "bayag",
            "fuck", "fck", "fuk", "shit", "bitch", "asshole", "dick", "cock",
            "pussy", "porn", "sex", "nude", "boobs", "bastard", "slut", "whore", "cunt"
        )

        val combined = "$location $notes".lowercase(Locale.ROOT)
        for (badWord in profanityWords) {
            val regex = Regex("\\b${Regex.escape(badWord)}\\b", RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(combined) || combined.contains(badWord)) {
                return "Hindi appropriate ang iyong report dahil may nakitang bastos o hindi magandang salita. Mangyaring maglagay ng maayos na impormasyon."
            }
        }

        if (location.length < 3 || isGibberishText(location)) {
            return "Hindi appropriate o walang katuturan ang inilagay na Location. Mangyaring ilagay ang tamang pangalan ng kalsada o barangay sa San Mateo."
        }

        if (notes.isNotBlank() && (notes.length < 4 || isGibberishText(notes))) {
            return "Hindi appropriate o walang sense ang inilagay sa Notes. Mangyaring maglagay ng malinaw at totoong detalye tungkol sa baha."
        }

        return null
    }

    private fun submitReport() {
        // 1. Check 4-report daily limit per user
        val remainingBefore = UserSession.getRemainingReportsToday(this)
        if (remainingBefore <= 0) {
            AlertDialog.Builder(this)
                .setTitle("Daily Report Limit Reached (4 / 4)")
                .setMessage("Naabot mo na ang limit na 4 flood reports para sa araw na ito. Bukas ay awtomatikong magre-reset ang iyong report limit.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val rawLocation = locationInput.text.toString().replace("Auto Detected - ", "").trim()
        val loc = if (rawLocation.isBlank()) "San Mateo, Rizal" else rawLocation
        val notes = notesInput.text.toString().trim()

        // 2. Validate report content (Profanity & Nonsense Filter)
        val validationError = validateReportContent(loc, notes)
        if (validationError != null) {
            AlertDialog.Builder(this)
                .setTitle("Hindi Appropriate ang Report")
                .setMessage(validationError)
                .setPositiveButton("Ayusin ang Report", null)
                .show()
            return
        }

        val (statusText, level) = when (selectedSeverity) {
            "Impassable", "Waist-Deep" -> Pair("Not Passable", SeverityLevel.NOT_PASSABLE)
            "Knee-Deep" -> Pair("Heavy vehicles only", SeverityLevel.HEAVY_VEHICLES_ONLY)
            else -> Pair("Cleared", SeverityLevel.CLEARED)
        }

        val (reportLat, reportLon) = getCoordinatesForLocation(loc)

        val newReport = FloodReport(
            location = loc,
            severity = selectedSeverity,
            statusText = statusText,
            notes = if (notes.isBlank()) "Water level reported: $selectedSeverity" else notes,
            timestamp = System.currentTimeMillis(),
            severityLevel = level,
            photoUri = selectedImageUri?.toString(),
            lat = reportLat,
            lon = reportLon,
        )

        ReportRepository.addReport(this, newReport)

        // 3. Deduct 1 from user's daily report quota and show remaining reports
        val remainingAfter = UserSession.incrementDailyReportCount(this)
        updateRemainingReportsUI()

        if (chkSendSmsOnSubmit.isChecked) {
            SmsHelper.broadcastSmsToAllUsers(
                context = this,
                location = loc,
                severity = selectedSeverity,
                notes = notes
            )
        }

        Toast.makeText(
            this,
            "Flood report submitted! Natitirang report ngayong araw: $remainingAfter / ${UserSession.MAX_DAILY_REPORTS}",
            Toast.LENGTH_LONG
        ).show()

        val intent = Intent(this, AlertsActivity::class.java)
        startActivity(intent)
        finish()
    }
}
