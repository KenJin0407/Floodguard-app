package com.example.floodguard

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.util.Locale

/**
 * Activity for user account creation.
 * Collects Full Name, Contact Number, Gender, Username, and Password.
 * Triggers Google Firebase SMS OTP verification before persisting account data to SQLite & backend.
 */
class RegisterActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val fullNameField = findViewById<EditText>(R.id.fullNameField)
        val mobileField = findViewById<EditText>(R.id.mobileField)
        val btnRegMale = findViewById<Button>(R.id.btnRegMale)
        val btnRegFemale = findViewById<Button>(R.id.btnRegFemale)
        val regUsernameField = findViewById<EditText>(R.id.regUsernameField)
        val regPasswordField = findViewById<EditText>(R.id.regPasswordField)
        val toggleRegPassword = findViewById<ImageView>(R.id.toggleRegPassword)
        val confirmPasswordField = findViewById<EditText>(R.id.confirmPasswordField)
        val toggleConfirmPassword = findViewById<ImageView>(R.id.toggleConfirmPassword)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val loginLink = findViewById<TextView>(R.id.loginLink)

        var isRegPwVisible = false
        toggleRegPassword?.setOnClickListener {
            isRegPwVisible = !isRegPwVisible
            if (isRegPwVisible) {
                regPasswordField.transformationMethod = android.text.method.HideReturnsTransformationMethod.getInstance()
                toggleRegPassword.setImageResource(R.drawable.ic_visibility)
            } else {
                regPasswordField.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                toggleRegPassword.setImageResource(R.drawable.ic_visibility_off)
            }
            regPasswordField.setSelection(regPasswordField.text.length)
        }

        var isConfirmPwVisible = false
        toggleConfirmPassword?.setOnClickListener {
            isConfirmPwVisible = !isConfirmPwVisible
            if (isConfirmPwVisible) {
                confirmPasswordField.transformationMethod = android.text.method.HideReturnsTransformationMethod.getInstance()
                toggleConfirmPassword.setImageResource(R.drawable.ic_visibility)
            } else {
                confirmPasswordField.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                toggleConfirmPassword.setImageResource(R.drawable.ic_visibility_off)
            }
            confirmPasswordField.setSelection(confirmPasswordField.text.length)
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
            val mobile = mobileField?.text?.toString().orEmpty().trim()
            val username = regUsernameField?.text?.toString().orEmpty().trim()
            val password = regPasswordField?.text?.toString().orEmpty()
            val confirmPassword = confirmPasswordField?.text?.toString().orEmpty()

            val contactError = UserSession.getContactValidationError(mobile)

            if (fullName.isEmpty() || mobile.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            } else if (contactError != null) {
                mobileField?.error = contactError
                mobileField?.requestFocus()
                Toast.makeText(this, contactError, Toast.LENGTH_LONG).show()
            } else if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            } else {
                val hashedPassword = UserSession.hashPassword(password)
                val newUser = UserEntity(
                    fullName = fullName,
                    gender = selectedGender,
                    mobile = mobile,
                    username = username,
                    password = hashedPassword
                )

                // Trigger OTP Verification for mobile number
                showOtpVerificationDialog(newUser)
            }
        }

        loginLink?.setOnClickListener {
            finish()
        }
    }

    /** Displays the themed 6-digit OTP modal dialog for mobile number verification */
    private fun showOtpVerificationDialog(user: UserEntity) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_otp_verification, null)
        val tvPhone = dialogView.findViewById<TextView>(R.id.tvOtpPhoneNumber)
        val etOtp = dialogView.findViewById<EditText>(R.id.etOtpCode)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvOtpStatusMessage)
        val tvTimer = dialogView.findViewById<TextView>(R.id.tvOtpTimer)
        val btnResend = dialogView.findViewById<TextView>(R.id.btnResendOtp)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnOtpCancel)
        val btnVerify = dialogView.findViewById<Button>(R.id.btnOtpVerify)

        val formattedPhone = FirebaseAuthOtpHelper.formatToE164(user.mobile)
        tvPhone.text = formattedPhone

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        var countDownTimer: CountDownTimer? = null

        fun startCountdown() {
            countDownTimer?.cancel()
            btnResend.isEnabled = false
            btnResend.setTextColor(Color.parseColor("#94A3B8"))

            countDownTimer = object : CountDownTimer(60000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val sec = millisUntilFinished / 1000
                    tvTimer.text = String.format(Locale.US, "Resend code in %02d:%02d", sec / 60, sec % 60)
                }

                override fun onFinish() {
                    tvTimer.text = "Didn't receive SMS?"
                    btnResend.isEnabled = true
                    btnResend.setTextColor(Color.parseColor("#FFA500"))
                }
            }.start()
        }

        fun sendVerificationCode() {
            tvStatus.text = "Sending verification code via SMS..."
            tvStatus.setTextColor(Color.parseColor("#0284C7"))
            startCountdown()

            FirebaseAuthOtpHelper.sendOtp(
                activity = this,
                mobileNumber = user.mobile,
                callback = object : FirebaseAuthOtpHelper.OtpCallback {
                    override fun onCodeSent(formattedPhone: String) {
                        tvStatus.text = "SMS sent! Enter the 6-digit code above."
                        tvStatus.setTextColor(Color.parseColor("#0284C7"))
                    }

                    override fun onAutoVerified() {
                        countDownTimer?.cancel()
                        dialog.dismiss()
                        completeRegistration(user)
                    }

                    override fun onError(message: String) {
                        tvStatus.text = message
                        tvStatus.setTextColor(Color.parseColor("#DC2626"))
                    }

                    override fun onFallbackCodeGenerated(code: String) {
                        runOnUiThread {
                            FloodGuardThemedDialog.showSecurityCode(
                                activity = this@RegisterActivity,
                                code = code,
                                notice = "Firebase SMS is not yet activated on Firebase Console. Valid for 5 minutes."
                            ) { enteredCode ->
                                etOtp.setText(enteredCode)
                            }
                        }
                    }
                }
            )
        }

        btnResend.setOnClickListener {
            sendVerificationCode()
        }

        btnCancel.setOnClickListener {
            countDownTimer?.cancel()
            dialog.dismiss()
        }

        btnVerify.setOnClickListener {
            val code = etOtp.text.toString().trim()
            if (code.length < 6) {
                tvStatus.text = "Please enter the full 6-digit verification code."
                tvStatus.setTextColor(Color.parseColor("#DC2626"))
                return@setOnClickListener
            }

            btnVerify.isEnabled = false
            tvStatus.text = "Verifying code..."
            tvStatus.setTextColor(Color.parseColor("#0284C7"))

            FirebaseAuthOtpHelper.verifyCode(
                enteredCode = code,
                onSuccess = {
                    countDownTimer?.cancel()
                    dialog.dismiss()
                    completeRegistration(user)
                },
                onError = { errMsg ->
                    btnVerify.isEnabled = true
                    tvStatus.text = errMsg
                    tvStatus.setTextColor(Color.parseColor("#DC2626"))
                }
            )
        }

        dialog.show()
        sendVerificationCode()
    }

    /** Finalizes user registration, saves records in SQLite and the backend, and initializes session */
    private fun completeRegistration(user: UserEntity) {
        val db = FloodGuardDatabase.getInstance(this)
        db.registerUser(user)
        FloodGuardApiService.registerUserInBackend(user)

        UserSession.setLoggedIn(
            context = this,
            loggedIn = true,
            userName = user.fullName,
            username = user.username,
            userGender = user.gender,
            userContact = user.mobile
        )

        Toast.makeText(this, "✅ Mobile number verified! Account created.", Toast.LENGTH_LONG).show()
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }
}

