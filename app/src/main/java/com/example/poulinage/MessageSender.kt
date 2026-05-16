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
            val msg = "Numéro de téléphone non configuré"
            AppLogger.w(Constants.TAG, "SMS non envoyé — $msg")
            RemoteLogger.warn("SMS_NO_PHONE_NUMBER", mapOf(
                "alert_type" to alertType,
                "detail" to detail
            ))
            onResult?.invoke(false, msg)
            return
        }

        // Normaliser le format local FR → international (+33)
        val phoneNumber = if (rawNumber.startsWith("0") && rawNumber.length == 10) {
            "+33${rawNumber.substring(1)}"
        } else rawNumber

        val maskedNumber = phoneNumber.take(4) + "****" + phoneNumber.takeLast(3)
        val message = buildSmsBody(timestampMs, alertType, detail)

        val smsManager: SmsManager = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    ?: throw IllegalStateException("SmsManager.getSystemService a retourné null")
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (e: Exception) {
            val msg = "Impossible d'obtenir SmsManager : ${e.message}"
            AppLogger.e(Constants.TAG, msg)
            RemoteLogger.error("SMS_MANAGER_INIT_ERROR", e, mapOf(
                "alert_type" to alertType,
                "phone" to maskedNumber,
                "api_level" to Build.VERSION.SDK_INT
            ))
            onResult?.invoke(false, msg)
            return
        }

        val sentAction = "SMS_SENT_$timestampMs"
        val sentIntent: PendingIntent = try {
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(sentAction).setPackage(context.packageName),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (e: Exception) {
            val msg = "Impossible de créer le PendingIntent : ${e.message}"
            AppLogger.e(Constants.TAG, msg)
            RemoteLogger.error("SMS_PENDING_INTENT_ERROR", e, mapOf(
                "alert_type" to alertType,
                "phone" to maskedNumber
            ))
            onResult?.invoke(false, msg)
            return
        }

        val sentReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                try {
                    context.unregisterReceiver(this)
                } catch (e: Exception) {
                    RemoteLogger.warn("SMS_UNREGISTER_RECEIVER_WARNING", mapOf(
                        "warning" to e.message,
                        "alert_type" to alertType
                    ))
                }

                val success = resultCode == Activity.RESULT_OK
                val errorLabel = when (resultCode) {
                    Activity.RESULT_OK                          -> null
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE     -> "RESULT_ERROR_GENERIC_FAILURE"
                    SmsManager.RESULT_ERROR_NO_SERVICE          -> "RESULT_ERROR_NO_SERVICE"
                    SmsManager.RESULT_ERROR_NULL_PDU            -> "RESULT_ERROR_NULL_PDU"
                    SmsManager.RESULT_ERROR_RADIO_OFF           -> "RESULT_ERROR_RADIO_OFF"
                    else                                        -> "UNKNOWN_RESULT_CODE_$resultCode"
                }

                if (success) {
                    AppLogger.i(Constants.TAG, "SMS confirmé envoyé → $maskedNumber")
                } else {
                    val msg = "SMS refusé par le système : $errorLabel (code=$resultCode)"
                    AppLogger.e(Constants.TAG, msg)
                    RemoteLogger.warn("SMS_SENT_FAILURE", mapOf(
                        "result_code" to resultCode,
                        "error_label" to errorLabel,
                        "phone" to maskedNumber,
                        "alert_type" to alertType,
                        "detail" to detail,
                        "message_length" to message.length
                    ))
                }
                onResult?.invoke(success, errorLabel)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(sentReceiver, IntentFilter(sentAction), Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(sentReceiver, IntentFilter(sentAction))
            }
        } catch (e: Exception) {
            val msg = "Impossible d'enregistrer le BroadcastReceiver : ${e.message}"
            AppLogger.e(Constants.TAG, msg)
            RemoteLogger.error("SMS_REGISTER_RECEIVER_ERROR", e, mapOf(
                "alert_type" to alertType,
                "phone" to maskedNumber
            ))
            onResult?.invoke(false, msg)
            return
        }

        try {
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
            AppLogger.i(Constants.TAG, "SMS soumis au système → $maskedNumber (${message.length} cars, ${parts.size} partie(s))")
        } catch (e: SecurityException) {
            val msg = "Permission refusée pour l'envoi SMS : ${e.message}"
            AppLogger.e(Constants.TAG, msg)
            RemoteLogger.error("SMS_SECURITY_EXCEPTION", e, mapOf(
                "alert_type" to alertType,
                "phone" to maskedNumber,
                "hint" to "Vérifier permission SEND_SMS accordée au runtime"
            ))
            onResult?.invoke(false, msg)
        } catch (e: IllegalArgumentException) {
            val msg = "Argument invalide pour l'envoi SMS : ${e.message}"
            AppLogger.e(Constants.TAG, msg)
            RemoteLogger.error("SMS_ILLEGAL_ARGUMENT", e, mapOf(
                "alert_type" to alertType,
                "phone" to maskedNumber,
                "message_length" to message.length,
                "raw_number" to rawNumber.take(4) + "****"
            ))
            onResult?.invoke(false, msg)
        } catch (e: Exception) {
            val msg = "Erreur inattendue lors de l'envoi SMS : ${e.message}"
            AppLogger.e(Constants.TAG, msg)
            RemoteLogger.error("SMS_UNEXPECTED_ERROR", e, mapOf(
                "alert_type" to alertType,
                "phone" to maskedNumber,
                "message_length" to message.length
            ))
            onResult?.invoke(false, msg)
        }
    }

    private fun buildSmsBody(timestampMs: Long, alertType: String, detail: String): String {
        val date = dateFormat.format(Date(timestampMs))
        return "ALERTE POULINAGE [$alertType]\n$date\n$detail\nVérifiez la jument immédiatement."
    }
}
