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
    const val PREF_PHONE_NUMBER       = "phone_number"      // destinataire SMS

    // Config SMTP conservée pour EmailSender (non utilisé par le service actuellement)
    const val PREF_SMTP_HOST          = "smtp_host"
    const val PREF_SMTP_PORT          = "smtp_port"
    const val PREF_SMTP_USER          = "smtp_user"
    const val PREF_SMTP_PASSWORD      = "smtp_password"
    const val PREF_EMAIL_DEST         = "email_destination"

    // -------------------------------------------------------------------------
    // Détection mouvement (accéléromètre)
    // -------------------------------------------------------------------------

    /** Seuil : 2.5g en m/s² — mouvement fort */
    const val THRESHOLD_MS2      = 2.5f * 9.81f   // ≈ 24.5 m/s²

    /** Fenêtre glissante commune aux deux détecteurs */
    const val WINDOW_SIZE_MS     = 10_000L

    /** Nombre de dépassements (rising edge) nécessaires dans la fenêtre */
    const val MIN_CROSSINGS      = 6

    /** Cooldown partagé entre tous les types d'alerte : 30 minutes */
    const val COOLDOWN_MS        = 30 * 60 * 1_000L

    /** Fréquence capteur : 50 ms = 20 Hz */
    const val SENSOR_SAMPLING_US = 50_000

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
     * 4 dépassements en 10 secondes suffisent pour la rotation
     * (moins que pour l'accéléromètre car la rotation est plus spécifique).
     */
    const val MIN_ROTATION_CROSSINGS   = 4

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
    const val EXTRA_STATUS_MESSAGE   = "status_message"
    const val EXTRA_ALERT_TIMESTAMP  = "alert_timestamp"
    const val EXTRA_ALERT_TYPE       = "alert_type"
    const val EXTRA_ALERT_DETAIL     = "alert_detail"

    // -------------------------------------------------------------------------
    // Log tag
    // -------------------------------------------------------------------------
    const val TAG = "Poulinage"
}
