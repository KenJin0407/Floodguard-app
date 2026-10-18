package com.example.floodguard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.widget.Toast

object SmsHelper {

    private val SAN_MATEO_COMMUNITY_CONTACTS = listOf(
        "+63 915 245 6879",
        "+63 917 888 1234",
        "+63 920 555 9876",
        "+63 918 333 4567"
    )

    /**
     * Collects contact numbers for all application users registered in San Mateo / flood vicinity.
     */
    fun getSanMateoVicinityContacts(context: Context): List<String> {
        val db = FloodGuardDatabase(context)
        
        FloodGuardApiService.syncUsersFromBackend { remoteUsers ->
            for (u in remoteUsers) {
                db.registerUser(u)
            }
        }

        val registeredMobiles = db.getAllUserMobiles()
        val sessionMobile = UserSession.getUserContact(context)

        val combined = mutableListOf<String>()
        if (sessionMobile.isNotBlank()) combined.add(sessionMobile.trim())
        combined.addAll(registeredMobiles)
        combined.addAll(SAN_MATEO_COMMUNITY_CONTACTS)

        return combined.distinct()
    }

    /**
     * Automatically dispatches the flood report text message to all application users
     * in the San Mateo vicinity when a flood report occurs.
     */
    fun broadcastSmsToAllUsers(
        context: Context,
        location: String = "San Mateo, Rizal",
        severity: String = "Flood Report",
        notes: String = ""
    ) {
        val userName = UserSession.getUserName(context)
        val userContact = UserSession.getUserContact(context)
        val recipientList = getSanMateoVicinityContacts(context)

        val messageBody = buildString {
            append("[FLOODGUARD SAN MATEO VICINITY ALERT]\n")
            append("Reporter: $userName ($userContact)\n")
            append("Flood Area: $location (San Mateo Vicinity)\n")
            append("Severity: $severity\n")
            if (notes.isNotBlank()) {
                append("Notes: $notes\n")
            }
            append("Automated SMS notification dispatched to all app users in San Mateo vicinity.")
        }

        var directSmsSent = false
        try {
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            for (number in recipientList) {
                val cleanNumber = number.replace(" ", "")
                smsManager.sendTextMessage(cleanNumber, null, messageBody, null, null)
            }
            directSmsSent = true
            Toast.makeText(context, "SMS alert broadcasted to ${recipientList.size} app users in San Mateo vicinity!", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            directSmsSent = false
        }

        if (!directSmsSent) {
            val recipientsCombined = recipientList.joinToString(";") { it.replace(" ", "") }
            try {
                val uri = Uri.parse("smsto:$recipientsCombined")
                val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                    putExtra("sms_body", messageBody)
                    putExtra("address", recipientsCombined)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Dispatching SMS broadcast to ${recipientList.size} app users in San Mateo vicinity", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                try {
                    val uri = Uri.parse("sms:$recipientsCombined")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        putExtra("sms_body", messageBody)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Unable to open SMS application on device.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
