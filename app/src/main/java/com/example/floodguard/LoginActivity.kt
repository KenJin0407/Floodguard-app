package com.example.floodguard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val usernameField = findViewById<EditText>(R.id.usernameField)
        val passwordField = findViewById<EditText>(R.id.passwordField)
        val togglePasswordVisibility = findViewById<ImageView>(R.id.togglePasswordVisibility)
        val signInButton = findViewById<Button>(R.id.signInButton)
        val createAccountLink = findViewById<TextView>(R.id.createAccountLink)
        val forgotPasswordLink = findViewById<TextView>(R.id.forgotPasswordLink)

        val prefillUser = intent.getStringExtra("prefill_username")
        if (!prefillUser.isNullOrEmpty()) {
            usernameField.setText(prefillUser)
            passwordField.requestFocus()
        }

        var isPasswordVisible = false
        togglePasswordVisibility?.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                passwordField.transformationMethod = android.text.method.HideReturnsTransformationMethod.getInstance()
                togglePasswordVisibility.setImageResource(R.drawable.ic_visibility)
            } else {
                passwordField.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_off)
            }
            passwordField.setSelection(passwordField.text.length)
        }

        val db = FloodGuardDatabase(this)
        FloodGuardApiService.syncUsersFromBackend { users ->
            for (u in users) {
                db.registerUser(u)
            }
        }

        signInButton?.setOnClickListener {
            val username = usernameField?.text?.toString().orEmpty().trim()
            val password = passwordField?.text?.toString().orEmpty()

            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val savedUser = db.getUserByUsername(username)
            val hashedInput = UserSession.hashPassword(password)

            // Local verification first
            if (savedUser != null && (savedUser.password == hashedInput || savedUser.password == password)) {
                if (savedUser.password == password && savedUser.password != hashedInput) {
                    db.registerUser(savedUser.copy(password = hashedInput))
                }
                UserSession.setLoggedIn(
                    context = this,
                    loggedIn = true,
                    username = savedUser.username,
                    userName = savedUser.fullName,
                    userGender = savedUser.gender,
                    userContact = savedUser.mobile,
                    phoneVerified = savedUser.isVerified
                )
                Toast.makeText(this, "Welcome back, ${savedUser.fullName}!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, DashboardActivity::class.java)
                startActivity(intent)
                finish()
                return@setOnClickListener
            }

            // Remote backend verification
            Toast.makeText(this, "Verifying credentials...", Toast.LENGTH_SHORT).show()
            FloodGuardApiService.loginUserInBackend(username, password) { remoteUser ->
                runOnUiThread {
                    if (remoteUser != null) {
                        val userToSave = remoteUser.copy(password = hashedInput)
                        db.registerUser(userToSave)
                        UserSession.setLoggedIn(
                            context = this,
                            loggedIn = true,
                            username = remoteUser.username,
                            userName = remoteUser.fullName,
                            userGender = remoteUser.gender,
                            userContact = remoteUser.mobile,
                            phoneVerified = remoteUser.isVerified
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

        createAccountLink?.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        forgotPasswordLink?.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            val currentTyped = usernameField?.text?.toString().orEmpty().trim()
            if (currentTyped.isNotEmpty()) {
                intent.putExtra("prefill_identifier", currentTyped)
            }
            startActivity(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val prefillUser = intent.getStringExtra("prefill_username")
        if (!prefillUser.isNullOrEmpty()) {
            findViewById<EditText>(R.id.usernameField)?.setText(prefillUser)
            findViewById<EditText>(R.id.passwordField)?.requestFocus()
        }
    }
}
