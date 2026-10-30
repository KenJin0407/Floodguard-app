package com.example.floodguard

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView

/**
 * Centralized UI helper for FloodGuard themed dialogs.
 * Ensures all modals (Logout, About, Security Codes, Confirmations) adhere strictly
 * to the signature FloodGuard Navy (#0A192F) and Amber Orange (#FFA500) brand aesthetic.
 */
object FloodGuardThemedDialog {

    /** Displays the themed About FloodGuard modal with capabilities overview */
    fun showAbout(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_floodguard_about, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<Button>(R.id.btnAboutClose)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /** Displays the themed Log Out confirmation dialog */
    fun showLogout(activity: Activity, onConfirmLogout: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_floodguard_confirm, null)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.dialogHeaderSubtitle)
        val tvTitle = dialogView.findViewById<TextView>(R.id.dialogConfirmTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.dialogConfirmMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnDialogConfirm)

        tvSubtitle?.text = "Account Session"
        tvTitle?.text = "Log Out of FloodGuard?"
        tvMessage?.text = "Are you sure you want to log out? You will need to sign in again to submit and manage flood reports."

        btnConfirm?.apply {
            text = "Log Out"
            setBackgroundResource(R.drawable.btn_logout_red)
            setTextColor(Color.WHITE)
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm?.setOnClickListener {
            dialog.dismiss()
            onConfirmLogout()
        }

        dialog.show()
    }

    /** Displays the themed Remove / Delete Report confirmation dialog */
    fun showDeleteReport(activity: Activity, locationName: String, onConfirmDelete: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_floodguard_confirm, null)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.dialogHeaderSubtitle)
        val tvTitle = dialogView.findViewById<TextView>(R.id.dialogConfirmTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.dialogConfirmMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnDialogConfirm)

        tvSubtitle?.text = "Report Management"
        tvTitle?.text = "Remove Flood Report?"
        tvMessage?.text = "Are you sure you want to remove the flood report for \"$locationName\"? This action cannot be undone."

        btnConfirm?.apply {
            text = "Remove"
            setBackgroundResource(R.drawable.btn_logout_red)
            setTextColor(Color.WHITE)
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm?.setOnClickListener {
            dialog.dismiss()
            onConfirmDelete()
        }

        dialog.show()
    }

    /** Displays the themed 6-digit Security Code (OTP) dialog */
    fun showSecurityCode(
        activity: Activity,
        code: String,
        notice: String? = null,
        onEnterCode: ((String) -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_floodguard_security_code, null)
        val tvCode = dialogView.findViewById<TextView>(R.id.tvDisplayedOtpCode)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvSecurityCodeSubtitle)
        val btnClose = dialogView.findViewById<Button>(R.id.btnSecurityCodeClose)
        val btnEnter = dialogView.findViewById<Button>(R.id.btnSecurityCodeEnter)

        tvCode?.text = code
        if (!notice.isNullOrBlank()) {
            tvSubtitle?.text = notice
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnClose?.setOnClickListener {
            dialog.dismiss()
        }

        btnEnter?.setOnClickListener {
            dialog.dismiss()
            onEnterCode?.invoke(code)
        }

        dialog.show()
    }

    /** Displays a themed success alert modal (e.g. Password Reset Successful) */
    fun showSuccess(
        activity: Activity,
        title: String,
        message: String,
        buttonText: String = "Got It",
        onDone: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_floodguard_confirm, null)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.dialogHeaderSubtitle)
        val tvTitle = dialogView.findViewById<TextView>(R.id.dialogConfirmTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.dialogConfirmMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnDialogConfirm)

        tvSubtitle?.text = "Notification"
        tvTitle?.text = title
        tvMessage?.text = message

        btnCancel?.visibility = View.GONE
        btnConfirm?.apply {
            text = buttonText
            setBackgroundResource(R.drawable.btn_yellow)
            setTextColor(Color.parseColor("#0A192F"))
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnConfirm?.setOnClickListener {
            dialog.dismiss()
            onDone?.invoke()
        }

        dialog.show()
    }

    /** Displays the themed Daily Report Limit Reached modal adhering to FloodGuard aesthetic */
    fun showDailyLimitReached(activity: Activity, onReturnToDashboard: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_daily_limit_reached, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<Button>(R.id.btnLimitClose)?.setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnLimitDashboard)?.setOnClickListener {
            dialog.dismiss()
            if (onReturnToDashboard != null) {
                onReturnToDashboard()
            } else {
                activity.finish()
            }
        }

        dialog.show()
    }

    /** Displays the themed Report Content / Validation Warning dialog adhering to FloodGuard aesthetic */
    fun showReportContentWarning(
        activity: Activity,
        message: String,
        title: String = "Inappropriate or Invalid Report",
        subtitle: String = "Community Reporting Standards",
        badgeText: String = "⚠️ Revision Needed",
        buttonText: String = "Edit & Fix Report",
        onAction: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_report_content_warning, null)
        dialogView.findViewById<TextView>(R.id.tvReportWarningTitle)?.text = title
        dialogView.findViewById<TextView>(R.id.tvReportWarningSubtitle)?.text = subtitle
        dialogView.findViewById<TextView>(R.id.tvReportWarningBadge)?.text = badgeText
        dialogView.findViewById<TextView>(R.id.tvReportWarningReason)?.text = message

        val btnFix = dialogView.findViewById<Button>(R.id.btnFixReport)
        btnFix?.text = buttonText

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnFix?.setOnClickListener {
            dialog.dismiss()
            onAction?.invoke()
        }

        dialog.show()
    }
}
