package com.example.poulinage

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        val rawNumber = prefs.getString(Constants.PREF_PHONE_NUMBER, "") ?: ""

        if (rawNumber.isBlank()) {
            AppLogger.w(Constants.TAG, "Numéro non configuré — SMS non envoyé")
            onResult?.invoke(false, "Numéro de téléphone non configuré")
            return
        }

        // Normaliser le format local FR → international (+33)
        val phoneNumber = if (rawNumber.startsWith("0") && rawNumber.length == 10) {
            "+33${rawNumber.substring(1)}"
        } else rawNumber

        val message = buildSmsBody(timestampMs, alertType, detail)

        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    ?: throw IllegalStateException("SmsManager indisponible")
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val sentAction = "SMS_SENT_${timestampMs}"
            val sentIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(sentAction).setPackage(context.packageName),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val sentReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    context.unregisterReceiver(this)
                    val success = resultCode == Activity.RESULT_OK
                    val error = when (resultCode) {
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Erreur générique"
                        SmsManager.RESULT_ERROR_NO_SERVICE      -> "Pas de réseau"
                        SmsManager.RESULT_ERROR_NULL_PDU        -> "PDU null"
                        SmsManager.RESULT_ERROR_RADIO_OFF       -> "Radio éteinte (mode avion ?)"
                        else                                    -> "Code erreur $resultCode"
                    }
                    if (success) {
                        AppLogger.i(Constants.TAG, "SMS confirmé envoyé à $phoneNumber")
                    } else {
                        AppLogger.e(Constants.TAG, "SMS ECHOUE ($phoneNumber) : $error")
                    }
                    onResult?.invoke(success, if (success) null else error)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(sentReceiver, IntentFilter(sentAction), Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(sentReceiver, IntentFilter(sentAction))
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, sentIntent, null)
            } else {
                val sentIntents = ArrayList<PendingIntent?>(parts.size).apply {
                    add(sentIntent)
                    repeat(parts.size - 1) { add(null) }
                }
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null)
            }

            AppLogger.i(Constants.TAG, "SMS soumis au système → $phoneNumber (attente confirmation)")

        } catch (e: Exception) {
            AppLogger.e(Constants.TAG, "Exception envoi SMS : ${e.message}")
            onResult?.invoke(false, e.message)
        }
    }

    private fun buildSmsBody(timestampMs: Long, alertType: String, detail: String): String {
        val date = dateFormat.format(Date(timestampMs))
        return "ALERTE POULINAGE [$alertType]\n$date\n$detail\nVérifiez la jument immédiatement."
    }
}
