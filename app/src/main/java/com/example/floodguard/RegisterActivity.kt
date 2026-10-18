package com.example.floodguard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

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
                val db = FloodGuardDatabase(this)
                val hashedPassword = UserSession.hashPassword(password)
                val user = UserEntity(
                    fullName = fullName,
                    gender = selectedGender,
                    mobile = mobile,
                    username = username,
                    password = hashedPassword
                )
                db.registerUser(user)
                FloodGuardApiService.registerUserInBackend(user)

                UserSession.setLoggedIn(
                    context = this,
                    loggedIn = true,
                    userName = fullName,
                    username = username,
                    userGender = selectedGender,
                    userContact = mobile
                )
                Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, DashboardActivity::class.java)
                startActivity(intent)
                finish()
            }
        }

        loginLink?.setOnClickListener {
            finish()
        }
    }
}
