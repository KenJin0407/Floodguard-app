package com.example.floodguard

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class RegisterActivity : ComponentActivity() {

    private var activeOtpTimer: CountDownTimer? = null
    private var activeLocalOtp: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val fullNameField = findViewById<EditText>(R.id.fullNameField)
        val mobileField = findViewById<EditText>(R.id.mobileField)
        val btnRegMale = findViewById<Button>(R.id.btnRegMale)
        val btnRegFemale = findViewById<Button>(R.id.btnRegFemale)
        val regUsernameField = findViewById<EditText>(R.id.regUsernameField)
        val regPasswordField = findViewById<EditText>(R.id.regPasswordField)
        val confirmPasswordField = findViewById<EditText>(R.id.confirmPasswordField)
        val toggleRegPassword = findViewById<ImageView>(R.id.toggleRegPassword)
        val toggleConfirmPassword = findViewById<ImageView>(R.id.toggleConfirmPassword)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val loginLink = findViewById<TextView>(R.id.loginLink)

        var isRegPasswordVisible = false
        toggleRegPassword?.setOnClickListener {
            isRegPasswordVisible = !isRegPasswordVisible
            if (isRegPasswordVisible) {
                regPasswordField.transformationMethod = HideReturnsTransformationMethod.getInstance()
                toggleRegPassword.setImageResource(R.drawable.ic_visibility)
            } else {
                regPasswordField.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleRegPassword.setImageResource(R.drawable.ic_visibility_off)
            }
            regPasswordField.setSelection(regPasswordField.text?.length ?: 0)
        }

        var isConfirmPasswordVisible = false
        toggleConfirmPassword?.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            if (isConfirmPasswordVisible) {
                confirmPasswordField.transformationMethod = HideReturnsTransformationMethod.getInstance()
                toggleConfirmPassword.setImageResource(R.drawable.ic_visibility)
            } else {
                confirmPasswordField.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleConfirmPassword.setImageResource(R.drawable.ic_visibility_off)
            }
            confirmPasswordField.setSelection(confirmPasswordField.text?.length ?: 0)
        }

        var selectedGender = "Male"

        btnRegMale?.setOnClickListener {
            selectedGender = "Male"
            btnRegMale.setBackgroundResource(R.drawable.btn_severity_selected)
            btnRegMale.setTextColor(resources.getColor(android.R.color.white, theme))
            btnRegFemale?.setBackgroundResource(R.drawable.btn_severity_default)
            btnRegFemale?.setTextColor(resources.getColor(android.R.color.black, theme))
        }

        btnRegFemale?.setOnClickListener {
            selectedGender = "Female"
            btnRegFemale.setBackgroundResource(R.drawable.btn_severity_selected)
            btnRegFemale.setTextColor(resources.getColor(android.R.color.white, theme))
            btnRegMale?.setBackgroundResource(R.drawable.btn_severity_default)
            btnRegMale?.setTextColor(resources.getColor(android.R.color.black, theme))
        }

        registerButton?.setOnClickListener {
            val fullName = fullNameField?.text?.toString().orEmpty().trim()
            val rawMobile = mobileField?.text?.toString().orEmpty().trim()
            val username = regUsernameField?.text?.toString().orEmpty().trim()
            val password = regPasswordField?.text?.toString().orEmpty()
            val confirmPassword = confirmPasswordField?.text?.toString().orEmpty()

            if (fullName.isEmpty() || rawMobile.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val (isValidMobile, validationMsg) = PhilippineNumberValidator.validate(rawMobile)
            if (!isValidMobile) {
                AlertDialog.Builder(this)
                    .setTitle("Invalid Philippine Mobile Number")
                    .setMessage(validationMsg)
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }
            val validatedMobile = validationMsg

            registerButton.isEnabled = false
            Toast.makeText(this, "Sending verification code to $validatedMobile...", Toast.LENGTH_SHORT).show()

            FloodGuardApiService.sendOtpToBackend(validatedMobile) { success, demoCode, message ->
                runOnUiThread {
                    registerButton.isEnabled = true
                    if (success || demoCode != null) {
                        showOtpVerificationDialog(
                            fullName = fullName,
                            selectedGender = selectedGender,
                            mobile = validatedMobile,
                            username = username,
                            password = password,
                            initialDemoCode = demoCode
                        )
                    } else {
                        // Offline / Direct Device Fallback:
                        // Generate local random 6-digit OTP so the app never gets stuck even if Python server is not started!
                        val localCode = String.format(Locale.US, "%06d", (100000..999999).random())
                        activeLocalOtp = localCode
                        Toast.makeText(this, "Server offline: using local verification mode", Toast.LENGTH_SHORT).show()

                        // Best effort send SMS via Textbelt directly from device
                        thread {
                            val formattedPhone = SmsAlertBroadcastManager.formatPhoneE164(validatedMobile)
                            val smsMsg = "[FloodGuard] Your security verification code is $localCode. Valid for 5 minutes."
                            try {
                                val url = URL(SmsAlertBroadcastManager.FREE_SMS_API_ENDPOINT)
                                val conn = url.openConnection() as HttpURLConnection
                                conn.requestMethod = "POST"
                                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                                conn.connectTimeout = 4000
                                conn.readTimeout = 4000
                                conn.doOutput = true
                                val payload = org.json.JSONObject().apply {
                                    put("phone", formattedPhone)
                                    put("message", smsMsg)
                                    put("key", SmsAlertBroadcastManager.FREE_SMS_API_KEY)
                                }
                                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { w ->
                                    w.write(payload.toString())
                                    w.flush()
                                }
                                conn.responseCode
                                conn.disconnect()
                            } catch (_: Exception) {}
                        }

                        showOtpVerificationDialog(
                            fullName = fullName,
                            selectedGender = selectedGender,
                            mobile = validatedMobile,
                            username = username,
                            password = password,
                            initialDemoCode = localCode
                        )
                    }
                }
            }
        }

        loginLink?.setOnClickListener {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeOtpTimer?.cancel()
    }

    private fun validatePhilippineMobileNumber(raw: String): String? {
        val (valid, result) = PhilippineNumberValidator.validate(raw)
        return if (valid) result else null
    }

    @android.annotation.SuppressLint("InflateParams")
    private fun showOtpVerificationDialog(
        fullName: String,
        selectedGender: String,
        mobile: String,
        username: String,
        password: String,
        initialDemoCode: String?,
    ) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_verify_otp, null)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)

        val tvRecipient = dialogView.findViewById<TextView>(R.id.tvOtpRecipientPhone)
        val demoContainer = dialogView.findViewById<LinearLayout>(R.id.otpDemoCodeContainer)
        val tvDemoCode = dialogView.findViewById<TextView>(R.id.tvDemoCodeValue)
        val etOtp = dialogView.findViewById<EditText>(R.id.etOtpCodeInput)
        val tvTimer = dialogView.findViewById<TextView>(R.id.tvOtpTimer)
        val btnResend = dialogView.findViewById<TextView>(R.id.btnOtpResend)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnOtpCancel)
        val btnVerify = dialogView.findViewById<Button>(R.id.btnOtpVerify)

        tvRecipient.text = "Verification code sent to $mobile"

        if (!initialDemoCode.isNullOrBlank()) {
            demoContainer.visibility = View.VISIBLE
            tvDemoCode.text = initialDemoCode
            // Do not auto-type: user must manually read their SMS or verification code
            etOtp.setText("")
        } else {
            demoContainer.visibility = View.GONE
        }

        activeOtpTimer?.cancel()
        activeOtpTimer = object : CountDownTimer(300_000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val mins = (millisUntilFinished / 1000) / 60
                val secs = (millisUntilFinished / 1000) % 60
                tvTimer.text = String.format(Locale.US, "Code expires in %02d:%02d", mins, secs)
            }

            override fun onFinish() {
                tvTimer.text = "Code expired. Please tap Resend."
            }
        }.start()

        btnResend.setOnClickListener {
            btnResend.isEnabled = false
            Toast.makeText(this, "Resending code to $mobile...", Toast.LENGTH_SHORT).show()
            FloodGuardApiService.sendOtpToBackend(mobile) { resendSuccess, newDemoCode, _ ->
                runOnUiThread {
                    btnResend.isEnabled = true
                    val effectiveCode = if (resendSuccess || newDemoCode != null) {
                        newDemoCode
                    } else {
                        val localCode = String.format(Locale.US, "%06d", (100000..999999).random())
                        activeLocalOtp = localCode
                        localCode
                    }

                    Toast.makeText(this, "New verification code generated!", Toast.LENGTH_SHORT).show()
                    if (!effectiveCode.isNullOrBlank()) {
                        demoContainer.visibility = View.VISIBLE
                        tvDemoCode.text = effectiveCode
                        etOtp.setText(effectiveCode)
                        etOtp.setSelection(effectiveCode.length)
                    }
                }
            }
        }

        btnCancel.setOnClickListener {
            activeOtpTimer?.cancel()
            dialog.dismiss()
        }

        btnVerify.setOnClickListener {
            val enteredCode = etOtp.text.toString().trim()
            if (enteredCode.length < 6) {
                Toast.makeText(this, "Please enter the full 6-digit verification code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            fun completeRegistration() {
                activeOtpTimer?.cancel()
                dialog.dismiss()

                val db = FloodGuardDatabase(this@RegisterActivity)
                val user = UserEntity(
                    fullName = fullName,
                    gender = selectedGender,
                    mobile = mobile,
                    username = username,
                    password = password,
                    isVerified = true,
                )
                db.registerUser(user)
                FloodGuardApiService.registerUserInBackend(user)

                UserSession.setLoggedIn(
                    context = this@RegisterActivity,
                    loggedIn = true,
                    username = username,
                    userName = fullName,
                    userGender = selectedGender,
                    userContact = mobile,
                    phoneVerified = true,
                )
                Toast.makeText(this@RegisterActivity, "Mobile number verified & Account created!", Toast.LENGTH_LONG).show()
                val intent = Intent(this@RegisterActivity, DashboardActivity::class.java)
                startActivity(intent)
                finish()
            }

            btnVerify.isEnabled = false

            // If local offline OTP was generated and matches, accept immediately
            if (activeLocalOtp != null && enteredCode == activeLocalOtp) {
                completeRegistration()
                return@setOnClickListener
            }

            FloodGuardApiService.verifyOtpInBackend(mobile, enteredCode) { verifySuccess, verifyMsg ->
                runOnUiThread {
                    btnVerify.isEnabled = true
                    if (verifySuccess) {
                        completeRegistration()
                    } else if (activeLocalOtp != null && enteredCode == activeLocalOtp) {
                        completeRegistration()
                    } else {
                        Toast.makeText(this@RegisterActivity, verifyMsg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        dialog.show()
    }
}
