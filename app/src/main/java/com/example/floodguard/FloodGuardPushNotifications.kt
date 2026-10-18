package com.example.floodguard

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

object FloodGuardPushNotifications {

    private const val TOPIC = "floodguard"
    private const val NOTIFICATION_PERMISSION_REQUEST = 4101

    fun initialize(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "floodguard_flood_alerts",
                "Flood alerts",
                NotificationManager.IMPORTANCE_HIGH,
            )
            activity.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST,
            )
        }

        val firebaseApp = FirebaseApp.initializeApp(activity)
        if (firebaseApp == null) {
            Log.e("FloodGuardPush", "Firebase is not configured; add app/google-services.json to enable push alerts.")
            return
        }

        FirebaseMessaging.getInstance()
            .subscribeToTopic(TOPIC)
            .addOnFailureListener { error ->
                Log.e("FloodGuardPush", "Failed to subscribe to flood alerts", error)
            }
    }
}

class FloodGuardMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val authorUsername = data["authorUsername"].orEmpty()
        val currentUsername = UserSession.getUsername(this)
        if (authorUsername.isNotBlank() && authorUsername.equals(currentUsername, ignoreCase = true)) {
            return
        }
        val broadcast = FloodBroadcast(
            id = data["reportId"] ?: message.messageId ?: System.currentTimeMillis().toString(),
            location = data["location"] ?: "Flood report",
            severity = data["severity"] ?: "New flood report",
            notes = data["notes"].orEmpty(),
            reporterName = data["reporterName"].orEmpty(),
            timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
            authorUsername = authorUsername,
        )
        FloatingAlertManager.showPushAlert(this, broadcast)
    }
}
