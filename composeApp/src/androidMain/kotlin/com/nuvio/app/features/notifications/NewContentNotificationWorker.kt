package com.nuvio.app.features.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nuvio.app.core.network.BridgeIdentity
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Notifikasi film/serial baru dari bridge HOFLIX.
 *
 * Satu notifikasi per judul, sesuai keputusan produk: pengguna ingin tahu
 * judulnya, bukan ringkasan gabungan. Kursor disimpan di SharedPreferences,
 * jadi judul yang sama tidak dinotifikasi dua kali.
 */
object NewContentNotifications {

    private const val PREFS = "hoflix_new_content"
    private const val KEY_CURSOR = "cursor"
    private const val KEY_ENABLED = "enabled"
    private const val CHANNEL_ID = "hoflix_new_content"
    private const val WORK_NAME = "hoflix-new-content"
    private const val BASE = "https://hoflix-api.siho.my.id"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) schedule(context) else WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun schedule(context: Context) {
        if (!isEnabled(context)) return
        val request = PeriodicWorkRequestBuilder<NewContentNotificationWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    internal fun cursor(context: Context): String =
        prefs(context).getString(KEY_CURSOR, "").orEmpty()

    internal fun saveCursor(context: Context, cursor: String) {
        if (cursor.isBlank()) return
        prefs(context).edit().putString(KEY_CURSOR, cursor).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    internal fun post(context: Context, title: String, body: String, deepLink: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(deepLink)).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            title.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.nuvio.app.R.drawable.ic_notification_small)
            .setLargeIcon(
                ContextCompat.getDrawable(context, com.nuvio.app.R.drawable.ic_notification_large)
                    ?.toBitmap(),
            )
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(title.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Konten baru HOFLIX",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Film dan serial baru di katalog HOFLIX"
            },
        )
    }

    internal fun baseUrl(): String = BASE
}

class NewContentNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!NewContentNotifications.isEnabled(applicationContext)) return Result.success()
        val cursor = NewContentNotifications.cursor(applicationContext)
        val body = fetch(NewContentNotifications.baseUrl(), cursor) ?: return Result.retry()

        val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return Result.success()
        val newCursor = json["cursor"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val items = json["items"]?.jsonArray ?: return Result.success()
        if (cursor.isBlank()) {
            // Panggilan pertama hanya menetapkan baseline.
            NewContentNotifications.saveCursor(applicationContext, newCursor)
            return Result.success()
        }
        items.forEach { element ->
            val item = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
            val name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (name.isBlank()) return@forEach
            val type = item["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val link = item["deepLink"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (link.isBlank()) return@forEach
            val label = if (type == "series") "Serial baru" else "Film baru"
            NewContentNotifications.post(applicationContext, name, label, link)
        }
        NewContentNotifications.saveCursor(applicationContext, newCursor)
        return Result.success()
    }

    private fun fetch(base: String, cursor: String): String? {
        val query = if (cursor.isBlank()) "" else "?since=${java.net.URLEncoder.encode(cursor, "UTF-8")}"
        return runCatching {
            val connection = (URL("$base/new.json$query").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                requestMethod = "GET"
                // `/new.json` ada di balik gate keanggotaan: tanpa sesi bridge
                // membalas 401 dan worker diam-diam berhenti memberi notifikasi.
                BridgeIdentity.headers().forEach { (name, value) -> setRequestProperty(name, value) }
            }
            try {
                if (connection.responseCode != 200) return null
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
