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
 * Algorithme : fenêtre glissante de [Constants.WINDOW_SIZE_MS] ms.
 * Chaque fois que la magnitude passe de sous à sur le seuil (rising edge),
 * l'événement est enregistré. Si [Constants.MIN_CROSSINGS] événements
 * sont comptés dans la fenêtre, [onAlertTriggered] est appelé.
 *
 * Le capteur tourne sur un HandlerThread dédié pour ne pas bloquer l'UI.
 */
class AccelerometerHandler(
    private val context: Context,
    private val onAlertTriggered: (timestampMs: Long) -> Unit
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

    // SharedPreferences pour lire/écrire le cooldown
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

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
        handlerThread.start()
        sensorHandler = Handler(handlerThread.looper)
        sensorManager.registerListener(
            this,
            accelerometer,
            Constants.SENSOR_SAMPLING_US,
            sensorHandler
        )
        Log.i(Constants.TAG, "AccelerometerHandler démarré (${Constants.SENSOR_SAMPLING_US / 1000} ms)")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        handlerThread.quitSafely()
        Log.i(Constants.TAG, "AccelerometerHandler arrêté")
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

        // Détection rising edge : passage de sous → sur le seuil
        if (previousMagnitude < Constants.THRESHOLD_MS2 && magnitude >= Constants.THRESHOLD_MS2) {
            crossingTimestamps.addLast(now)
            logEvent("Dépassement #${crossingTimestamps.size} — magnitude=%.2f m/s²".format(magnitude))
        }
        previousMagnitude = magnitude

        // Supprimer les événements plus vieux que la fenêtre
        while (crossingTimestamps.isNotEmpty() &&
            (now - crossingTimestamps.first()) > Constants.WINDOW_SIZE_MS
        ) {
            crossingTimestamps.removeFirst()
        }

        // Vérifier si le seuil de déclenchement est atteint
        if (crossingTimestamps.size >= Constants.MIN_CROSSINGS) {
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
            logEvent("Alerte supprimée — cooldown actif (encore ${remainingMin} min)")
            Log.i(Constants.TAG, "Alerte supprimée par cooldown (encore ${remainingMin} min)")
            return
        }

        // Enregistre l'heure de l'alerte avant d'appeler le callback
        prefs.edit().putLong(Constants.PREF_LAST_ALERT_TIME, now).apply()
        logEvent("ALERTE déclenchée à $now")
        Log.i(Constants.TAG, "ALERTE déclenchée !")
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
