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
 * Service foreground de surveillance — actif même écran éteint.
 *
 * Démarre deux détecteurs en parallèle :
 *   - [AccelerometerHandler]  : grands mouvements (chocs, agitation)
 *   - [RotationDetector]      : rotations répétées (se roule, se retourne)
 *
 * Sur alerte :
 *   1. Enregistre l'événement dans [AlertHistory]
 *   2. Envoie un SMS via [MessageSender]
 *   3. Met à jour la notification foreground
 *   4. Broadcast l'alerte vers [MainActivity]
 *
 * Retourne START_STICKY : le système redémarre le service s'il est tué.
 */
class FoalingDetectionService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var accelerometerHandler: AccelerometerHandler? = null
    private var rotationDetector: RotationDetector? = null

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)

    // Dernières valeurs capteur — mises à jour depuis les HandlerThreads des détecteurs
    @Volatile private var lastAccelMagnitude = 0f
    @Volatile private var lastGyroMagnitude  = 0f
    @Volatile private var lastSensorBroadcastMs = 0L

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(Constants.NOTIFICATION_ID, buildNotification("Surveillance en cours..."))
        acquireWakeLock()
        startMonitoring()
        AppLogger.i(Constants.TAG, "Service démarré")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        releaseWakeLock()
        broadcastStatus("Surveillance arrêtée")
        AppLogger.i(Constants.TAG, "Service arrêté")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------------
    // Canal et notification (obligatoire API 26+)
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Surveillance continue des mouvements de la jument"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
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
            .setOngoing(true)
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
            PowerManager.PARTIAL_WAKE_LOCK,
            "poulinage:detection"
        ).also { it.acquire() }
        Log.i(Constants.TAG, "WakeLock acquis")
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // -------------------------------------------------------------------------
    // Surveillance
    // -------------------------------------------------------------------------

    private fun startMonitoring() {
        // Détection grands mouvements (accéléromètre)
        accelerometerHandler = AccelerometerHandler(
            context = this,
            onAlertTriggered = { timestampMs ->
                handleAlert(
                    timestampMs = timestampMs,
                    alertType   = Constants.ALERT_TYPE_MOVEMENT,
                    detail      = "magnitude=%.1f m/s²".format(lastAccelMagnitude)
                )
            },
            onValueUpdate = { magnitude ->
                lastAccelMagnitude = magnitude
                throttledSensorBroadcast()
            }
        )
        accelerometerHandler?.start()

        // Détection rotations (gyroscope) — ignoré silencieusement si absent
        rotationDetector = RotationDetector(
            context = this,
            onAlertTriggered = { timestampMs, detail ->
                handleAlert(
                    timestampMs = timestampMs,
                    alertType   = Constants.ALERT_TYPE_ROTATION,
                    detail      = detail
                )
            },
            onValueUpdate = { magnitude ->
                lastGyroMagnitude = magnitude
                throttledSensorBroadcast()
            }
        )
        rotationDetector?.start()

        val gyroInfo = if (rotationDetector?.isAvailable == true) "gyroscope actif" else "gyroscope absent"
        broadcastStatus("Surveillance active ($gyroInfo)")
        Log.i(Constants.TAG, "Surveillance démarrée — accéléromètre + $gyroInfo")
    }

    private fun stopMonitoring() {
        accelerometerHandler?.stop()
        accelerometerHandler = null
        rotationDetector?.stop()
        rotationDetector = null
    }

    // -------------------------------------------------------------------------
    // Gestion d'une alerte (commune aux deux détecteurs)
    // -------------------------------------------------------------------------

    private fun handleAlert(timestampMs: Long, alertType: String, detail: String) {
        val time = timeFormat.format(Date(timestampMs))
        AppLogger.i(Constants.TAG, "Alerte $alertType à $time — $detail")

        // 1. Enregistrement dans l'historique persistant
        AlertHistory.save(
            context = this,
            event   = AlertHistory.Event(timestamp = timestampMs, type = alertType, detail = detail)
        )

        // 2. Mise à jour notification et broadcast vers l'UI
        updateNotification("ALERTE $alertType à $time")
        broadcastAlertFired(timestampMs, alertType, detail)

        // 3. Envoi SMS — limité à 1 SMS / 30 s
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val lastSms = prefs.getLong(Constants.PREF_LAST_SMS_TIME, 0L)
        if (timestampMs - lastSms >= Constants.SMS_COOLDOWN_MS) {
            prefs.edit().putLong(Constants.PREF_LAST_SMS_TIME, timestampMs).apply()
            MessageSender.sendAlertSms(
                context     = this,
                timestampMs = timestampMs,
                alertType   = alertType,
                detail      = detail,
                onResult    = { success, error ->
                    if (success) {
                        AppLogger.i(Constants.TAG, "SMS envoyé à $time")
                        updateNotification("SMS envoyé ($time) — surveillance active")
                        broadcastStatus("SMS envoyé à $time")
                    } else {
                        AppLogger.e(Constants.TAG, "Erreur SMS : $error")
                        updateNotification("Erreur SMS — surveillance active")
                        broadcastStatus("Erreur SMS : $error")
                    }
                }
            )
        } else {
            val remainingSec = (Constants.SMS_COOLDOWN_MS - (timestampMs - lastSms)) / 1_000
            AppLogger.i(Constants.TAG, "SMS ignoré — cooldown SMS actif (encore ${remainingSec}s)")
            updateNotification("Alerte $alertType ($time) — surveillance active")
        }
    }

    // -------------------------------------------------------------------------
    // Broadcasts vers MainActivity
    // -------------------------------------------------------------------------

    /**
     * Broadcast les valeurs capteur brutes à l'UI, au plus toutes les
     * [Constants.SENSOR_BROADCAST_INTERVAL_MS] ms pour ne pas saturer le main thread.
     * Appelé depuis les HandlerThreads des détecteurs.
     */
    private fun throttledSensorBroadcast() {
        val now = System.currentTimeMillis()
        if (now - lastSensorBroadcastMs < Constants.SENSOR_BROADCAST_INTERVAL_MS) return
        lastSensorBroadcastMs = now
        sendBroadcast(Intent(Constants.ACTION_SENSOR_VALUES).apply {
            putExtra(Constants.EXTRA_ACCEL_MAGNITUDE, lastAccelMagnitude)
            putExtra(Constants.EXTRA_GYRO_MAGNITUDE, lastGyroMagnitude)
            setPackage(packageName)
        })
    }

    private fun broadcastStatus(message: String) {
        sendBroadcast(Intent(Constants.ACTION_STATUS_UPDATE).apply {
            putExtra(Constants.EXTRA_STATUS_MESSAGE, message)
            setPackage(packageName)
        })
    }

    private fun broadcastAlertFired(timestampMs: Long, alertType: String, detail: String) {
        sendBroadcast(Intent(Constants.ACTION_ALERT_FIRED).apply {
            putExtra(Constants.EXTRA_ALERT_TIMESTAMP, timestampMs)
            putExtra(Constants.EXTRA_ALERT_TYPE, alertType)
            putExtra(Constants.EXTRA_ALERT_DETAIL, detail)
            setPackage(packageName)
        })
    }
}
