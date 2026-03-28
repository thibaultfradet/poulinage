package com.example.poulinage

import android.content.Context
import android.content.SharedPreferences

/**
 * Gère la persistance de l'historique des alertes dans SharedPreferences.
 *
 * Format de stockage : une entrée par ligne, champs séparés par "|"
 *   "timestamp|TYPE|detail"
 * Les entrées les plus récentes sont en première position.
 * Taille max : [MAX_EVENTS] entrées (les plus vieilles sont supprimées).
 */
object AlertHistory {

    private const val PREF_KEY   = "alert_history"
    private const val MAX_EVENTS = 50
    private const val FIELD_SEP  = "|"
    private const val LINE_SEP   = "\n"

    /** Représente un événement dans l'historique. */
    data class Event(
        val timestamp: Long,
        val type: String,    // ALERT_TYPE_MOVEMENT ou ALERT_TYPE_ROTATION
        val detail: String   // ex : "magnitude=28.3 m/s²"
    )

    /**
     * Ajoute un événement en tête de l'historique et le sauvegarde.
     * Les entrées en excès de [MAX_EVENTS] sont supprimées.
     */
    fun save(context: Context, event: Event) {
        val prefs = prefs(context)
        val existing = load(prefs)
        val updated = (listOf(event) + existing).take(MAX_EVENTS)
        prefs.edit().putString(PREF_KEY, serialize(updated)).apply()
    }

    /** Charge et retourne la liste des événements (du plus récent au plus ancien). */
    fun load(context: Context): List<Event> = load(prefs(context))

    /** Supprime tout l'historique. */
    fun clear(context: Context) {
        prefs(context).edit().remove(PREF_KEY).apply()
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private fun load(prefs: SharedPreferences): List<Event> {
        val raw = prefs.getString(PREF_KEY, "") ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(LINE_SEP).mapNotNull { line ->
            // limit=3 pour que le "detail" puisse contenir des "|" sans être cassé
            val parts = line.split(FIELD_SEP, limit = 3)
            if (parts.size == 3) {
                val ts = parts[0].toLongOrNull() ?: return@mapNotNull null
                Event(timestamp = ts, type = parts[1], detail = parts[2])
            } else null
        }
    }

    private fun serialize(events: List<Event>): String =
        events.joinToString(LINE_SEP) { "${it.timestamp}${FIELD_SEP}${it.type}${FIELD_SEP}${it.detail}" }
}
