package com.example.floodguard

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Helper for attaching and animating the Edge Navigation Panel.
 * Replaces the traditional bottom navigation bar with a sleek, slide-out edge drawer
 * containing Home, Map, Alerts, and Profile destinations.
 */
object EdgeNavigationHelper {

    enum class NavTab {
        HOME, MAP, ALERTS, PROFILE
    }

    @SuppressLint("InflateParams")
    fun attachEdgeNavigation(activity: Activity, currentTab: NavTab) {
        val rootViewGroup = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        // Inflate Edge Navigation layout into the activity root
        val edgeView = LayoutInflater.from(activity).inflate(R.layout.layout_edge_navigation, rootViewGroup, false)

        val edgeScrim = edgeView.findViewById<View>(R.id.edgeScrim)
        val edgeHandle = edgeView.findViewById<LinearLayout>(R.id.edgeHandle)
        val edgePanelContainer = edgeView.findViewById<LinearLayout>(R.id.edgePanelContainer)
        val btnEdgeClose = edgeView.findViewById<ImageView>(R.id.btnEdgeClose)

        val itemNavHome = edgeView.findViewById<LinearLayout>(R.id.edgeNavHome)
        val itemNavMap = edgeView.findViewById<LinearLayout>(R.id.edgeNavMap)
        val itemNavAlerts = edgeView.findViewById<LinearLayout>(R.id.edgeNavAlerts)
        val itemNavProfile = edgeView.findViewById<LinearLayout>(R.id.edgeNavProfile)

        val tagHomeActive = edgeView.findViewById<TextView>(R.id.tagHomeActive)
        val tagMapActive = edgeView.findViewById<TextView>(R.id.tagMapActive)
        val tagAlertsActive = edgeView.findViewById<TextView>(R.id.tagAlertsActive)
        val tagProfileActive = edgeView.findViewById<TextView>(R.id.tagProfileActive)
        val btnEdgeHotline = edgeView.findViewById<LinearLayout>(R.id.btnEdgeHotline)

        // Highlight the current active tab
        when (currentTab) {
            NavTab.HOME -> {
                itemNavHome.setBackgroundResource(R.drawable.bg_edge_item_active)
                tagHomeActive.visibility = View.VISIBLE
            }
            NavTab.MAP -> {
                itemNavMap.setBackgroundResource(R.drawable.bg_edge_item_active)
                tagMapActive.visibility = View.VISIBLE
            }
            NavTab.ALERTS -> {
                itemNavAlerts.setBackgroundResource(R.drawable.bg_edge_item_active)
                tagAlertsActive.visibility = View.VISIBLE
            }
            NavTab.PROFILE -> {
                itemNavProfile.setBackgroundResource(R.drawable.bg_edge_item_active)
                tagProfileActive.visibility = View.VISIBLE
            }
        }

        var isPanelOpen = false
        val panelWidthPx = (300 * activity.resources.displayMetrics.density)

        fun openPanel() {
            if (isPanelOpen) return
            isPanelOpen = true

            edgePanelContainer.visibility = View.VISIBLE
            edgeScrim.visibility = View.VISIBLE

            // Slide in animation from right edge
            edgePanelContainer.translationX = panelWidthPx
            edgePanelContainer.animate()
                .translationX(0f)
                .setDuration(260)
                .setInterpolator(DecelerateInterpolator())
                .start()

            edgeScrim.alpha = 0f
            edgeScrim.animate()
                .alpha(1f)
                .setDuration(260)
                .start()

            edgeHandle.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction { edgeHandle.visibility = View.GONE }
                .start()
        }

        fun closePanel(onClosed: (() -> Unit)? = null) {
            if (!isPanelOpen) return
            isPanelOpen = false

            edgePanelContainer.animate()
                .translationX(panelWidthPx)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    edgePanelContainer.visibility = View.GONE
                    edgeScrim.visibility = View.GONE
                    onClosed?.invoke()
                }
                .start()

            edgeScrim.animate()
                .alpha(0f)
                .setDuration(220)
                .start()

            edgeHandle.visibility = View.VISIBLE
            edgeHandle.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }

        edgeHandle.setOnClickListener {
            openPanel()
        }

        edgeScrim.setOnClickListener {
            closePanel()
        }

        btnEdgeClose.setOnClickListener {
            closePanel()
        }

        itemNavHome.setOnClickListener {
            if (currentTab == NavTab.HOME) {
                closePanel {
                    Toast.makeText(activity, "You are on Home Screen", Toast.LENGTH_SHORT).show()
                }
            } else {
                closePanel {
                    val intent = Intent(activity, DashboardActivity::class.java)
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
        }

        itemNavMap.setOnClickListener {
            if (currentTab == NavTab.MAP) {
                closePanel()
            } else {
                closePanel {
                    val intent = Intent(activity, MapActivity::class.java)
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
        }

        itemNavAlerts.setOnClickListener {
            if (currentTab == NavTab.ALERTS) {
                closePanel()
            } else {
                closePanel {
                    val intent = Intent(activity, AlertsActivity::class.java)
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
        }

        itemNavProfile.setOnClickListener {
            if (currentTab == NavTab.PROFILE) {
                closePanel()
            } else {
                closePanel {
                    val intent = Intent(activity, ProfileActivity::class.java)
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
        }

        btnEdgeHotline.setOnClickListener {
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:0289971830"))
                activity.startActivity(dialIntent)
            } catch (_: Exception) {
                Toast.makeText(activity, "Rescue Hotline: (02) 8997-1830", Toast.LENGTH_LONG).show()
            }
        }

        rootViewGroup.addView(edgeView)
    }
}
