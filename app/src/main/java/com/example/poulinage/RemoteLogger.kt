package com.example.poulinage

import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RemoteLogger {

    private const val API_URL = "https://api-poulinage.thibault-fradet.fr/api/log"
    private val tsFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.FRANCE)

    fun warn(title: String, context: Map<String, Any?> = emptyMap()) = post(title, context)

    fun error(title: String, e: Throwable? = null, context: Map<String, Any?> = emptyMap()) {
        val ctx = buildMap {
            putAll(context)
            if (e != null) {
                put("exception_type", e.javaClass.simpleName)
                put("exception_message", e.message)
                put("stacktrace", e.stackTraceToString().take(1500))
            }
        }
        post(title, ctx)
    }

    private fun post(title: String, context: Map<String, Any?>) {
        Thread {
            try {
                val desc = JSONObject().apply {
                    put("timestamp", tsFormat.format(Date()))
                    put("device_model", Build.MODEL)
                    put("android_sdk", Build.VERSION.SDK_INT)
                    put("android_release", Build.VERSION.RELEASE)
                    put("manufacturer", Build.MANUFACTURER)
                    context.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
                }.toString()

                val body = JSONObject().apply {
                    put("title", title)
                    put("description", desc)
                }.toString().toByteArray()

                val conn = URL(API_URL).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 6_000
                conn.readTimeout = 6_000
                conn.doOutput = true
                conn.outputStream.use { it.write(body) }
                conn.inputStream.use { }
                conn.disconnect()
            } catch (e: Exception) {
                val msg = "RemoteLogger HTTP échoué (${e.javaClass.simpleName}) : ${e.message}"
                Log.e(Constants.TAG, msg)
                AppLogger.e(Constants.TAG, msg)
            }
        }.also { it.isDaemon = true }.start()
    }
}
