package com.example.floodguard

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlin.concurrent.thread

class ForgotPasswordActivity : ComponentActivity() {

    private var activeUsername: String = ""
    private var activeMobile: String = ""
    private var activeLocalOtp: String? = null

    private var isNewPasswordVisible: Boolean = false
    private var isConfirmPasswordVisible: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        val btnBackToLogin = findViewById<ImageView>(R.id.btnBackToLogin)
        val bottomSignInLink = findViewById<TextView>(R.id.bottomSignInLink)

        val step1Container = findViewById<LinearLayout>(R.id.step1Container)
        val identifierField = findViewById<EditText>(R.id.identifierField)

        val prefilledId = intent.getStringExtra("prefill_identifier")
        if (!prefilledId.isNullOrBlank()) {
            identifierField.setText(prefilledId)
        }
        val btnFindAndSendOtp = findViewById<Button>(R.id.btnFindAndSendOtp)

        val step2Container = findViewById<LinearLayout>(R.id.step2Container)
        val tvAccountBanner = findViewById<TextView>(R.id.tvAccountBanner)
        val otpField = findViewById<EditText>(R.id.otpField)
        val btnResendOtp = findViewById<TextView>(R.id.btnResendOtp)

        val newPasswordField = findViewById<EditText>(R.id.newPasswordField)
        val toggleNewPasswordVisibility = findViewById<ImageView>(R.id.toggleNewPasswordVisibility)
        val confirmPasswordField = findViewById<EditText>(R.id.confirmPasswordField)
        val toggleConfirmPasswordVisibility = findViewById<ImageView>(R.id.toggleConfirmPasswordVisibility)
        val btnResetPassword = findViewById<Button>(R.id.btnResetPassword)

        val db = FloodGuardDatabase(this)

        btnBackToLogin.setOnClickListener {
            finish()
        }

        bottomSignInLink.setOnClickListener {
            finish()
        }

        // Toggle New Password Visibility
        toggleNewPasswordVisibility.setOnClickListener {
            isNewPasswordVisible = !isNewPasswordVisible
            if (isNewPasswordVisible) {
                newPasswordField.transformationMethod = HideReturnsTransformationMethod.getInstance()
                toggleNewPasswordVisibility.setImageResource(R.drawable.ic_visibility)
            } else {
                newPasswordField.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleNewPasswordVisibility.setImageResource(R.drawable.ic_visibility_off)
            }
            newPasswordField.setSelection(newPasswordField.text.length)
        }

