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
    const val PREF_SMTP_HOST          = "smtp_host"
    const val PREF_SMTP_PORT          = "smtp_port"
    const val PREF_SMTP_USER          = "smtp_user"
    const val PREF_SMTP_PASSWORD      = "smtp_password"
    const val PREF_EMAIL_DEST         = "email_destination"
    const val PREF_LAST_ALERT_TIME    = "last_alert_time"

    // -------------------------------------------------------------------------
    // Algorithme de détection
    // -------------------------------------------------------------------------

    /** Seuil : 2.5g en m/s² — au-delà = mouvement fort */
    const val THRESHOLD_MS2     = 2.5f * 9.81f   // ≈ 24.5 m/s²

    /** Fenêtre glissante : on compte les dépassements sur 10 secondes */
    const val WINDOW_SIZE_MS    = 10_000L

    /** Nombre minimum de dépassements dans la fenêtre pour déclencher l'alerte */
    const val MIN_CROSSINGS     = 6

    /** Cooldown : pas de 2e alerte avant 30 minutes */
    const val COOLDOWN_MS       = 30 * 60 * 1_000L

    /** Fréquence d'échantillonnage du capteur : 50 ms = 20 Hz */
    const val SENSOR_SAMPLING_US = 50_000

    // -------------------------------------------------------------------------
    // Broadcast Service → Activity
    // -------------------------------------------------------------------------
    const val ACTION_STATUS_UPDATE   = "com.example.poulinage.STATUS_UPDATE"
    const val ACTION_ALERT_FIRED     = "com.example.poulinage.ALERT_FIRED"
    const val EXTRA_STATUS_MESSAGE   = "status_message"
    const val EXTRA_ALERT_TIMESTAMP  = "alert_timestamp"

    // -------------------------------------------------------------------------
    // Log tag
    // -------------------------------------------------------------------------
    const val TAG = "Poulinage"
}
