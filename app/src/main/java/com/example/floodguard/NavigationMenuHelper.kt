package com.example.floodguard

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast

/**
 * Helper for rendering the top app bar popup menu.
 * Provides shortcuts to the user's profile, the About FloodGuard dialog, and account logout.
 */
object NavigationMenuHelper {

    @SuppressLint("InflateParams")
    fun showMenu(activity: Activity, anchorView: View) {
        val inflater = LayoutInflater.from(activity)
        val menuView = inflater.inflate(R.layout.nav_dropdown_menu, null)

        val popupWindow = PopupWindow(
            menuView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            elevation = 12f
            isOutsideTouchable = true
        }

        menuView.findViewById<LinearLayout>(R.id.menuProfile)?.setOnClickListener {
            popupWindow.dismiss()
            if (activity !is ProfileActivity) {
                val intent = Intent(activity, ProfileActivity::class.java)
                activity.startActivity(intent)
            } else {
                Toast.makeText(activity, "You are on Profile screen", Toast.LENGTH_SHORT).show()
            }
        }

        menuView.findViewById<LinearLayout>(R.id.menuAbout)?.setOnClickListener {
            popupWindow.dismiss()
            showAboutDialog(activity)
        }

        menuView.findViewById<LinearLayout>(R.id.menuLogout)?.setOnClickListener {
            popupWindow.dismiss()
            confirmLogout(activity)
        }

        popupWindow.showAsDropDown(anchorView, -20, 8, Gravity.END)
    }

    private fun showAboutDialog(activity: Activity) {
        FloodGuardThemedDialog.showAbout(activity)
    }

    private fun confirmLogout(activity: Activity) {
        FloodGuardThemedDialog.showLogout(activity) {
            UserSession.logout(activity)
            val intent = Intent(activity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            activity.startActivity(intent)
            activity.finish()
        }
    }
}