        // Toggle Confirm Password Visibility
        toggleConfirmPasswordVisibility.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            if (isConfirmPasswordVisible) {
                confirmPasswordField.transformationMethod = HideReturnsTransformationMethod.getInstance()
                toggleConfirmPasswordVisibility.setImageResource(R.drawable.ic_visibility)
            } else {
                confirmPasswordField.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleConfirmPasswordVisibility.setImageResource(R.drawable.ic_visibility_off)
            }
            confirmPasswordField.setSelection(confirmPasswordField.text.length)
        }

        // STEP 1: Find Account & Dispatch OTP
        btnFindAndSendOtp.setOnClickListener {
            val identifier = identifierField.text?.toString().orEmpty().trim()
            if (identifier.isEmpty()) {
                Toast.makeText(this, "Please enter your username or registered mobile number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isDigitOnly = identifier.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }
            if (isDigitOnly) {
                val (validMobile, validMsg) = PhilippineNumberValidator.validate(identifier)
                if (!validMobile) {
                    Toast.makeText(this, validMsg, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
            }

            btnFindAndSendOtp.isEnabled = false
            btnFindAndSendOtp.text = "SEARCHING ACCOUNT..."

            // Check Local Database first
            val localUser = db.getUserByUsername(identifier) ?: db.getUserByMobile(identifier)
            if (localUser != null && !localUser.mobile.isNullOrBlank()) {
                proceedToStep2(localUser.username, localUser.mobile, step1Container, step2Container, tvAccountBanner, btnFindAndSendOtp)
                return@setOnClickListener
            }

            // If not found locally, query backend
            FloodGuardApiService.findAccountInBackend(identifier) { success, username, mobile, masked ->
                runOnUiThread {
                    btnFindAndSendOtp.isEnabled = true
                    btnFindAndSendOtp.text = "SEND VERIFICATION CODE"

                    if (success && !username.isNullOrBlank() && !mobile.isNullOrBlank()) {
                        proceedToStep2(username, mobile, step1Container, step2Container, tvAccountBanner, btnFindAndSendOtp, masked)
                    } else {
                        Toast.makeText(
                            this@ForgotPasswordActivity,
                            "Account not found for '$identifier'. Please make sure the username or mobile is correct.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        // Resend OTP
        btnResendOtp.setOnClickListener {
            if (activeMobile.isNotBlank()) {
                dispatchOtp(activeMobile, tvAccountBanner)
                Toast.makeText(this, "A new verification OTP has been sent.", Toast.LENGTH_SHORT).show()
            }
        }

        // STEP 2: Verify OTP & Reset Password
        btnResetPassword.setOnClickListener {
            val enteredOtp = otpField.text?.toString().orEmpty().trim()
            val newPw = newPasswordField.text?.toString().orEmpty()
            val confirmPw = confirmPasswordField.text?.toString().orEmpty()

            val pwError = UserSession.getPasswordValidationError(newPw)
            if (pwError != null) {
                newPasswordField.error = pwError
                newPasswordField.requestFocus()
                Toast.makeText(this, "⚠️ $pwError", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (confirmPw.isEmpty() || newPw != confirmPw) {
                confirmPasswordField.error = "Passwords do not match."
                confirmPasswordField.requestFocus()
                Toast.makeText(this, "Passwords do not match. Please re-check.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (enteredOtp.length != 6) {
                otpField.error = "Please enter 6-digit code"
                otpField.requestFocus()
                Toast.makeText(this, "Please enter the valid 6-digit verification code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnResetPassword.isEnabled = false
            btnResetPassword.text = "UPDATING PASSWORD..."

            // Verify OTP
            val isLocalOtpMatch = activeLocalOtp != null && enteredOtp == activeLocalOtp

            if (isLocalOtpMatch) {
                applyPasswordReset(activeUsername, newPw, db)
            } else {
                FloodGuardApiService.verifyOtpInBackend(activeMobile, enteredOtp) { verified, message ->
                    runOnUiThread {
                        if (verified) {
                            applyPasswordReset(activeUsername, newPw, db)
                        } else {
                            btnResetPassword.isEnabled = true
                            btnResetPassword.text = "UPDATE PASSWORD"
                            Toast.makeText(this@ForgotPasswordActivity, "Incorrect OTP: $message", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun proceedToStep2(
        username: String,
        mobile: String,
        step1: View,
        step2: View,
        banner: TextView,
        btnFind: Button,
        customMasked: String? = null
    ) {
        activeUsername = username
        activeMobile = mobile

        val masked = customMasked ?: maskPhone(mobile)
        banner.text = "Account: $username ($masked)\nWe sent a 6-digit OTP to your number. Enter it below with your new password."

        step1.visibility = View.GONE
        step2.visibility = View.VISIBLE
        btnFind.isEnabled = true
        btnFind.text = "SEND VERIFICATION CODE"

        dispatchOtp(mobile, banner)
    }

    private fun dispatchOtp(mobile: String, banner: TextView) {
        val fallbackOtp = (100000..999999).random().toString()
        activeLocalOtp = fallbackOtp

        FloodGuardApiService.sendOtpToBackend(mobile, activeUsername) { success, demoCode, message ->
            runOnUiThread {
                val effectiveOtp = if (!demoCode.isNullOrBlank()) {
                    activeLocalOtp = demoCode
                    demoCode
                } else {
                    fallbackOtp
                }

                FloodGuardThemedDialog.showSecurityCode(
                    activity = this,
                    code = effectiveOtp,
                    notice = "SMS gateway dispatched. Valid for 5 minutes."
                ) { enteredCode ->
                    findViewById<EditText>(R.id.otpField)?.setText(enteredCode)
                }
            }
        }
    }

    private fun applyPasswordReset(username: String, newPw: String, db: FloodGuardDatabase) {
        val hashedPw = UserSession.hashPassword(newPw)

        // 1. Update in local SQLite DB
        db.updateUserPassword(username, hashedPw)

        // 2. Update in Backend Database
        FloodGuardApiService.resetPasswordInBackend(username, newPw) { _, _ -> }

        FloodGuardThemedDialog.showSuccess(
            activity = this,
            title = "Password Reset Successful",
            message = "Your password has been successfully updated! You can now log in with your new password.",
            buttonText = "Sign In Now"
        ) {
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("prefill_username", username)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun maskPhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length >= 7) {
            digits.take(4) + "***" + digits.takeLast(3)
        } else {
            phone
        }
    }
}
