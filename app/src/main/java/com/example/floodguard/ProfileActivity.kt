package com.example.floodguard

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Spinner
import android.widget.TextView
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Activity for displaying and editing the current user's profile.
 * Supports updating Full Name, Gender, Mobile Number, profile photo avatar,
 * password changes, viewing user's submitted reports, and logging out.
 */
class ProfileActivity : ComponentActivity() {

    private lateinit var avatarImage: ImageView
    private lateinit var addPhotoButton: LinearLayout
    private lateinit var profileNameInput: EditText
    private lateinit var profileGenderInput: EditText
    private lateinit var profileContactInput: EditText
    private lateinit var profileReportsContainer: LinearLayout

    private lateinit var btnEditProfile: RelativeLayout
    private lateinit var btnChangePassword: RelativeLayout
    private lateinit var btnLogout: LinearLayout
    private lateinit var menuIcon: ImageView

    private val selectPhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val persisted = ReportPhotoHelper.persistPickedPhoto(this, uri)
            val bitmap = ReportPhotoHelper.loadBitmap(this, persisted ?: uri.toString())
            if (bitmap != null) {
                ReportPhotoHelper.showAdjustAvatarDialog(this, bitmap) { adjustedBitmap, encodedDataUri ->
                    UserSession.setUserAvatar(this, encodedDataUri)
                    applyCircularAvatar(adjustedBitmap)
                    Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Unable to load selected photo", Toast.LENGTH_SHORT).show()
            }
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
        FloatingAlertManager.onActivityResumed(this)
        loadUserData()
        populateProfileReports()
    }

    override fun onPause() {
        super.onPause()
        FloatingAlertManager.onActivityPaused(this)
    }

    private fun initViews() {
        avatarImage = findViewById(R.id.avatarImage)
        addPhotoButton = findViewById(R.id.addPhotoButton)
        profileNameInput = findViewById(R.id.profileNameInput)
        profileGenderInput = findViewById(R.id.profileGenderInput)
        profileContactInput = findViewById(R.id.profileContactInput)
        profileReportsContainer = findViewById(R.id.profileReportsContainer)

        btnEditProfile = findViewById(R.id.btnEditProfile)
        btnChangePassword = findViewById(R.id.btnChangePassword)
        btnLogout = findViewById(R.id.btnLogout)
        menuIcon = findViewById(R.id.menuIcon)

        listOf(profileNameInput, profileGenderInput, profileContactInput).forEach { field ->
            field.keyListener = null
            field.isFocusable = false
            field.isFocusableInTouchMode = false
            field.isClickable = false
            field.isLongClickable = false
            field.isCursorVisible = false
        }
    }

    private fun applyCircularAvatar(bitmap: android.graphics.Bitmap) {
        val circular = ReportPhotoHelper.createCircularAvatarBitmap(bitmap, 280)
        avatarImage.setPadding(0, 0, 0, 0)
        avatarImage.background = null
        avatarImage.scaleType = ImageView.ScaleType.FIT_CENTER
        avatarImage.setImageBitmap(circular)
    }

    private fun resetDefaultAvatar() {
        val padPx = (16 * resources.displayMetrics.density).toInt()
        avatarImage.setPadding(padPx, padPx, padPx, padPx)
        avatarImage.setBackgroundResource(R.drawable.circle_avatar_bg)
        avatarImage.scaleType = ImageView.ScaleType.CENTER_INSIDE
        avatarImage.setImageResource(R.drawable.ic_profile)
    }

    private fun loadUserData() {
        profileNameInput.setText(UserSession.getUserName(this))
        profileGenderInput.setText(UserSession.getUserGender(this))
        profileContactInput.setText(UserSession.getUserContact(this))

        val savedAvatar = UserSession.getUserAvatar(this)
        val bitmap = ReportPhotoHelper.loadBitmap(this, savedAvatar)
        if (bitmap != null) {
            applyCircularAvatar(bitmap)
        } else {
            resetDefaultAvatar()
        }
    }

    private fun populateProfileReports() {
        profileReportsContainer.removeAllViews()

        val currentUsername = UserSession.getUsername(this)
        val currentFullName = UserSession.getUserName(this)

        val reportsList = ReportRepository.loadUserReports(this) {
            runOnUiThread { populateProfileReports() }
        }

        if (reportsList.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No flood reports submitted by you yet. Tap + Report Flood on Alerts to submit a report."
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

            cardView.addView(headerLayout)
            cardView.addView(subtitleText)

            if (!report.photoUri.isNullOrBlank()) {
                val photoBtn = TextView(this).apply {
                    text = "View Flood Photo"
                    setTextColor(resources.getColor(android.R.color.holo_blue_dark, theme))
                    textSize = 12f
                    paint.isFakeBoldText = true
                    val topMarginPx = (6 * resources.displayMetrics.density).toInt()
                    setPadding(0, topMarginPx, 0, 0)
                    setOnClickListener {
                        ReportPhotoHelper.showFloodPhotoDialog(this@ProfileActivity, report.photoUri!!, report.location)
                    }
                }
                cardView.addView(photoBtn)
            }

            if (report.isAuthoredBy(currentUsername, currentFullName)) {
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
                cardView.addView(actionRow)
            }

            profileReportsContainer.addView(cardView)
        }
    }

