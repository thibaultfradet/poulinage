package com.example.poulinage

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Logger en mémoire accessible depuis n'importe quelle classe de l'app.
 * Tamponne les 300 dernières entrées (circulaire).
 * Thread-safe via CopyOnWriteArrayList.
 */
object AppLogger {

    private const val MAX_ENTRIES = 300
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.FRANCE)

    data class Entry(
        val timestamp: Long,
        val level: Char,     // 'I', 'D', 'W', 'E'
        val tag: String,
        val message: String
    ) {
        val formatted: String
            get() = "[${dateFormat.format(Date(timestamp))}] $level/$tag: $message"
    }

    // CopyOnWriteArrayList : lectures sans lock, thread-safe pour l'UI
    private val entries = CopyOnWriteArrayList<Entry>()

    fun i(tag: String, msg: String) = append('I', tag, msg).also { Log.i(tag, msg) }
    fun d(tag: String, msg: String) = append('D', tag, msg).also { Log.d(tag, msg) }
    fun w(tag: String, msg: String) = append('W', tag, msg).also { Log.w(tag, msg) }
    fun e(tag: String, msg: String) = append('E', tag, msg).also { Log.e(tag, msg) }

    private fun append(level: Char, tag: String, msg: String) {
        entries.add(Entry(System.currentTimeMillis(), level, tag, msg))
        // Supprimer les entrées les plus vieilles si on dépasse la limite
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)
    }

    /** Retourne les entrées du plus récent au plus ancien. */
    fun getAll(): List<Entry> = entries.reversed()

    fun clear() = entries.clear()

    /** Tout le log en texte brut, pour le presse-papier. */
    fun toPlainText(): String = entries.joinToString("\n") { it.formatted }
}
