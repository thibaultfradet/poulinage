package com.example.poulinage

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Envoie un SMS d'alerte au numéro configuré dans SharedPreferences.
 * La permission SEND_SMS doit être accordée au runtime avant l'appel.
 */
object MessageSender {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE)

    fun sendAlertSms(
        context: Context,
        timestampMs: Long,
        alertType: String,
        detail: String,
        onResult: ((success: Boolean, error: String?) -> Unit)? = null
    ) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val phoneNumber = prefs.getString(Constants.PREF_PHONE_NUMBER, "") ?: ""

        if (phoneNumber.isBlank()) {
            AppLogger.w(Constants.TAG, "Numéro non configuré — SMS non envoyé")
            onResult?.invoke(false, "Numéro de téléphone non configuré")
            return
        }

        val message = buildSmsBody(timestampMs, alertType, detail)

        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // divideMessage gère les messages > 160 caractères
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }

            AppLogger.i(Constants.TAG, "SMS envoyé à $phoneNumber")
            onResult?.invoke(true, null)

        } catch (e: Exception) {
            AppLogger.e(Constants.TAG, "Erreur envoi SMS : ${e.message}")
            onResult?.invoke(false, e.message)
        }
    }

    private fun buildSmsBody(timestampMs: Long, alertType: String, detail: String): String {
        val date = dateFormat.format(Date(timestampMs))
        return "ALERTE POULINAGE [$alertType]\n$date\n$detail\nVérifiez la jument immédiatement."
    }
}
