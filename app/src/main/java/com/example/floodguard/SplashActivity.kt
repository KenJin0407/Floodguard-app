package com.example.floodguard

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * Splash screen activity displayed upon application startup.
 * Initializes the database connection and routes to DashboardActivity if already logged in,
 * or LoginActivity for unauthenticated users.
 */
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splashscreen)

        // Initialize and open database connection so Database Inspector connects immediately
        try {
            FloodGuardDatabase(this).writableDatabase
        } catch (_: Exception) {
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val targetActivity = if (UserSession.isLoggedIn(this)) {
                DashboardActivity::class.java
            } else {
                LoginActivity::class.java
            }
            val intent = Intent(this, targetActivity)
            startActivity(intent)
            finish()
        }, 3000)
    }
}
