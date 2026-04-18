package com.example.poulinage

object Constants {

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------
    const val NOTIFICATION_CHANNEL_ID   = "poulinage_service_channel"
    const val NOTIFICATION_ID           = 1001

    // -------------------------------------------------------------------------
    // SharedPreferences
    // -------------------------------------------------------------------------
    const val PREFS_NAME              = "poulinage_prefs"
    const val PREF_LAST_ALERT_TIME    = "last_alert_time"
    const val PREF_LAST_SMS_TIME      = "last_sms_time"
    const val PREF_PHONE_NUMBER       = "phone_number"      // destinataire SMS

    // Configuration dynamique des seuils de détection
    const val PREF_ACCEL_THRESHOLD          = "accel_threshold"
    const val PREF_ROTATION_THRESHOLD       = "rotation_threshold"
    const val PREF_MIN_CROSSINGS            = "min_crossings"
    const val PREF_MIN_ROTATION_SAMPLES     = "min_rotation_samples"
    const val PREF_WINDOW_SIZE_MS           = "window_size_ms"

    // Config SMTP conservée pour EmailSender (non utilisé par le service actuellement)
    const val PREF_SMTP_HOST          = "smtp_host"
    const val PREF_SMTP_PORT          = "smtp_port"
    const val PREF_SMTP_USER          = "smtp_user"
    const val PREF_SMTP_PASSWORD      = "smtp_password"
    const val PREF_EMAIL_DEST         = "email_destination"

    // -------------------------------------------------------------------------
    // Détection mouvement (accéléromètre)
    // -------------------------------------------------------------------------

    /** Seuil : 1.8g en m/s² — mouvement fort (abaissé pour plus de sensibilité) */
    const val THRESHOLD_MS2      = 1.8f * 9.81f   // ≈ 17.7 m/s²

    /** Fenêtre glissante commune aux deux détecteurs */
    const val WINDOW_SIZE_MS     = 10_000L

    /** Nombre de dépassements (rising edge) nécessaires dans la fenêtre */
    const val MIN_CROSSINGS      = 3

    /** Cooldown partagé entre tous les types d'alerte : 5 secondes */
    const val COOLDOWN_MS        = 5_000L

    /** Cooldown SMS : 1 SMS maximum toutes les 30 secondes */
    const val SMS_COOLDOWN_MS    = 30_000L

    /** Fréquence capteur : 50 ms = 20 Hz */
    const val SENSOR_SAMPLING_US = 50_000

    // ---- Valeurs par défaut pour les seuils (réutilisées si prefs vides) ----
    const val DEFAULT_ACCEL_THRESHOLD      = THRESHOLD_MS2
    const val DEFAULT_ROTATION_THRESHOLD   = 2.0f
    const val DEFAULT_MIN_CROSSINGS        = 3
    const val DEFAULT_MIN_ROTATION_SAMPLES = 6
    const val DEFAULT_WINDOW_SIZE_MS       = WINDOW_SIZE_MS

    // -------------------------------------------------------------------------
    // Détection rotation (gyroscope)
    // -------------------------------------------------------------------------

    /**
     * Seuil de vitesse angulaire : 2.0 rad/s ≈ 115°/s.
     * Une jument qui se roule ou se retourne atteint facilement cette valeur ;
     * une marche normale reste en dessous de 1 rad/s.
     */
    const val ROTATION_THRESHOLD_RADS  = 2.0f

    /**
     * Nombre d'échantillons consécutifs au-dessus du seuil nécessaires pour déclencher.
     * À 20 Hz, 12 échantillons = 0.6 s de rotation soutenue.
     * Compter des échantillons (pas des rising-edges) permet de détecter
     * les rotations soutenues qui ne génèrent qu'un seul crossing.
     */
    const val MIN_ROTATION_SAMPLES     = 6

    // -------------------------------------------------------------------------
    // Types d'alerte
    // -------------------------------------------------------------------------
    const val ALERT_TYPE_MOVEMENT = "MOUVEMENT"
    const val ALERT_TYPE_ROTATION = "ROTATION"

    // -------------------------------------------------------------------------
    // Broadcast Service → Activity
    // -------------------------------------------------------------------------
    const val ACTION_STATUS_UPDATE   = "com.example.poulinage.STATUS_UPDATE"
    const val ACTION_ALERT_FIRED     = "com.example.poulinage.ALERT_FIRED"
    const val ACTION_SENSOR_VALUES   = "com.example.poulinage.SENSOR_VALUES"
    const val EXTRA_STATUS_MESSAGE   = "status_message"
    const val EXTRA_ALERT_TIMESTAMP  = "alert_timestamp"
    const val EXTRA_ALERT_TYPE       = "alert_type"
    const val EXTRA_ALERT_DETAIL     = "alert_detail"
    const val EXTRA_ACCEL_MAGNITUDE  = "accel_magnitude"   // Float m/s²
    const val EXTRA_GYRO_MAGNITUDE   = "gyro_magnitude"    // Float rad/s

    /** Intervalle minimum entre deux broadcasts de valeurs capteur (ms) */
    const val SENSOR_BROADCAST_INTERVAL_MS = 500L

    // -------------------------------------------------------------------------
    // Log tag
    // -------------------------------------------------------------------------
    const val TAG = "Poulinage"
}
