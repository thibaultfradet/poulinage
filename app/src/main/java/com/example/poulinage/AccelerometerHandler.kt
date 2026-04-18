package com.example.poulinage

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlin.math.sqrt

/**
 * Gère l'accéléromètre et détecte les mouvements anormaux.
 *
 * Algorithme : fenêtre glissante dynamique.
 * Chaque fois que la magnitude passe de sous à sur le seuil (rising edge),
 * l'événement est enregistré. Si le nombre minimum de dépassements
 * sont comptés dans la fenêtre, [onAlertTriggered] est appelé.
 *
 * Les seuils sont lus depuis SharedPreferences et peuvent être modifiés
 * dynamiquement par l'utilisateur.
 *
 * Le capteur tourne sur un HandlerThread dédié pour ne pas bloquer l'UI.
 */
class AccelerometerHandler(
    private val context: Context,
    private val onAlertTriggered: (timestampMs: Long) -> Unit,
    private val onValueUpdate: (magnitude: Float) -> Unit = {}
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Thread dédié pour les callbacks capteur
    private val handlerThread = HandlerThread("AccelHandlerThread")
    private var sensorHandler: Handler? = null

    // État de l'algorithme de détection
    private var previousMagnitude = 0f
    private val crossingTimestamps = ArrayDeque<Long>()

    // SharedPreferences pour lire/écrire le cooldown ET les seuils dynamiques
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    // Seuils dynamiques chargés depuis SharedPreferences
    private var thresholdMs2 = Constants.DEFAULT_ACCEL_THRESHOLD
    private var windowSizeMs = Constants.DEFAULT_WINDOW_SIZE_MS
    private var minCrossings = Constants.DEFAULT_MIN_CROSSINGS

    // Log circulaire (200 lignes max) pour le débogage
    private val eventLog = mutableListOf<String>()

    // -------------------------------------------------------------------------
    // Démarrage / arrêt
    // -------------------------------------------------------------------------

    fun start() {
        if (accelerometer == null) {
            Log.e(Constants.TAG, "Pas d'accéléromètre sur cet appareil")
            return
        }
        reloadThresholds()  // Charger les seuils depuis SharedPreferences
        handlerThread.start()
        sensorHandler = Handler(handlerThread.looper)
        sensorManager.registerListener(
            this,
            accelerometer,
            Constants.SENSOR_SAMPLING_US,
            sensorHandler
        )
        AppLogger.i(Constants.TAG, "AccelerometerHandler démarré (seuil=%.1f m/s², min_crossings=$minCrossings)".format(thresholdMs2))
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        handlerThread.quitSafely()
        AppLogger.i(Constants.TAG, "AccelerometerHandler arrêté")
    }

    /** Recharge les seuils depuis SharedPreferences */
    private fun reloadThresholds() {
        thresholdMs2 = prefs.getFloat(Constants.PREF_ACCEL_THRESHOLD, Constants.DEFAULT_ACCEL_THRESHOLD)
        windowSizeMs = prefs.getLong(Constants.PREF_WINDOW_SIZE_MS, Constants.DEFAULT_WINDOW_SIZE_MS)
        minCrossings = prefs.getInt(Constants.PREF_MIN_CROSSINGS, Constants.DEFAULT_MIN_CROSSINGS)
        AppLogger.d(Constants.TAG, "Seuils accéléromètre rechargés: seuil=$thresholdMs2, window=$windowSizeMs, min=$minCrossings")
    }

    // -------------------------------------------------------------------------
    // SensorEventListener
    // -------------------------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        // Magnitude totale incluant la gravité (≈9.81 m/s² au repos)
        val magnitude = sqrt(ax * ax + ay * ay + az * az)
        val now = System.currentTimeMillis()

        onValueUpdate(magnitude)

        // Détection rising edge : passage de sous → sur le seuil dynamique
        if (previousMagnitude < thresholdMs2 && magnitude >= thresholdMs2) {
            crossingTimestamps.addLast(now)
            AppLogger.d(Constants.TAG, "Dépassement #${crossingTimestamps.size} — magnitude=%.2f m/s²".format(magnitude))
        }
        previousMagnitude = magnitude

        // Supprimer les événements plus vieux que la fenêtre dynamique
        while (crossingTimestamps.isNotEmpty() &&
            (now - crossingTimestamps.first()) > windowSizeMs
        ) {
            crossingTimestamps.removeFirst()
        }

        // Vérifier si le seuil de déclenchement est atteint
        if (crossingTimestamps.size >= minCrossings) {
            crossingTimestamps.clear()  // évite les déclenchements en cascade
            checkCooldownAndDispatch(now)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Log.d(Constants.TAG, "Précision capteur changée : $accuracy")
    }

    // -------------------------------------------------------------------------
    // Gestion du cooldown
    // -------------------------------------------------------------------------

    private fun checkCooldownAndDispatch(now: Long) {
        val lastAlert = prefs.getLong(Constants.PREF_LAST_ALERT_TIME, 0L)
        val elapsed = now - lastAlert

        if (elapsed < Constants.COOLDOWN_MS) {
            val remainingMin = (Constants.COOLDOWN_MS - elapsed) / 60_000
            AppLogger.i(Constants.TAG, "Alerte supprimée — cooldown actif (encore ${remainingMin} min)")
            return
        }

        // Enregistre l'heure de l'alerte avant d'appeler le callback
        prefs.edit().putLong(Constants.PREF_LAST_ALERT_TIME, now).apply()
        AppLogger.i(Constants.TAG, "ALERTE mouvement déclenchée !")
        onAlertTriggered(now)
    }

    // -------------------------------------------------------------------------
    // Log
    // -------------------------------------------------------------------------

    private fun logEvent(msg: String) {
        val line = "[${System.currentTimeMillis()}] $msg"
        Log.d(Constants.TAG, line)
        synchronized(eventLog) {
            eventLog.add(line)
            if (eventLog.size > 200) eventLog.removeAt(0)
        }
    }

    /** Retourne une copie du log pour affichage/débogage. */
    fun getEventLog(): List<String> = synchronized(eventLog) { eventLog.toList() }
}
