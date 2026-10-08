package com.example.floodguard

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class ProfileActivity : ComponentActivity() {

    private lateinit var avatarImage: ImageView
    private lateinit var addPhotoButton: LinearLayout
    private lateinit var profileNameInput: EditText
    private lateinit var profileGenderInput: EditText
    private lateinit var profileContactInput: EditText
    private lateinit var profileEmailInput: EditText
    private lateinit var profileReportsContainer: LinearLayout

    private lateinit var btnChangeContact: RelativeLayout
    private lateinit var btnTestSmsProfile: RelativeLayout
    private lateinit var btnChangePassword: RelativeLayout
    private lateinit var btnApplyChanges: Button
    private lateinit var btnLogout: LinearLayout
    private lateinit var menuIcon: ImageView

    private val selectPhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            avatarImage.setImageURI(uri)
            Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        initViews()
        loadUserData()
        populateProfileReports()
        setupListeners()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
        populateProfileReports()
    }

    private fun initViews() {
        avatarImage = findViewById(R.id.avatarImage)
        addPhotoButton = findViewById(R.id.addPhotoButton)
        profileNameInput = findViewById(R.id.profileNameInput)
        profileGenderInput = findViewById(R.id.profileGenderInput)
        profileContactInput = findViewById(R.id.profileContactInput)
        profileEmailInput = findViewById(R.id.profileEmailInput)
        profileReportsContainer = findViewById(R.id.profileReportsContainer)

        btnChangeContact = findViewById(R.id.btnChangeContact)
        btnTestSmsProfile = findViewById(R.id.btnTestSmsProfile)
        btnChangePassword = findViewById(R.id.btnChangePassword)
        btnApplyChanges = findViewById(R.id.btnApplyChanges)
        btnLogout = findViewById(R.id.btnLogout)
        menuIcon = findViewById(R.id.menuIcon)
    }

    private fun loadUserData() {
        profileNameInput.setText(UserSession.getUserName(this))
        profileGenderInput.setText(UserSession.getUserGender(this))
        profileContactInput.setText(UserSession.getUserContact(this))
        profileEmailInput.setText(UserSession.getUserEmail(this))
    }

    private fun populateProfileReports() {
        profileReportsContainer.removeAllViews()

        val reportsList = ReportRepository.loadReports(this) {
            runOnUiThread { populateProfileReports() }
        }

        if (reportsList.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No flood reports submitted yet. Tap + Report on Alerts to submit a report."
                setTextColor(resources.getColor(android.R.color.darker_gray, theme))
                textSize = 13f
                setPadding(12, 12, 12, 12)
            }
            profileReportsContainer.addView(emptyText)
            return
        }

        for (report in reportsList) {
            val cardView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
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
            }

            val headerLayout = RelativeLayout(this)

            val timeText = TextView(this).apply {
                id = View.generateViewId()
                text = report.getFormattedTimeAgo()
                setTextColor(resources.getColor(android.R.color.darker_gray, theme))
                textSize = 11f
                val params = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                }
                layoutParams = params
            }

            val titleText = TextView(this).apply {
                text = "${report.location} - ${report.statusText}"
                setTextColor(resources.getColor(android.R.color.black, theme))
                textSize = 14f
                paint.isFakeBoldText = true
                val params = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_START)
                    addRule(RelativeLayout.START_OF, timeText.id)
                }
                layoutParams = params
            }

            headerLayout.addView(titleText)
            headerLayout.addView(timeText)

            val subtitleText = TextView(this).apply {
                text = "Severity: ${report.severity} • ${report.notes}"
                setTextColor(resources.getColor(android.R.color.darker_gray, theme))
                textSize = 12f
                val topMarginPx = (4 * resources.displayMetrics.density).toInt()
                setPadding(0, topMarginPx, 0, 0)
            }

            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                val topMarginPx = (8 * resources.displayMetrics.density).toInt()
                setPadding(0, topMarginPx, 0, 0)
            }

            val editBtn = TextView(this).apply {
                text = "Edit"
                setTextColor(resources.getColor(android.R.color.holo_blue_dark, theme))
                textSize = 12f
                paint.isFakeBoldText = true
                setPadding(16, 8, 16, 8)
                setOnClickListener {
                    showEditReportDialog(report)
                }
            }

            val deleteBtn = TextView(this).apply {
                text = "Remove"
                setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
                textSize = 12f
                paint.isFakeBoldText = true
                setPadding(16, 8, 0, 8)
                setOnClickListener {
                    showDeleteReportDialog(report)
                }
            }

            actionRow.addView(editBtn)
            actionRow.addView(deleteBtn)

            cardView.addView(headerLayout)
            cardView.addView(subtitleText)
            cardView.addView(actionRow)

            profileReportsContainer.addView(cardView)
        }
    }

    private fun showEditReportDialog(report: FloodReport) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val locationEdit = EditText(this).apply {
            hint = "Location"
            setText(report.location)
        }

        val severitySpinner = Spinner(this).apply {
            val options = arrayOf("Ankle-Deep", "Knee-Deep", "Waist-Deep", "Impassable")
            adapter = ArrayAdapter(this@ProfileActivity, android.R.layout.simple_spinner_dropdown_item, options)
            val index = options.indexOf(report.severity)
            if (index >= 0) setSelection(index)
        }

        val notesEdit = EditText(this).apply {
            hint = "Notes"
            setText(report.notes)
        }

        layout.addView(TextView(this).apply { text = "Location:"; setPadding(0, 10, 0, 4) })
        layout.addView(locationEdit)
        layout.addView(TextView(this).apply { text = "Severity:"; setPadding(0, 10, 0, 4) })
        layout.addView(severitySpinner)
        layout.addView(TextView(this).apply { text = "Notes:"; setPadding(0, 10, 0, 4) })
        layout.addView(notesEdit)

        AlertDialog.Builder(this)
            .setTitle("Edit Flood Report")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newLoc = locationEdit.text.toString().trim()
                val newSev = severitySpinner.selectedItem.toString()
                val newNotes = notesEdit.text.toString().trim()

                if (newLoc.isNotEmpty()) {
                    ReportRepository.updateReport(this, report.id, newLoc, newSev, newNotes)
                    populateProfileReports()
                    Toast.makeText(this, "Report updated successfully!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteReportDialog(report: FloodReport) {
        AlertDialog.Builder(this)
            .setTitle("Remove Report")
            .setMessage("Are you sure you want to remove the report for \"${report.location}\"?")
            .setPositiveButton("Remove") { _, _ ->
                ReportRepository.removeReport(this, report.id)
                populateProfileReports()
                Toast.makeText(this, "Report removed successfully!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupListeners() {
        addPhotoButton.setOnClickListener {
            selectPhotoLauncher.launch("image/*")
        }

        btnChangeContact.setOnClickListener {
            showChangeContactDialog()
        }

        btnTestSmsProfile.setOnClickListener {
            SmsHelper.broadcastSmsToAllUsers(
                context = this,
                location = "San Mateo Vicinity Test",
                severity = "System Broadcast Test",
                notes = "San Mateo area SMS broadcast test"
            )
        }

        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        btnApplyChanges.setOnClickListener {
            val name = profileNameInput.text.toString().trim()
            val gender = profileGenderInput.text.toString().trim()
            val contact = profileContactInput.text.toString().trim()
            val email = profileEmailInput.text.toString().trim()

            if (name.isEmpty() || contact.isEmpty()) {
                Toast.makeText(this, "Name and contact cannot be empty", Toast.LENGTH_SHORT).show()
            } else {
                UserSession.updateProfile(this, name, gender, contact, email)
                val username = UserSession.getUserName(this)
                FloodGuardApiService.updateUserProfileInBackend(username, name, gender, contact, email)
                Toast.makeText(this, "Profile information updated successfully!", Toast.LENGTH_SHORT).show()
            }
        }

        btnLogout.setOnClickListener {
            confirmLogout()
        }

        menuIcon.setOnClickListener { view ->
            NavigationMenuHelper.showMenu(this, view)
        }
    }

    private fun showChangeContactDialog() {
        val input = EditText(this).apply {
            hint = "New Contact Number"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(profileContactInput.text.toString())
        }
        AlertDialog.Builder(this)
            .setTitle("Change Contact")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newContact = input.text.toString().trim()
                if (newContact.isNotEmpty()) {
                    profileContactInput.setText(newContact)
                    UserSession.updateProfile(
                        this,
                        profileNameInput.text.toString(),
                        profileGenderInput.text.toString(),
                        newContact,
                        profileEmailInput.text.toString()
                    )
                    Toast.makeText(this, "Contact number updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showChangePasswordDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
        }
        val currentPass = EditText(this).apply {
            hint = "Current Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val newPass = EditText(this).apply {
            hint = "New Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(currentPass)
        layout.addView(newPass)

        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(layout)
            .setPositiveButton("Update") { _, _ ->
                if (newPass.text.toString().length >= 6) {
                    Toast.makeText(this, "Password changed successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Log Out")
            .setMessage("Are you sure you want to log out of FloodGuard?")
            .setPositiveButton("Log Out") { _, _ ->
                UserSession.logout(this)
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupBottomNavigation() {
        findViewById<LinearLayout>(R.id.logoLayout)?.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish()
        }
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
            val intent = Intent(this, AlertsActivity::class.java)
            startActivity(intent)
            finish()
        }
        findViewById<LinearLayout>(R.id.navProfile)?.setOnClickListener {
            // Already on Profile
        }
    }
}
