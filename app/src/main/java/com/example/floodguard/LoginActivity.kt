package com.example.floodguard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.floodguard.R

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val usernameField = findViewById<EditText>(R.id.usernameField)
        val passwordField = findViewById<EditText>(R.id.passwordField)
        val signInButton = findViewById<Button>(R.id.signInButton)
        val createAccountLink = findViewById<TextView>(R.id.createAccountLink)

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
            } else {
                val savedUser = db.getUserByUsername(username)

                if (savedUser != null) {
                    UserSession.setLoggedIn(
                        context = this,
                        loggedIn = true,
                        username = savedUser.username,
                        userName = savedUser.fullName,
                        userGender = savedUser.gender,
                        userContact = savedUser.mobile
                    )
                } else {
                    val savedName = UserSession.getSavedNameForUser(this, username)
                    val reportAuthorName = db.getAllReports()
                        .firstOrNull { it.authorUsername.equals(username, ignoreCase = true) && it.reporterName.isNotBlank() }
                        ?.reporterName
                    val displayName = when {
                        !savedName.isNullOrBlank() -> savedName
                        !reportAuthorName.isNullOrBlank() -> reportAuthorName
                        username.equals("juan", ignoreCase = true) -> "Juan Dela Cruz"
                        else -> username
                    }
                    val gender = UserSession.getSavedGenderForUser(this, username) ?: "Male"
                    val contact = UserSession.getSavedContactForUser(this, username) ?: "+63 915 245 6879"

                    val fallbackUser = UserEntity(
                        fullName = displayName,
                        gender = gender,
                        mobile = contact,
                        username = username,
                        password = password
                    )
                    db.registerUser(fallbackUser)

                    UserSession.setLoggedIn(
                        context = this,
                        loggedIn = true,
                        username = username,
                        userName = displayName,
                        userGender = gender,
                        userContact = contact
                    )
                }

                FloodGuardApiService.loginUserInBackend(username, password) { remoteUser ->
                    if (remoteUser != null) {
                        db.registerUser(remoteUser)
                        UserSession.setLoggedIn(
                            context = this,
                            loggedIn = true,
                            username = remoteUser.username,
                            userName = remoteUser.fullName,
                            userGender = remoteUser.gender,
                            userContact = remoteUser.mobile
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
