package com.example.floodguard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val usernameField = findViewById<EditText>(R.id.usernameField)
        val passwordField = findViewById<EditText>(R.id.passwordField)
        val signInButton = findViewById<Button>(R.id.signInButton)
        val createAccountLink = findViewById<TextView>(R.id.createAccountLink)

        signInButton?.setOnClickListener {
            val username = usernameField?.text?.toString().orEmpty().trim()
            val password = passwordField?.text?.toString().orEmpty()

            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show()
            } else {
                val db = FloodGuardDatabase(this)
                val savedUser = db.getUserByUsername(username)

                if (savedUser != null) {
                    UserSession.setLoggedIn(
                        context = this,
                        loggedIn = true,
                        userName = savedUser.fullName,
                        userGender = savedUser.gender,
                        userContact = savedUser.mobile,
                        userEmail = savedUser.email
                    )
                } else {
                    val displayName = if (username.equals("juan", ignoreCase = true)) "Juan Dela Cruz" else username
                    UserSession.setLoggedIn(
                        context = this,
                        loggedIn = true,
                        userName = displayName,
                        userGender = "Male",
                        userContact = "+63 915 245 6879",
                        userEmail = "$username@example.com"
                    )
                }

                FloodGuardApiService.loginUserInBackend(username, password) { remoteUser ->
                    if (remoteUser != null) {
                        db.registerUser(remoteUser)
                        UserSession.setLoggedIn(
                            context = this,
                            loggedIn = true,
                            userName = remoteUser.fullName,
                            userGender = remoteUser.gender,
                            userContact = remoteUser.mobile,
                            userEmail = remoteUser.email
                        )
                    }
                }

                Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, DashboardActivity::class.java)
                startActivity(intent)
                finish()
            }
        }

        createAccountLink?.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}
