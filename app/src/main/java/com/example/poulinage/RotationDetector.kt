package com.example.poulinage

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import kotlin.math.sqrt

/**
 * Détecte les rotations anormales via le gyroscope.
 *
 * Algorithme : comptage d'échantillons dans une fenêtre glissante.
 * Chaque sample dont la magnitude dépasse [Constants.ROTATION_THRESHOLD_RADS]
 * est horodaté et ajouté à la file. Si [Constants.MIN_ROTATION_SAMPLES]
 * échantillons sont présents dans la fenêtre de [Constants.WINDOW_SIZE_MS] ms,
 * l'alerte est déclenchée.
 *
 * Pourquoi pas des rising-edges ?
 * Une rotation soutenue (ex: tourner le téléphone d'un geste) reste au-dessus
 * du seuil en continu — elle ne génère qu'un seul rising-edge et ne déclenche
 * jamais avec l'ancien algorithme. Compter les échantillons détecte à la fois
 * les rotations brèves répétées ET les rotations soutenues.
 */
class RotationDetector(
    private val context: Context,
    private val onAlertTriggered: (timestampMs: Long, detail: String) -> Unit,
    private val onValueUpdate: (magnitude: Float) -> Unit = {}
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val gyroscope: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val handlerThread = HandlerThread("GyroHandlerThread")
    private var gyroHandler: Handler? = null

    // Horodatages des échantillons au-dessus du seuil dans la fenêtre courante
    private val samplesAboveThreshold = ArrayDeque<Long>()

    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    val isAvailable: Boolean get() = gyroscope != null

    // -------------------------------------------------------------------------
    // Démarrage / arrêt
    // -------------------------------------------------------------------------

    fun start() {
        if (gyroscope == null) {
            AppLogger.w(Constants.TAG, "Pas de gyroscope — détection rotation désactivée")
            return
        }
        handlerThread.start()
        gyroHandler = Handler(handlerThread.looper)
        sensorManager.registerListener(
            this,
            gyroscope,
            Constants.SENSOR_SAMPLING_US,
            gyroHandler
        )
        AppLogger.i(Constants.TAG, "RotationDetector démarré (seuil=${Constants.ROTATION_THRESHOLD_RADS} rad/s, min=${Constants.MIN_ROTATION_SAMPLES} samples)")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        handlerThread.quitSafely()
        AppLogger.i(Constants.TAG, "RotationDetector arrêté")
    }

    // -------------------------------------------------------------------------
    // SensorEventListener
    // -------------------------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        val wx = event.values[0]
        val wy = event.values[1]
        val wz = event.values[2]
        val magnitude = sqrt(wx * wx + wy * wy + wz * wz)
        val now = System.currentTimeMillis()

        onValueUpdate(magnitude)

        // Ajouter cet échantillon si au-dessus du seuil
        if (magnitude >= Constants.ROTATION_THRESHOLD_RADS) {
            samplesAboveThreshold.addLast(now)
        }

        // Supprimer les échantillons hors de la fenêtre glissante
        while (samplesAboveThreshold.isNotEmpty() &&
            (now - samplesAboveThreshold.first()) > Constants.WINDOW_SIZE_MS
        ) {
            samplesAboveThreshold.removeFirst()
        }

        // Déclencher si assez d'échantillons accumulés
        if (samplesAboveThreshold.size >= Constants.MIN_ROTATION_SAMPLES) {
            val peak = magnitude
            samplesAboveThreshold.clear()
            checkCooldownAndDispatch(now, "vitesse=%.2f rad/s".format(peak))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    // -------------------------------------------------------------------------
    // Cooldown partagé avec AccelerometerHandler
    // -------------------------------------------------------------------------

    private fun checkCooldownAndDispatch(now: Long, detail: String) {
        val lastAlert = prefs.getLong(Constants.PREF_LAST_ALERT_TIME, 0L)
        val elapsed = now - lastAlert

        if (elapsed < Constants.COOLDOWN_MS) {
            val remainingMin = (Constants.COOLDOWN_MS - elapsed) / 60_000
            AppLogger.i(Constants.TAG, "[Rotation] Alerte supprimée — cooldown actif (encore ${remainingMin} min)")
            return
        }

        prefs.edit().putLong(Constants.PREF_LAST_ALERT_TIME, now).apply()
        AppLogger.i(Constants.TAG, "[Rotation] ALERTE déclenchée — $detail")
        onAlertTriggered(now, detail)
    }
}
