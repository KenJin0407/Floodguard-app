package com.example.floodguard

import android.animation.ObjectAnimator
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

object FloatingAlertManager {

    private var activeActivityRef: WeakReference<Activity>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val CHANNEL_ID = "floodguard_flood_alerts"

    fun onActivityResumed(activity: Activity) {
        activeActivityRef = WeakReference(activity)
    }

    fun onActivityPaused(activity: Activity) {
        if (activeActivityRef?.get() == activity) {
            activeActivityRef = null
        }
    }

    fun showPushAlert(context: Context, broadcast: FloodBroadcast) {
        mainHandler.post {
            val activity = activeActivityRef?.get()
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                showFloatingAlert(activity, broadcast)
            } else {
                showSystemNotification(context, broadcast)
            }
        }
    }

    private fun showSystemNotification(context: Context, broadcast: FloodBroadcast) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Flood alerts",
                NotificationManager.IMPORTANCE_HIGH,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            broadcast.id.hashCode(),
            Intent(context, AlertsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val details = "${broadcast.location} — ${broadcast.severity}"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle("FloodGuard flood alert")
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${details}\n${broadcast.notes}"))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(broadcast.id.hashCode(), notification)
    }

    @SuppressLint("SetTextI18n")
    fun showFloatingAlert(activity: Activity, broadcast: FloodBroadcast) {
        val rootGroup = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        // Create container for floating overlay
        val cardLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            elevation = 24f

            val paddingPx = (14 * activity.resources.displayMetrics.density).toInt()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)

            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#FFFFFF"))
                setStroke((1 * activity.resources.displayMetrics.density).toInt(), Color.parseColor("#E5E7EB"))
                cornerRadius = 16 * activity.resources.displayMetrics.density
            }
            background = bg

            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                val marginPx = (16 * activity.resources.displayMetrics.density).toInt()
                setMargins(marginPx, (12 * activity.resources.displayMetrics.density).toInt(), marginPx, 0)
            }
            layoutParams = params
        }

        // Header Row (Badge + Title + Close)
        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val badgeIcon = ImageView(activity).apply {
            setImageResource(R.drawable.ic_alert)
            val iconSize = (20 * activity.resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                rightMargin = (8 * activity.resources.displayMetrics.density).toInt()
            }
        }

        val titleText = TextView(activity).apply {
            text = "🚨 FLOODGUARD FLOOD ALERT"
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(activity).apply {
            text = "✕"
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(12, 4, 12, 4)
        }

        headerRow.addView(badgeIcon)
        headerRow.addView(titleText)
        headerRow.addView(closeBtn)

        // Location & Severity
        val locationText = TextView(activity).apply {
            text = "${broadcast.location} — ${broadcast.severity}"
            setTextColor(Color.parseColor("#111827"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            val topMarginPx = (6 * activity.resources.displayMetrics.density).toInt()
            setPadding(0, topMarginPx, 0, 0)
        }

        // Notes & Reporter Info
        val reporterInfo = if (broadcast.reporterName.isNotBlank()) " (By: ${broadcast.reporterName})" else ""
        val detailsText = TextView(activity).apply {
            text = "${broadcast.notes}$reporterInfo"
            setTextColor(Color.parseColor("#4B5563"))
            textSize = 12f
            val topMarginPx = (4 * activity.resources.displayMetrics.density).toInt()
            setPadding(0, topMarginPx, 0, 0)
        }

        // Action Buttons Row (View Map | Dismiss)
        val actionRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            val topMarginPx = (10 * activity.resources.displayMetrics.density).toInt()
            setPadding(0, topMarginPx, 0, 0)
        }

        val btnMap = TextView(activity).apply {
            text = "VIEW ON MAP"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(16, 8, 16, 8)

            setOnClickListener {
                dismissCard(rootGroup, cardLayout)
                val intent = Intent(activity, MapActivity::class.java).apply {
                    putExtra("search_query", broadcast.location)
                }
                activity.startActivity(intent)
            }
        }

        val btnDismiss = TextView(activity).apply {
            text = "DISMISS"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(16, 8, 8, 8)

            setOnClickListener {
                dismissCard(rootGroup, cardLayout)
            }
        }

        closeBtn.setOnClickListener {
            dismissCard(rootGroup, cardLayout)
        }

        actionRow.addView(btnMap)
        actionRow.addView(btnDismiss)

        cardLayout.addView(headerRow)
        cardLayout.addView(locationText)
        cardLayout.addView(detailsText)
        cardLayout.addView(actionRow)

        // Initial translation off-screen top
        cardLayout.translationY = -400f
        rootGroup.addView(cardLayout)

        // Animate slide down
        ObjectAnimator.ofFloat(cardLayout, "translationY", -400f, 0f).apply {
            duration = 450
            interpolator = DecelerateInterpolator()
            start()
        }

        // Auto dismiss after 10 seconds
        mainHandler.postDelayed({
            dismissCard(rootGroup, cardLayout)
        }, 10000)
    }

    private fun dismissCard(rootGroup: ViewGroup, cardLayout: View) {
        if (cardLayout.parent != null) {
            ObjectAnimator.ofFloat(cardLayout, "translationY", cardLayout.translationY, -500f).apply {
                duration = 350
                interpolator = DecelerateInterpolator()
                start()
            }
            mainHandler.postDelayed({
                try {
                    rootGroup.removeView(cardLayout)
                } catch (_: Exception) {
                }
            }, 360)
        }
    }
}