    private fun showEditReportDialog(report: FloodReport) {
        if (!report.isAuthoredBy(UserSession.getUsername(this), UserSession.getUserName(this))) {
            Toast.makeText(this, "Only the author who submitted this report can edit it.", Toast.LENGTH_SHORT).show()
            return
        }

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
                    val updated = ReportRepository.updateReport(this, report.id, newLoc, newSev, newNotes)
                    populateProfileReports()
                    if (updated) {
                        Toast.makeText(this, "Report updated successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Only the author can edit this report.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteReportDialog(report: FloodReport) {
        if (!report.isAuthoredBy(UserSession.getUsername(this), UserSession.getUserName(this))) {
            Toast.makeText(this, "Only the author who submitted this report can remove it.", Toast.LENGTH_SHORT).show()
            return
        }

        FloodGuardThemedDialog.showDeleteReport(this, report.location) {
            val removed = ReportRepository.removeReport(this, report.id)
            populateProfileReports()
            if (removed) {
                Toast.makeText(this, "Report removed successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Only the author can remove this report.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupListeners() {
        avatarImage.setOnClickListener {
            selectPhotoLauncher.launch("image/*")
        }

        addPhotoButton.setOnClickListener {
            selectPhotoLauncher.launch("image/*")
        }

        btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        btnLogout.setOnClickListener {
            confirmLogout()
        }

        menuIcon.setOnClickListener { view ->
            NavigationMenuHelper.showMenu(this, view)
        }
    }

    private fun showEditProfileDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null)
        val nameEdit = dialogView.findViewById<EditText>(R.id.dialogEditName)
        val btnMale = dialogView.findViewById<Button>(R.id.btnDialogMale)
        val btnFemale = dialogView.findViewById<Button>(R.id.btnDialogFemale)
        val contactEdit = dialogView.findViewById<EditText>(R.id.dialogEditContact)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        val btnSave = dialogView.findViewById<Button>(R.id.btnDialogSave)

        nameEdit.setText(profileNameInput.text.toString())
        contactEdit.setText(profileContactInput.text.toString())

        var selectedGender = profileGenderInput.text.toString().trim().ifEmpty { "Male" }

        fun updateGenderToggle(gender: String) {
            selectedGender = gender
            val isFemale = gender.equals("Female", ignoreCase = true)
            if (isFemale) {
                btnFemale.setBackgroundResource(R.drawable.btn_severity_selected)
                btnFemale.setTextColor(Color.WHITE)
                btnMale.setBackgroundResource(R.drawable.btn_severity_default)
                btnMale.setTextColor(Color.parseColor("#111827"))
            } else {
                btnMale.setBackgroundResource(R.drawable.btn_severity_selected)
                btnMale.setTextColor(Color.WHITE)
                btnFemale.setBackgroundResource(R.drawable.btn_severity_default)
                btnFemale.setTextColor(Color.parseColor("#111827"))
            }
        }

        updateGenderToggle(selectedGender)

        btnMale.setOnClickListener { updateGenderToggle("Male") }
        btnFemale.setOnClickListener { updateGenderToggle("Female") }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val newName = nameEdit.text.toString().trim()
            val newContact = contactEdit.text.toString().trim()
            val contactError = UserSession.getContactValidationError(newContact)

            if (newName.isEmpty() || newContact.isEmpty()) {
                Toast.makeText(this, "Name and contact cannot be empty", Toast.LENGTH_SHORT).show()
            } else if (contactError != null) {
                contactEdit.error = contactError
                contactEdit.requestFocus()
                Toast.makeText(this, contactError, Toast.LENGTH_LONG).show()
            } else {
                val username = UserSession.getUsername(this)
                val db = FloodGuardDatabase.getInstance(this)
                val existingWithContact = db.getUserByMobile(newContact)
                if (existingWithContact != null && !existingWithContact.username.equals(username, ignoreCase = true)) {
                    contactEdit.error = "This mobile number is already in use by another user."
                    contactEdit.requestFocus()
                    Toast.makeText(this, "⚠️ This mobile number is already assigned to another account.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val updated = db.updateUserProfile(username, newName, selectedGender, newContact)
                if (!updated) {
                    contactEdit.error = "This mobile number is already in use by another user."
                    contactEdit.requestFocus()
                    Toast.makeText(this, "⚠️ Failed to update: This mobile number is already assigned to another account.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                profileNameInput.setText(newName)
                profileGenderInput.setText(selectedGender)
                profileContactInput.setText(newContact)

                UserSession.updateProfile(this, newName, selectedGender, newContact)
                FloodGuardApiService.updateUserProfileInBackend(username, newName, selectedGender, newContact)
                populateProfileReports()
                Toast.makeText(this, "Profile information updated successfully!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showChangePasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null)
        val currentPass = dialogView.findViewById<EditText>(R.id.dialogCurrentPassInput)
        val toggleCurrentPass = dialogView.findViewById<ImageView>(R.id.toggleCurrentPassVisibility)
        val newPass = dialogView.findViewById<EditText>(R.id.dialogNewPassInput)
        val toggleNewPass = dialogView.findViewById<ImageView>(R.id.toggleNewPassVisibility)
        val confirmPass = dialogView.findViewById<EditText>(R.id.dialogConfirmPassInput)
        val toggleConfirmPass = dialogView.findViewById<ImageView>(R.id.toggleConfirmPassVisibility)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnPasswordCancel)
        val btnSave = dialogView.findViewById<Button>(R.id.btnPasswordSave)

        var isCurrentPassVisible = false
        toggleCurrentPass?.setOnClickListener {
            isCurrentPassVisible = !isCurrentPassVisible
            if (isCurrentPassVisible) {
                currentPass.transformationMethod = HideReturnsTransformationMethod.getInstance()
                toggleCurrentPass.setImageResource(R.drawable.ic_visibility)
            } else {
                currentPass.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleCurrentPass.setImageResource(R.drawable.ic_visibility_off)
            }
            currentPass.setSelection(currentPass.text.length)
        }

        var isNewPassVisible = false
        toggleNewPass?.setOnClickListener {
            isNewPassVisible = !isNewPassVisible
            if (isNewPassVisible) {
                newPass.transformationMethod = HideReturnsTransformationMethod.getInstance()
                toggleNewPass.setImageResource(R.drawable.ic_visibility)
            } else {
                newPass.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleNewPass.setImageResource(R.drawable.ic_visibility_off)
            }
            newPass.setSelection(newPass.text.length)
        }

        var isConfirmPassVisible = false
        toggleConfirmPass?.setOnClickListener {
            isConfirmPassVisible = !isConfirmPassVisible
            if (isConfirmPassVisible) {
                confirmPass.transformationMethod = HideReturnsTransformationMethod.getInstance()
                toggleConfirmPass.setImageResource(R.drawable.ic_visibility)
            } else {
                confirmPass.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleConfirmPass.setImageResource(R.drawable.ic_visibility_off)
            }
            confirmPass.setSelection(confirmPass.text.length)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val currentText = currentPass.text.toString().trim()
            val newText = newPass.text.toString().trim()
            val confirmText = confirmPass.text.toString().trim()

            when {
                currentText.isEmpty() || newText.isEmpty() || confirmText.isEmpty() -> {
                    Toast.makeText(this, "Please fill in all password fields", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                newText.length < 6 -> {
                    Toast.makeText(this, "New password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                newText != confirmText -> {
                    Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            val username = UserSession.getUsername(this)
            val db = FloodGuardDatabase.getInstance(this)
            val savedUser = db.getUserByUsername(username)
            val hashedCurrent = UserSession.hashPassword(currentText)

            if (savedUser != null && savedUser.password != hashedCurrent && savedUser.password != currentText) {
                Toast.makeText(this, "Current password is incorrect", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val hashedNew = UserSession.hashPassword(newText)
            if (savedUser != null) {
                db.updatePassword(username, hashedNew)
            } else {
                db.registerUser(
                    UserEntity(
                        fullName = UserSession.getUserName(this),
                        gender = UserSession.getUserGender(this),
                        mobile = UserSession.getUserContact(this),
                        username = username,
                        password = hashedNew,
                    )
                )
            }

            // Also update password on the backend server
            FloodGuardApiService.updatePasswordInBackend(username, currentText, newText)

            Toast.makeText(this, "Password changed successfully!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun confirmLogout() {
        FloodGuardThemedDialog.showLogout(this) {
            UserSession.logout(this)
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
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
        EdgeNavigationHelper.attachEdgeNavigation(this, EdgeNavigationHelper.NavTab.PROFILE)
    }
}
