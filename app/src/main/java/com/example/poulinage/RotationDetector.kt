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
 * Détecte les rotations anormales via le gyroscope.
 *
 * Le gyroscope mesure la vitesse angulaire (rad/s) sur chaque axe.
 * Au repos ou lors d'une marche normale : < 1 rad/s.
 * Lors d'un roulement ou retournement (comme une jument qui se roule) :
 * magnitude > [Constants.ROTATION_THRESHOLD_RADS] de façon répétée.
 *
 * Même algorithme que [AccelerometerHandler] : rising-edge dans une
 * fenêtre glissante, avec cooldown partagé via SharedPreferences.
 */
class RotationDetector(
    private val context: Context,
    private val onAlertTriggered: (timestampMs: Long, detail: String) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val gyroscope: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val handlerThread = HandlerThread("GyroHandlerThread")
    private var gyroHandler: Handler? = null

    // État de l'algorithme de détection
    private var previousMagnitude = 0f
    private val crossingTimestamps = ArrayDeque<Long>()

    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    val isAvailable: Boolean get() = gyroscope != null

    // -------------------------------------------------------------------------
    // Démarrage / arrêt
    // -------------------------------------------------------------------------

    fun start() {
        if (gyroscope == null) {
            Log.w(Constants.TAG, "Pas de gyroscope sur cet appareil — détection rotation désactivée")
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
        Log.i(Constants.TAG, "RotationDetector démarré (seuil=${Constants.ROTATION_THRESHOLD_RADS} rad/s)")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        handlerThread.quitSafely()
        Log.i(Constants.TAG, "RotationDetector arrêté")
    }

    // -------------------------------------------------------------------------
    // SensorEventListener
    // -------------------------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        val wx = event.values[0]  // vitesse angulaire axe X (rad/s)
        val wy = event.values[1]  // vitesse angulaire axe Y (rad/s)
        val wz = event.values[2]  // vitesse angulaire axe Z (rad/s)

        // Magnitude de la vitesse angulaire totale
        val magnitude = sqrt(wx * wx + wy * wy + wz * wz)
        val now = System.currentTimeMillis()

        // Rising edge : passage de sous → sur le seuil de rotation
        if (previousMagnitude < Constants.ROTATION_THRESHOLD_RADS &&
            magnitude >= Constants.ROTATION_THRESHOLD_RADS
        ) {
            crossingTimestamps.addLast(now)
            Log.d(Constants.TAG, "Rotation #${crossingTimestamps.size} — vitesse=%.2f rad/s".format(magnitude))
        }
        previousMagnitude = magnitude

        // Supprimer les événements hors fenêtre
        while (crossingTimestamps.isNotEmpty() &&
            (now - crossingTimestamps.first()) > Constants.WINDOW_SIZE_MS
        ) {
            crossingTimestamps.removeFirst()
        }

        // Déclenchement si seuil de répétition atteint
        if (crossingTimestamps.size >= Constants.MIN_ROTATION_CROSSINGS) {
            val peakMagnitude = magnitude
            crossingTimestamps.clear()
            checkCooldownAndDispatch(now, "vitesse=%.2f rad/s".format(peakMagnitude))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    // -------------------------------------------------------------------------
    // Cooldown (partagé avec AccelerometerHandler via la même clé prefs)
    // -------------------------------------------------------------------------

    private fun checkCooldownAndDispatch(now: Long, detail: String) {
        val lastAlert = prefs.getLong(Constants.PREF_LAST_ALERT_TIME, 0L)
        val elapsed = now - lastAlert

        if (elapsed < Constants.COOLDOWN_MS) {
            val remainingMin = (Constants.COOLDOWN_MS - elapsed) / 60_000
            Log.i(Constants.TAG, "[Rotation] Alerte supprimée — cooldown actif (encore ${remainingMin} min)")
            return
        }

        prefs.edit().putLong(Constants.PREF_LAST_ALERT_TIME, now).apply()
        Log.i(Constants.TAG, "[Rotation] ALERTE déclenchée — $detail")
        onAlertTriggered(now, detail)
    }
}
