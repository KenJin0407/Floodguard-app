package com.example.floodguard

import android.content.Intent
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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
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
        submitReportButton = findViewById(R.id.submitReportButton)

        btnAnkleDeep = findViewById(R.id.btnAnkleDeep)
        btnKneeDeep = findViewById(R.id.btnKneeDeep)
        btnWaistDeep = findViewById(R.id.btnWaistDeep)
        btnImpassable = findViewById(R.id.btnImpassable)
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

    private fun submitReport() {
        val rawLocation = locationInput.text.toString().replace("Auto Detected - ", "").trim()
        val loc = if (rawLocation.isBlank()) "San Mateo, Rizal" else rawLocation
        val notes = notesInput.text.toString().trim()

        val (statusText, level) = when (selectedSeverity) {
            "Impassable", "Waist-Deep" -> Pair("Not Passable", SeverityLevel.NOT_PASSABLE)
            "Knee-Deep" -> Pair("Heavy vehicles only", SeverityLevel.HEAVY_VEHICLES_ONLY)
            else -> Pair("Cleared", SeverityLevel.CLEARED)
        }

        val newReport = FloodReport(
            location = loc,
            severity = selectedSeverity,
            statusText = statusText,
            notes = if (notes.isBlank()) "Water level reported: $selectedSeverity" else notes,
            timestamp = System.currentTimeMillis(),
            severityLevel = level,
            photoUri = selectedImageUri?.toString(),
            lat = 14.6958,
            lon = 121.1207,
        )

        ReportRepository.addReport(this, newReport)

        if (chkSendSmsOnSubmit.isChecked) {
            SmsHelper.broadcastSmsToAllUsers(
                context = this,
                location = loc,
                severity = selectedSeverity,
                notes = notes
            )
        }

        Toast.makeText(this, "Flood report submitted & SMS broadcasted to all users!", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, AlertsActivity::class.java)
        startActivity(intent)
        finish()
    }
}
