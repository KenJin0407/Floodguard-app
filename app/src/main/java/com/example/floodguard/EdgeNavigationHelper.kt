package com.example.floodguard

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * iOS AssistiveTouch-style floating navigation menu for FloodGuard.
 * Replaces the traditional bottom navigation bar with a floating circular button
 * that expands into an iOS-style dark rounded card containing Home, Map, Alerts, and Profile.
 */
object EdgeNavigationHelper {

    enum class NavTab {
        HOME, MAP, ALERTS, PROFILE
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun attachEdgeNavigation(activity: Activity, currentTab: NavTab) {
        val rootViewGroup = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        // Inflate AssistiveTouch layout into the activity root
        val edgeView = LayoutInflater.from(activity).inflate(R.layout.layout_edge_navigation, rootViewGroup, false)

        val edgeScrim = edgeView.findViewById<View>(R.id.edgeScrim)
        val edgeFloatingButton = edgeView.findViewById<FrameLayout>(R.id.edgeFloatingButton)
        val edgeAssistiveCard = edgeView.findViewById<LinearLayout>(R.id.edgeAssistiveCard)

        val edgeNavHome = edgeView.findViewById<LinearLayout>(R.id.edgeNavHome)
        val edgeNavMap = edgeView.findViewById<LinearLayout>(R.id.edgeNavMap)
        val edgeNavAlerts = edgeView.findViewById<LinearLayout>(R.id.edgeNavAlerts)
        val edgeNavProfile = edgeView.findViewById<LinearLayout>(R.id.edgeNavProfile)

        val ivHomeIcon = edgeView.findViewById<ImageView>(R.id.ivHomeIcon)
        val tvHomeText = edgeView.findViewById<TextView>(R.id.tvHomeText)
        val ivMapIcon = edgeView.findViewById<ImageView>(R.id.ivMapIcon)
        val tvMapText = edgeView.findViewById<TextView>(R.id.tvMapText)
        val ivAlertsIcon = edgeView.findViewById<ImageView>(R.id.ivAlertsIcon)
        val tvAlertsText = edgeView.findViewById<TextView>(R.id.tvAlertsText)
        val ivProfileIcon = edgeView.findViewById<ImageView>(R.id.ivProfileIcon)
        val tvProfileText = edgeView.findViewById<TextView>(R.id.tvProfileText)

        // Highlight current active tab (iOS style active badge)
        val activeAccentColor = Color.parseColor("#38BDF8")
        when (currentTab) {
            NavTab.HOME -> {
                edgeNavHome.setBackgroundResource(R.drawable.bg_assistive_item_active)
                ivHomeIcon.setColorFilter(activeAccentColor)
                tvHomeText.setTextColor(activeAccentColor)
                tvHomeText.text = "● Home"
            }
            NavTab.MAP -> {
                edgeNavMap.setBackgroundResource(R.drawable.bg_assistive_item_active)
                ivMapIcon.setColorFilter(activeAccentColor)
                tvMapText.setTextColor(activeAccentColor)
                tvMapText.text = "● Map"
            }
            NavTab.ALERTS -> {
                edgeNavAlerts.setBackgroundResource(R.drawable.bg_assistive_item_active)
                ivAlertsIcon.setColorFilter(Color.parseColor("#F87171"))
                tvAlertsText.setTextColor(Color.parseColor("#F87171"))
                tvAlertsText.text = "● Alerts"
            }
            NavTab.PROFILE -> {
                edgeNavProfile.setBackgroundResource(R.drawable.bg_assistive_item_active)
                ivProfileIcon.setColorFilter(activeAccentColor)
                tvProfileText.setTextColor(activeAccentColor)
                tvProfileText.text = "● Profile"
            }
        }

        var isMenuOpen = false

        fun openMenu() {
            if (isMenuOpen) return
            isMenuOpen = true

            edgeAssistiveCard.visibility = View.VISIBLE
            edgeScrim.visibility = View.VISIBLE

            // Spring scale & fade in (iOS AssistiveTouch open animation)
            edgeAssistiveCard.scaleX = 0.4f
            edgeAssistiveCard.scaleY = 0.4f
            edgeAssistiveCard.alpha = 0f
            edgeAssistiveCard.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(240)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()

            edgeScrim.alpha = 0f
            edgeScrim.animate()
                .alpha(1f)
                .setDuration(240)
                .start()

            edgeFloatingButton.animate()
                .alpha(0f)
                .scaleX(0.5f)
                .scaleY(0.5f)
                .setDuration(160)
                .withEndAction { edgeFloatingButton.visibility = View.GONE }
                .start()
        }

        fun closeMenu(onClosed: (() -> Unit)? = null) {
            if (!isMenuOpen) return
            isMenuOpen = false

            edgeAssistiveCard.animate()
                .scaleX(0.4f)
                .scaleY(0.4f)
                .alpha(0f)
                .setDuration(180)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    edgeAssistiveCard.visibility = View.GONE
                    edgeScrim.visibility = View.GONE
                    onClosed?.invoke()
                }
                .start()

            edgeScrim.animate()
                .alpha(0f)
                .setDuration(180)
                .start()

            edgeFloatingButton.visibility = View.VISIBLE
            edgeFloatingButton.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start()
        }

        // Draggable iPhone AssistiveTouch Button
        var dY = 0f
        var startY = 0f
        var isDragging = false

        edgeFloatingButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dY = view.y - event.rawY
                    startY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(event.rawY - startY) > 12) {
                        isDragging = true
                        val parentHeight = (view.parent as? View)?.height ?: rootViewGroup.height
                        val minTop = (60 * activity.resources.displayMetrics.density)
                        val maxBottom = parentHeight - view.height - (40 * activity.resources.displayMetrics.density)
                        val newY = (event.rawY + dY).coerceIn(minTop, maxBottom)
                        view.y = newY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        openMenu()
                    }
                    true
                }
                else -> false
            }
        }

        edgeScrim.setOnClickListener {
            closeMenu()
        }

        edgeNavHome.setOnClickListener {
            if (currentTab == NavTab.HOME) {
                closeMenu {
                    Toast.makeText(activity, "You are on Home Screen", Toast.LENGTH_SHORT).show()
                }
            } else {
                closeMenu {
                    val intent = Intent(activity, DashboardActivity::class.java)
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
        }

        edgeNavMap.setOnClickListener {
            if (currentTab == NavTab.MAP) {
                closeMenu()
            } else {
                closeMenu {
                    val intent = Intent(activity, MapActivity::class.java)
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
        }

        edgeNavAlerts.setOnClickListener {
            if (currentTab == NavTab.ALERTS) {
                closeMenu()
            } else {
                closeMenu {
                    val intent = Intent(activity, AlertsActivity::class.java)
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
        }

        edgeNavProfile.setOnClickListener {
            if (currentTab == NavTab.PROFILE) {
                closeMenu()
            } else {
                closeMenu {
                    val intent = Intent(activity, ProfileActivity::class.java)
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
        }

        rootViewGroup.addView(edgeView)
    }
}
