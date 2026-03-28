package com.example.poulinage

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Envoie un SMS d'alerte au numéro configuré dans SharedPreferences.
 *
 * L'envoi réel est commenté ci-dessous.
 * Pour l'activer :
 *   1. Décommenter le bloc marqué TODO
 *   2. Supprimer la ligne "simulation" juste en dessous
 *   3. S'assurer que la permission SEND_SMS est accordée au runtime
 *
 * Note : SmsManager.sendTextMessage() peut être appelé depuis n'importe
 * quel thread (pas de restriction réseau comme SMTP).
 * Si le message dépasse 160 caractères, il est automatiquement divisé.
 */
object MessageSender {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE)

    /**
     * Envoie le SMS d'alerte.
     *
     * @param context       Contexte pour lire le numéro dans SharedPreferences
     * @param timestampMs   Horodatage de la détection (epoch ms)
     * @param alertType     Type d'alerte : [Constants.ALERT_TYPE_MOVEMENT] ou [Constants.ALERT_TYPE_ROTATION]
     * @param detail        Détail technique (ex : "magnitude=28.3 m/s²")
     * @param onResult      Callback (success, errorMessage) — appelé sur le thread courant
     */
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
            Log.w(Constants.TAG, "Numéro de téléphone non configuré — SMS non envoyé")
            onResult?.invoke(false, "Numéro de téléphone non configuré")
            return
        }

        val message = buildSmsBody(timestampMs, alertType, detail)

        /* TODO: Décommenter ce bloc pour activer l'envoi SMS réel.
                 Vérifier que la permission SEND_SMS est accordée avant d'appeler.

        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // divideMessage gère automatiquement les messages > 160 caractères
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }

            Log.i(Constants.TAG, "SMS envoyé à $phoneNumber")
            onResult?.invoke(true, null)

        } catch (e: Exception) {
            Log.e(Constants.TAG, "Erreur envoi SMS : ${e.message}", e)
            onResult?.invoke(false, e.message)
        }
        */

        // -- Simulation (à supprimer lors du décommentage ci-dessus) ----------
        AppLogger.i(Constants.TAG, "[SIMULATION SMS → $phoneNumber] $message")
        onResult?.invoke(true, null)
        // ----------------------------------------------------------------------
    }

    private fun buildSmsBody(timestampMs: Long, alertType: String, detail: String): String {
        val date = dateFormat.format(Date(timestampMs))
        return "ALERTE POULINAGE [$alertType]\n$date\n$detail\nVérifiez la jument immédiatement."
    }
}
