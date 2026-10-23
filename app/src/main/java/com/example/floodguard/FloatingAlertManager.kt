package com.example.floodguard

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * In-app floating alert notification and banner manager.
 * Displays high-visibility heads-up warning banners when severe flood reports or emergency broadcasts
 * are detected near the user's current location.
 */
object FloatingAlertManager {

    private var activeActivityRef: WeakReference<Activity>? = null
    private var activeDialogRef: WeakReference<AlertDialog>? = null
    private var pendingBroadcast: FloodBroadcast? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val CHANNEL_ID = "floodguard_flood_alerts"
    private const val POLL_INTERVAL_MS = 8000L

    private val broadcastPollRunnable = object : Runnable {
        override fun run() {
            val activity = activeActivityRef?.get()
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                checkUnseenBroadcastsForUser(activity)
                mainHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    fun onActivityResumed(activity: Activity) {
        activeActivityRef = WeakReference(activity)
        mainHandler.removeCallbacks(broadcastPollRunnable)

        val pending = pendingBroadcast
        if (pending != null) {
            pendingBroadcast = null
            mainHandler.postDelayed({
                val currentAct = activeActivityRef?.get()
                if (currentAct != null && !currentAct.isFinishing && !currentAct.isDestroyed) {
                    SmsAlertBroadcastManager.markBroadcastSeen(currentAct, pending.id)
                    showFloatingAlert(currentAct, pending)
                    showSmsWarningPopUpDialog(currentAct, pending)
                    showSystemNotification(currentAct.applicationContext, pending)
                }
            }, 300)
        } else {
            mainHandler.postDelayed({
                val currentAct = activeActivityRef?.get()
                if (currentAct != null && !currentAct.isFinishing && !currentAct.isDestroyed) {
                    checkUnseenBroadcastsForUser(currentAct)
                }
            }, 400)
        }

        mainHandler.postDelayed(broadcastPollRunnable, POLL_INTERVAL_MS)
    }

    fun onActivityPaused(activity: Activity) {
        if (activeActivityRef?.get() == activity) {
            activeActivityRef = null
            mainHandler.removeCallbacks(broadcastPollRunnable)
        }
    }

    fun deliverBroadcastIfNearby(
        context: Context,
        broadcast: FloodBroadcast,
        forcePopUp: Boolean = false
    ) {
        val isNearby = SmsAlertBroadcastManager.isUserNearArea(
            reportLat = broadcast.lat,
            reportLon = broadcast.lon,
            radiusKm = broadcast.radiusKm
        )
        if (!isNearby) return

        mainHandler.post {
            val activity = activeActivityRef?.get()
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                if (forcePopUp || !SmsAlertBroadcastManager.hasUserSeenBroadcast(activity, broadcast.id, broadcast.timestamp)) {
                    SmsAlertBroadcastManager.markBroadcastSeen(activity, broadcast.id)
                    showFloatingAlert(activity, broadcast)
                    showSmsWarningPopUpDialog(activity, broadcast)
                    showSystemNotification(context.applicationContext, broadcast)
                }
            } else {
                pendingBroadcast = broadcast
                showSystemNotification(context.applicationContext, broadcast)
            }
        }
    }

    private fun checkUnseenBroadcastsForUser(activity: Activity) {
        if (!SmsAlertBroadcastManager.isBaselineSet(activity)) {
            SmsAlertBroadcastManager.markAllExistingBroadcastsSeen(activity)
            return
        }

        val localBroadcasts = SmsAlertBroadcastManager.getSavedBroadcasts(activity)
        val recentCutoff = System.currentTimeMillis() - 6 * 60 * 60 * 1000L // Last 6 hours
        val unseenLocal = localBroadcasts.filter { b ->
            b.timestamp >= recentCutoff &&
                SmsAlertBroadcastManager.isUserNearArea(b.lat, b.lon, radiusKm = b.radiusKm) &&
                !SmsAlertBroadcastManager.hasUserSeenBroadcast(activity, b.id, b.timestamp)
        }
        if (unseenLocal.isNotEmpty()) {
            val latest = unseenLocal.maxByOrNull { it.timestamp } ?: unseenLocal.first()
            for (b in unseenLocal) {
                SmsAlertBroadcastManager.markBroadcastSeen(activity, b.id)
            }
            deliverBroadcastIfNearby(activity, latest, forcePopUp = false)
        }

        FloodGuardApiService.syncBroadcastsFromBackend { remoteList ->
            for (b in remoteList) {
                SmsAlertBroadcastManager.saveBroadcastLocally(activity.applicationContext, b)
            }
            if (!SmsAlertBroadcastManager.isBaselineSet(activity.applicationContext)) {
                SmsAlertBroadcastManager.markAllExistingBroadcastsSeen(activity.applicationContext)
                return@syncBroadcastsFromBackend
            }
            val unseenRemote = remoteList.filter { b ->
                b.timestamp >= recentCutoff &&
                    SmsAlertBroadcastManager.isUserNearArea(b.lat, b.lon, radiusKm = b.radiusKm) &&
                    !SmsAlertBroadcastManager.hasUserSeenBroadcast(activity.applicationContext, b.id, b.timestamp)
            }
            if (unseenRemote.isNotEmpty()) {
                val latestRemote = unseenRemote.maxByOrNull { it.timestamp } ?: unseenRemote.first()
                for (b in unseenRemote) {
                    SmsAlertBroadcastManager.markBroadcastSeen(activity.applicationContext, b.id)
                }
                deliverBroadcastIfNearby(activity.applicationContext, latestRemote, forcePopUp = false)
            }
        }
    }

    fun showPushAlert(context: Context, broadcast: FloodBroadcast) {
        deliverBroadcastIfNearby(context, broadcast, forcePopUp = true)
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
        val details = "${broadcast.location} — ${broadcast.severity} (${broadcast.similarReportCount} similar reports)"
        val bigText = if (broadcast.smsMessage.isNotBlank()) {
            broadcast.smsMessage
        } else {
            "${details}\n${broadcast.notes}"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle("🚨 FloodGuard SMS Flood Warning")
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(broadcast.id.hashCode(), notification)
    }

    @SuppressLint("SetTextI18n")
    fun showSmsWarningPopUpDialog(activity: Activity, broadcast: FloodBroadcast) {
        if (activity.isFinishing || activity.isDestroyed) return

        try {
            activeDialogRef?.get()?.let { existing ->
                if (existing.isShowing) existing.dismiss()
            }
        } catch (_: Exception) {
        }

        val density = activity.resources.displayMetrics.density
        val pad = (18 * density).toInt()
        val distKm = SmsAlertBroadcastManager.distanceInKm(
            SmsAlertBroadcastManager.DEFAULT_USER_LAT,
            SmsAlertBroadcastManager.DEFAULT_USER_LON,
            broadcast.lat,
            broadcast.lon
        )

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.dropdown_menu_bg)
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.dialog_header_bg)
            setPadding(pad, pad, pad, pad)
        }

