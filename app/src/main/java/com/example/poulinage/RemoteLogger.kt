package com.example.poulinage

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object RemoteLogger {

    private const val API_URL = "https://api-poulinage.thibault-fradet.fr/api/log"
    private val tsFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.FRANCE)

    @Volatile private var appContext: Context? = null

    // Single daemon thread serializes all sends — prevents concurrent flushes and unbounded thread creation.
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r).also { it.isDaemon = true }
    }

    @Synchronized
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun info(title: String, context: Map<String, Any?> = emptyMap()) = post(title, context)
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

    fun flushQueue(context: Context) {
        executor.execute { OfflineLogQueue.flush(context.applicationContext) }
    }

    private fun post(title: String, context: Map<String, Any?>) {
        executor.execute {
            val desc = buildDescription(context)
            if (!postDirect(title, desc)) {
                appContext?.let { OfflineLogQueue.enqueue(it, title, desc) }
            }
        }
    }

    // Synchronous HTTP POST — returns true if the server responded (any HTTP status),
    // false on network failure (IOException) → caller should enqueue for retry.
    internal fun postDirect(title: String, description: String): Boolean {
        return try {
            val body = JSONObject().apply {
                put("title", title)
                put("description", description)
            }.toString().toByteArray()

            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 6_000
            conn.readTimeout = 6_000
            conn.doOutput = true
            conn.outputStream.use { it.write(body) }
            conn.responseCode
            conn.disconnect()
            true
        } catch (e: java.io.IOException) {
            AppLogger.e(Constants.TAG, "RemoteLogger réseau échoué (${e.javaClass.simpleName}) : ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(Constants.TAG, "RemoteLogger erreur inattendue (${e.javaClass.simpleName}) : ${e.message}")
            AppLogger.e(Constants.TAG, "RemoteLogger erreur inattendue (${e.javaClass.simpleName}) : ${e.message}")
            true  // pas réseau → pas de retry utile
        }
    }

    private fun buildDescription(context: Map<String, Any?>): String =
        JSONObject().apply {
            put("timestamp", tsFormat.format(Date()))
            put("device_model", Build.MODEL)
            put("android_sdk", Build.VERSION.SDK_INT)
            put("android_release", Build.VERSION.RELEASE)
            put("manufacturer", Build.MANUFACTURER)
            context.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
        }.toString()
}
