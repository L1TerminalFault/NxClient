package rx.xdk.nx

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import rx.xdk.nx.Notifier
import rx.xdk.nx.Utils
import rx.xdk.nx.db.AppDatabase
import rx.xdk.nx.db.PendingNotification
import rx.xdk.nx.work.RetryWorker

class NotifListener : NotificationListenerService() {
  private val client = OkHttpClient()

  override fun onNotificationPosted(sbn: StatusBarNotification) {
    // if (sbn.packageName == "rx.xdk.nx") return
    val notif = sbn.notification ?: return

    val title = notif.extras.getString("android.title") ?: ""
    val text = notif.extras.getString("android.text") ?: ""

    val prefs = getSharedPreferences("nx_prefs", Context.MODE_PRIVATE)
    val connectionString = prefs.getString("connection_string", null) ?: ""
    val allowedChannels = prefs.getStringSet("allowed_channels", emptySet()) ?: emptySet()

    if (!allowedChannels.isNotEmpty() || !allowedChannels.contains(title)) {
      return
    }

    if (connectionString.isEmpty() || connectionString.isBlank()) {
      Notifier.showNotification(this, "Connection string is not set, cannot send notification", 2, id = 1)
      return
    }

    val context: Context = this
    sendToServer(context, connectionString, title, text, System.currentTimeMillis().toString())

    // Notifier.showNotification(
    //   this,
    //   "Notification posted by '${sbn.packageName}' with title '$title' saying '$text'",
    // )
  }

  override fun onListenerConnected() {
    super.onListenerConnected()
    Notifier.showNotification(this, "Listener connected!", id = 1)
    // Log.d("NotifListener", "Listener is ACTIVE")
  }

  private fun savePending(
    context: Context,
    connectionString: String,
    title: String,
    message: String,
    time: String,
  ) {
    val appContext = context.applicationContext
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val notif =
          PendingNotification(
            connectionString = connectionString,
            title = title,
            message = message,
            time = time,
          )
        AppDatabase.getInstance(appContext).notificationDao().insert(notif)
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          // val errorMsg = e.localizedMessage ?: e.message ?: "unknown error"
          Notifier.showNotification(
            appContext,
            "Couldn't save notification to internal database", // errorMsg,
            1,
            id = 1,
          )
        }
      }
    }
  }

  private fun scheduleRetry() {
    val workRequest =
      OneTimeWorkRequestBuilder<RetryWorker>()
        .setConstraints(
          Constraints
            .Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build(),
        ).build()

    WorkManager.getInstance(this).enqueueUniqueWork(
      "retry_notifications",
      ExistingWorkPolicy.KEEP,
      workRequest,
    )
  }

  private fun sendToServer(
    context: Context,
    connectionString: String,
    title: String,
    message: String,
    time: String,
  ) {
    Thread {
      try {
        val json =
          buildJsonObject {
            put("connectionString", connectionString)
            put("title", title)
            put("message", message)
            put("time", time)
          }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val req =
          Request
            .Builder()
            .url(Utils.SERVER_URL)
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) {
            Notifier.showNotification(
              this,
              "Failed to send the notification, will be retried once internet is available",
              1,
              id = 1,
            )
            savePending(context, connectionString, title, message, time)
            scheduleRetry()
          } else {
            Notifier.showNotification(this, "Notification from '$title' sent successfully", id = 1)
          }
        }
      } catch (e: Exception) {
        Notifier.showNotification(
          this,
          "Failed to send the notification, will be retried once internet is available",
          1,
          id = 1,
        )
        savePending(context, connectionString, title, message, time)
        scheduleRetry()
      }
    }.start()
  }
}