        val badgeRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val alertIcon = ImageView(activity).apply {
            setImageResource(R.drawable.ic_alert)
            val size = (22 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (8 * density).toInt()
            }
        }

        val titleView = TextView(activity).apply {
            text = "SMS FLOOD WARNING ALERT"
            setTextColor(Color.parseColor("#FFA500"))
            textSize = 16f
            paint.isFakeBoldText = true
        }

        badgeRow.addView(alertIcon)
        badgeRow.addView(titleView)

        val subtitleView = TextView(activity).apply {
            text = "Triggered by ${broadcast.similarReportCount} similar reports near your area"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, (4 * density).toInt(), 0, 0)
        }

        header.addView(badgeRow)
        header.addView(subtitleView)
        root.addView(header)

        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        // Location & Proximity Banner
        val areaBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val boxPad = (12 * density).toInt()
            setPadding(boxPad, boxPad, boxPad, boxPad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FEF2F2"))
                setStroke((1 * density).toInt(), Color.parseColor("#FECACA"))
                cornerRadius = 10 * density
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * density).toInt()
            }
        }

        val locationTitle = TextView(activity).apply {
            text = "📍 ${broadcast.location}"
            setTextColor(Color.parseColor("#991B1B"))
            textSize = 15f
            paint.isFakeBoldText = true
        }

        val severityStatusText = TextView(activity).apply {
            text = "Severity: ${broadcast.severity} (${broadcast.statusText}) • ~${String.format(Locale.US, "%.1f", distKm)} km away"
            setTextColor(Color.parseColor("#DC2626"))
            textSize = 12f
            paint.isFakeBoldText = true
            setPadding(0, (3 * density).toInt(), 0, 0)
        }

        areaBox.addView(locationTitle)
        areaBox.addView(severityStatusText)
        body.addView(areaBox)

        // SMS API Broadcast Message Box
        val smsBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val boxPad = (12 * density).toInt()
            setPadding(boxPad, boxPad, boxPad, boxPad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F8FAFC"))
                setStroke((1 * density).toInt(), Color.parseColor("#CBD5E1"))
                cornerRadius = 10 * density
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (14 * density).toInt()
            }
        }

        val smsHeader = TextView(activity).apply {
            text = "📡 FREE SMS API BROADCAST (${broadcast.smsProvider})"
            setTextColor(Color.parseColor("#0A192F"))
            textSize = 11f
            paint.isFakeBoldText = true
        }

        val smsContent = TextView(activity).apply {
            text = broadcast.smsMessage.ifBlank {
                "[FLOODGUARD SMS ALERT] URGENT: ${broadcast.similarReportCount} similar flood reports verified near ${broadcast.location}. Water Level: ${broadcast.severity} (${broadcast.statusText}). ${broadcast.notes}"
            }
            setTextColor(Color.parseColor("#1E293B"))
            textSize = 13f
            setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
        }

        val userPhone = SmsAlertBroadcastManager.formatPhoneE164(UserSession.getUserContact(activity))
        val recipientSummary = TextView(activity).apply {
            text = "Broadcasted to ${broadcast.smsRecipientsCount.coerceAtLeast(1)} nearby user(s) • Your SMS: ${userPhone.ifBlank { "+639152456879" }}\nReported by: ${broadcast.reporterName.ifBlank { "Community Reporters" }}"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 11f
        }

        smsBox.addView(smsHeader)
        smsBox.addView(smsContent)
        smsBox.addView(recipientSummary)
        body.addView(smsBox)

        // Action Buttons Row
        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val dismissBtn = Button(activity).apply {
            text = "Acknowledge"
            isAllCaps = false
            setTextColor(Color.parseColor("#374151"))
            textSize = 13f
            paint.isFakeBoldText = true
            setBackgroundResource(R.drawable.bg_dialog_cancel_btn)
            layoutParams = LinearLayout.LayoutParams(0, (44 * density).toInt(), 1f).apply {
                marginEnd = (8 * density).toInt()
            }
        }

        val viewMapBtn = Button(activity).apply {
            text = "View Area on Map"
            isAllCaps = false
            setTextColor(Color.parseColor("#0A192F"))
            textSize = 13f
            paint.isFakeBoldText = true
            setBackgroundResource(R.drawable.btn_yellow)
            layoutParams = LinearLayout.LayoutParams(0, (44 * density).toInt(), 1.2f)
        }

        btnRow.addView(dismissBtn)
        btnRow.addView(viewMapBtn)
        body.addView(btnRow)
        root.addView(body)

        val dialog = AlertDialog.Builder(activity)
            .setView(root)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dismissBtn.setOnClickListener {
            dialog.dismiss()
        }

        viewMapBtn.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(activity, MapActivity::class.java).apply {
                putExtra("search_query", broadcast.location)
            }
            activity.startActivity(intent)
        }

        activeDialogRef = WeakReference(dialog)
        dialog.show()
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
                setStroke((1.5f * activity.resources.displayMetrics.density).toInt(), Color.parseColor("#EF4444"))
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
            text = "🚨 SMS FLOOD BROADCAST (${broadcast.similarReportCount} REPORTS)"
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 12f
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
            text = "${broadcast.location} — ${broadcast.severity} (${broadcast.statusText})"
            setTextColor(Color.parseColor("#111827"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            val topMarginPx = (6 * activity.resources.displayMetrics.density).toInt()
            setPadding(0, topMarginPx, 0, 0)
        }

        // Notes & Reporter Info
        val detailsText = TextView(activity).apply {
            text = if (broadcast.smsMessage.isNotBlank()) {
                broadcast.smsMessage
            } else {
                val reporterInfo = if (broadcast.reporterName.isNotBlank()) " (By: ${broadcast.reporterName})" else ""
                "${broadcast.notes}$reporterInfo"
            }
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

        // Auto dismiss after 12 seconds
        mainHandler.postDelayed({
            dismissCard(rootGroup, cardLayout)
        }, 12000)
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

