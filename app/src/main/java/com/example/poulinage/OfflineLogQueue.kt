package com.example.poulinage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object OfflineLogQueue {

    private const val PREFS_NAME = "offline_log_queue"
    private const val KEY_PENDING = "pending_logs"
    private const val KEY_TITLE = "title"
    private const val KEY_DESCRIPTION = "description"
    private const val MAX_SIZE = 200

    @Synchronized
    fun enqueue(context: Context, title: String, description: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = parseArray(prefs.getString(KEY_PENDING, null))
        if (array.length() >= MAX_SIZE) array.remove(0)
        array.put(JSONObject().apply {
            put(KEY_TITLE, title)
            put(KEY_DESCRIPTION, description)
        })
        prefs.edit().putString(KEY_PENDING, array.toString()).apply()
    }

    fun flush(context: Context) {
        val snapshot = synchronized(this) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_PENDING, null)
            if (raw.isNullOrEmpty()) return
            val array = parseArray(raw)
            if (array.length() == 0) return
            prefs.edit().remove(KEY_PENDING).apply()
            array
        }
        AppLogger.i(Constants.TAG, "OfflineLogQueue: envoi de ${snapshot.length()} log(s) en attente")
        for (i in 0 until snapshot.length()) {
            val entry = snapshot.optJSONObject(i) ?: continue
            val title = entry.optString(KEY_TITLE)
            val description = entry.optString(KEY_DESCRIPTION)
            if (!RemoteLogger.postDirect(title, description)) {
                enqueue(context, title, description)
            }
        }
    }

    private fun parseArray(raw: String?): JSONArray =
        if (raw.isNullOrEmpty()) JSONArray()
        else runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
}
