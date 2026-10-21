package com.example.floodguard

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Activity for user authentication.
 * Supports credentials verification against local SQLite database with offline-first support,
 * fallback verification against the FloodGuard REST backend API, and secure password reset via mobile OTP.
 */
class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val usernameField = findViewById<EditText>(R.id.usernameField)
        val passwordField = findViewById<EditText>(R.id.passwordField)
        val togglePasswordVisibility = findViewById<ImageView>(R.id.togglePasswordVisibility)
        val forgotPasswordLink = findViewById<TextView>(R.id.forgotPasswordLink)
        val signInButton = findViewById<Button>(R.id.signInButton)
        val createAccountLink = findViewById<TextView>(R.id.createAccountLink)

        var isPasswordVisible = false
        togglePasswordVisibility?.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                // Show password (open eye)
                passwordField.transformationMethod = android.text.method.HideReturnsTransformationMethod.getInstance()
                togglePasswordVisibility.setImageResource(R.drawable.ic_visibility)
            } else {
                // Hide password (close eye with slash)
                passwordField.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_off)
            }
            passwordField.setSelection(passwordField.text.length)
        }

        signInButton?.setOnClickListener {
            val username = usernameField?.text?.toString().orEmpty().trim()
            val password = passwordField?.text?.toString().orEmpty()

            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show()
            } else {
                val db = FloodGuardDatabase.getInstance(this)
                val savedUser = db.getUserByUsername(username)
                val hashedInput = UserSession.hashPassword(password)

                // Check local database first
                if (savedUser != null && (savedUser.password == hashedInput || savedUser.password == password)) {
                    // Update to hashed password if it was plain
                    if (savedUser.password == password && savedUser.password != hashedInput) {
                        db.registerUser(savedUser.copy(password = hashedInput))
                    }
                    UserSession.setLoggedIn(
                        context = this,
                        loggedIn = true,
                        userName = savedUser.fullName,
                        username = savedUser.username,
                        userGender = savedUser.gender,
                        userContact = savedUser.mobile,
                    )
                    Toast.makeText(this, "Welcome back, ${savedUser.fullName}!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, DashboardActivity::class.java)
                    startActivity(intent)
                    finish()
                    return@setOnClickListener
                }

                // If not found locally or password mismatch, verify with backend API
                Toast.makeText(this, "Verifying credentials...", Toast.LENGTH_SHORT).show()
                FloodGuardApiService.loginUserInBackend(username, password) { remoteUser ->
                    runOnUiThread {
                        if (remoteUser != null) {
                            val userToSave = remoteUser.copy(password = hashedInput)
                            db.registerUser(userToSave)
                            UserSession.setLoggedIn(
                                context = this,
                                loggedIn = true,
                                userName = remoteUser.fullName,
                                username = remoteUser.username,
                                userGender = remoteUser.gender,
                                userContact = remoteUser.mobile,
                            )
                            Toast.makeText(this, "Welcome back, ${remoteUser.fullName}!", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, DashboardActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            if (savedUser != null) {
                                Toast.makeText(this, "Incorrect password. Please try again.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this, "Account not found. Please register first.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        forgotPasswordLink?.setOnClickListener {
            showForgotPasswordDialog()
        }

        createAccountLink?.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Displays a multi-step dialog for resetting a forgotten password using mobile OTP verification.
     * Step 1: User enters username -> verifies associated mobile number -> dispatches OTP.
     * Step 2: User enters the 6-digit OTP code and a new password -> updates SQLite and Backend.
     */
    private fun showForgotPasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null)
        val layoutStepUser = dialogView.findViewById<LinearLayout>(R.id.layoutForgotStepUser)
        val layoutStepReset = dialogView.findViewById<LinearLayout>(R.id.layoutForgotStepReset)

        val inputUsername = dialogView.findViewById<EditText>(R.id.forgotUsernameInput)
        val userStatusText = dialogView.findViewById<TextView>(R.id.forgotUserStatusMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnForgotCancel)
        val btnNext = dialogView.findViewById<Button>(R.id.btnForgotNext)

        val otpNoticeText = dialogView.findViewById<TextView>(R.id.forgotOtpNoticeText)
        val inputOtp = dialogView.findViewById<EditText>(R.id.forgotOtpCodeInput)
        val inputNewPass = dialogView.findViewById<EditText>(R.id.forgotNewPassInput)
        val inputConfirmPass = dialogView.findViewById<EditText>(R.id.forgotConfirmPassInput)
        val toggleForgotNewPass = dialogView.findViewById<ImageView>(R.id.toggleForgotNewPassVisibility)
        val toggleForgotConfirmPass = dialogView.findViewById<ImageView>(R.id.toggleForgotConfirmPassVisibility)

        var isForgotNewPassVisible = false
        toggleForgotNewPass?.setOnClickListener {
            isForgotNewPassVisible = !isForgotNewPassVisible
            if (isForgotNewPassVisible) {
                inputNewPass.transformationMethod = android.text.method.HideReturnsTransformationMethod.getInstance()
                toggleForgotNewPass.setImageResource(R.drawable.ic_visibility)
            } else {
                inputNewPass.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                toggleForgotNewPass.setImageResource(R.drawable.ic_visibility_off)
            }
            inputNewPass.setSelection(inputNewPass.text.length)
        }

        var isForgotConfirmPassVisible = false
        toggleForgotConfirmPass?.setOnClickListener {
            isForgotConfirmPassVisible = !isForgotConfirmPassVisible
            if (isForgotConfirmPassVisible) {
                inputConfirmPass.transformationMethod = android.text.method.HideReturnsTransformationMethod.getInstance()
                toggleForgotConfirmPass.setImageResource(R.drawable.ic_visibility)
            } else {
                inputConfirmPass.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                toggleForgotConfirmPass.setImageResource(R.drawable.ic_visibility_off)
            }
            inputConfirmPass.setSelection(inputConfirmPass.text.length)
        }

        val resetStatusText = dialogView.findViewById<TextView>(R.id.forgotResetStatusMessage)
        val btnBack = dialogView.findViewById<Button>(R.id.btnForgotBack)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnForgotSubmit)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        var resolvedUser: UserEntity? = null

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnNext.setOnClickListener {
            val enteredUser = inputUsername.text.toString().trim()
            if (enteredUser.isEmpty()) {
                userStatusText.text = "Please enter your username."
                userStatusText.setTextColor(Color.parseColor("#DC2626"))
                return@setOnClickListener
            }

            btnNext.isEnabled = false
            userStatusText.text = "Looking up account..."
            userStatusText.setTextColor(Color.parseColor("#0284C7"))

            val db = FloodGuardDatabase.getInstance(this)
            val localUser = db.getUserByUsername(enteredUser)

            fun onUserFound(user: UserEntity) {
                resolvedUser = user
                val mobile = user.mobile
                if (mobile.isBlank()) {
                    btnNext.isEnabled = true
                    userStatusText.text = "No mobile number linked with this account."
                    userStatusText.setTextColor(Color.parseColor("#DC2626"))
                    return
                }

                userStatusText.text = "Sending verification code via SMS..."
                userStatusText.setTextColor(Color.parseColor("#0284C7"))

                FirebaseAuthOtpHelper.sendOtp(
                    activity = this,
                    mobileNumber = mobile,
                    callback = object : FirebaseAuthOtpHelper.OtpCallback {
                        override fun onCodeSent(formattedPhone: String) {
                            btnNext.isEnabled = true
                            layoutStepUser.visibility = View.GONE
                            layoutStepReset.visibility = View.VISIBLE
                            otpNoticeText.text = "We sent an OTP code to $formattedPhone"
                        }

                        override fun onAutoVerified() {
                            btnNext.isEnabled = true
                            layoutStepUser.visibility = View.GONE
                            layoutStepReset.visibility = View.VISIBLE
                            inputOtp.setText("AUTO-VERIFIED")
                            inputOtp.isEnabled = false
                        }

                        override fun onError(message: String) {
                            btnNext.isEnabled = true
                            userStatusText.text = message
                            userStatusText.setTextColor(Color.parseColor("#DC2626"))
                        }

                        override fun onFallbackCodeGenerated(code: String) {
                            runOnUiThread {
                                AlertDialog.Builder(this@LoginActivity)
                                    .setTitle("FloodGuard Security Code")
                                    .setMessage("Firebase SMS is not yet activated on Firebase Console.\n\nYour 6-digit verification code is:\n\n$code\n\nValid for 5 minutes.")
                                    .setPositiveButton("Enter Code") { _, _ ->
                                        inputOtp.setText(code)
                                    }
                                    .setNegativeButton("Close", null)
                                    .show()
                            }
                        }
                    }
                )
            }

            if (localUser != null) {
                onUserFound(localUser)
            } else {
                // Try fetching users from backend to see if user exists remotely
                FloodGuardApiService.syncUsersFromBackend { remoteUsers ->
                    runOnUiThread {
                        val match = remoteUsers.find { it.username.equals(enteredUser, ignoreCase = true) }
                        if (match != null) {
                            db.registerUser(match)
                            onUserFound(match)
                        } else {
                            btnNext.isEnabled = true
                            userStatusText.text = "Account '$enteredUser' not found. Please register."
                            userStatusText.setTextColor(Color.parseColor("#DC2626"))
                        }
                    }
                }
            }
        }

        btnBack.setOnClickListener {
            layoutStepReset.visibility = View.GONE
            layoutStepUser.visibility = View.VISIBLE
            resetStatusText.visibility = View.GONE
        }

        btnSubmit.setOnClickListener {
            val user = resolvedUser ?: return@setOnClickListener
            val otpCode = inputOtp.text.toString().trim()
            val newPass = inputNewPass.text.toString()
            val confirmPass = inputConfirmPass.text.toString()

            if (inputOtp.isEnabled && otpCode.length < 6) {
                resetStatusText.text = "Please enter the 6-digit OTP code."
                resetStatusText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (newPass.length < 6) {
                resetStatusText.text = "New password must be at least 6 characters."
                resetStatusText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (newPass != confirmPass) {
                resetStatusText.text = "Passwords do not match."
                resetStatusText.visibility = View.VISIBLE
                return@setOnClickListener
            }

            btnSubmit.isEnabled = false
            resetStatusText.text = "Verifying code & resetting password..."
            resetStatusText.setTextColor(Color.parseColor("#0284C7"))
            resetStatusText.visibility = View.VISIBLE

            fun performPasswordUpdate() {
                val db = FloodGuardDatabase.getInstance(this)
                val newHash = UserSession.hashPassword(newPass)
                db.updatePassword(user.username, newHash)

                FloodGuardApiService.resetPasswordInBackend(user.username, newPass) { _, _ ->
                    // Backend synchronization handled in background
                }

                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, "✅ Password reset successfully! You can now log in.", Toast.LENGTH_LONG).show()
                }
            }

            if (!inputOtp.isEnabled) {
                // Already auto-verified
                performPasswordUpdate()
            } else {
                FirebaseAuthOtpHelper.verifyCode(
                    enteredCode = otpCode,
                    onSuccess = {
                        performPasswordUpdate()
                    },
                    onError = { errMsg ->
                        btnSubmit.isEnabled = true
                        resetStatusText.text = errMsg
                        resetStatusText.setTextColor(Color.parseColor("#DC2626"))
                        resetStatusText.visibility = View.VISIBLE
                    }
                )
            }
        }

        dialog.show()
    }
}
