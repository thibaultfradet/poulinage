package com.example.poulinage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Service foreground qui surveille l'accéléromètre en permanence.
 *
 * - Se lance en premier plan (notification visible) pour survivre aux restrictions API 26+
 * - Acquiert un PARTIAL_WAKE_LOCK pour maintenir le CPU actif écran éteint
 * - Délègue la détection à [AccelerometerHandler]
 * - Envoie l'email via [EmailSender] lors d'une alerte
 * - Communique les mises à jour à [MainActivity] via des broadcasts
 *
 * Retourne START_STICKY : le système redémarre le service s'il est tué.
 */
class FoalingDetectionService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var accelerometerHandler: AccelerometerHandler? = null

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // startForeground DOIT être appelé immédiatement dans onCreate
        startForeground(Constants.NOTIFICATION_ID, buildNotification("Surveillance en cours..."))
        acquireWakeLock()
        startMonitoring()
        Log.i(Constants.TAG, "Service démarré")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY : redémarrage automatique par le système si le service est tué
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        releaseWakeLock()
        broadcastStatus("Surveillance arrêtée")
        Log.i(Constants.TAG, "Service arrêté")
    }

    // Non lié à une Activity, pas de binding
    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------------
    // Canal et notification (obligatoire API 26+)
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW  // IMPORTANCE_LOW = pas de son, discret
        ).apply {
            description = "Surveillance continue des mouvements de la jument"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        // Appuyer sur la notification rouvre l'activité principale
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Détection Poulinage")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)   // ne peut pas être balayée par l'utilisateur
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(Constants.NOTIFICATION_ID, buildNotification(text))
    }

    // -------------------------------------------------------------------------
    // WakeLock
    // -------------------------------------------------------------------------

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,  // CPU actif, écran peut s'éteindre
            "poulinage:detection"
        ).also { it.acquire() }
        Log.i(Constants.TAG, "WakeLock acquis")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.i(Constants.TAG, "WakeLock relâché")
            }
        }
        wakeLock = null
    }

    // -------------------------------------------------------------------------
    // Surveillance
    // -------------------------------------------------------------------------

    private fun startMonitoring() {
        accelerometerHandler = AccelerometerHandler(
            context = this,
            onAlertTriggered = { timestampMs -> handleAlert(timestampMs) }
        )
        accelerometerHandler?.start()
        broadcastStatus("Surveillance active")
    }

    private fun stopMonitoring() {
        accelerometerHandler?.stop()
        accelerometerHandler = null
    }

    private fun handleAlert(timestampMs: Long) {
        val time = timeFormat.format(Date(timestampMs))
        Log.i(Constants.TAG, "Alerte détectée à $time — envoi email")
        updateNotification("ALERTE à $time — envoi email...")
        broadcastAlertFired(timestampMs)

        EmailSender.sendAlertAsync(
            context = this,
            timestampMs = timestampMs,
            onResult = { success, error ->
                if (success) {
                    updateNotification("Email envoyé ($time) — surveillance active")
                    broadcastStatus("Email envoyé à $time")
                } else {
                    Log.e(Constants.TAG, "Erreur envoi email : $error")
                    updateNotification("Erreur email — surveillance active")
                    broadcastStatus("Erreur email : $error")
                }
            }
        )
    }

    // -------------------------------------------------------------------------
    // Communication avec MainActivity (via broadcasts)
    // -------------------------------------------------------------------------

    private fun broadcastStatus(message: String) {
        sendBroadcast(Intent(Constants.ACTION_STATUS_UPDATE).apply {
            putExtra(Constants.EXTRA_STATUS_MESSAGE, message)
            setPackage(packageName)
        })
    }

    private fun broadcastAlertFired(timestampMs: Long) {
        sendBroadcast(Intent(Constants.ACTION_ALERT_FIRED).apply {
            putExtra(Constants.EXTRA_ALERT_TIMESTAMP, timestampMs)
            setPackage(packageName)
        })
    }
}
