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
import com.clerk.api.Clerk

class NotifListener : NotificationListenerService() {
  private val client = OkHttpClient()

  override fun onNotificationPosted(sbn: StatusBarNotification) {
    val notif = sbn.notification ?: return
    val userId = Clerk.userFlow.value?.id ?: return

    // Release only
    if (Utils.BUILD_TYPE == "Release" && sbn.packageName == "rx.xdk.nx") return

    val titleOriginal = notif.extras.getString("android.title") ?: ""
    val text = notif.extras.getString("android.text") ?: ""

    val prefs = getSharedPreferences("nx_prefs", Context.MODE_PRIVATE)
    // val connectionString = prefs.getString("connection_string", null) ?: ""
    val connectionString = userId
    val allowedChannels =
      prefs.getStringSet("allowed_channels", emptySet())?.toMutableSet()
        ?: mutableSetOf()

    val title =
      if (titleOriginal.contains(Utils.T127_TITLE)) {
        Utils.T127_TITLE
      } else if (titleOriginal.contains(Utils.CBE_TITLE)) {
        Utils.CBE_TITLE
      } else if (titleOriginal.contains(Utils.BOA_TITLE)) {
        Utils.BOA_TITLE
      } else {
        titleOriginal
      }

    // Debug only
    if (Utils.BUILD_TYPE == "Debug") {
      if (sbn.packageName != "rx.xdk.nx" || (sbn.packageName == "rx.xdk.nx" && title != "Test")) {
        Notifier.showNotification(
          this,
          "Notification posted by '${sbn.packageName}' with title '$title' saying '$text'",
          title = "Test",
        )
      }

      // Filtering notifiers
      if (allowedChannels.contains(title)) {
        if (sbn.packageName != "rx.xdk.nx" || (sbn.packageName == "rx.xdk.nx" && title != "Test")) {
          Notifier.showNotification(
            this,
            "notification from '${sbn.packageName}' with title '$title' is allowed, processing it",
            title = "Test",
          )
        }
      } else {
        if (sbn.packageName != "rx.xdk.nx" || (sbn.packageName == "rx.xdk.nx" && title != "Test")) {
          Notifier.showNotification(
            this,
            "notification from '${sbn.packageName}' with title '$title' is NOT allowed in '$allowedChannels', skipping it",
            title = "Test",
          )
        }
        return
      }
    }

    // Fallback checker
    // val allowedNotificationTitle = allowedChannels.any { channel -> title.contains(channel) || channel.contains(title) }

    if (!allowedChannels.contains(title)) {
      return
    }

    val contentFilter = true

    // Filtering only incoming transaction notifications
    // For CBE the magic incoming transaction body is " has been Credited with "
    // For 127 the magic incoming transaction body is "You have received "
    // For BOA the magic incoming transaction body is ""

    if (contentFilter) {
      if (title.contains(Utils.CBE_TITLE) && text.lowercase().contains(Utils.CBE_FILTER).not()) {
        // Debug only
        if (Utils.BUILD_TYPE == "Debug") {
          Notifier.showNotification(
            this,
            "Notification from 'CBE' didn't have the proper content to be sent, so dropping",
            title = "Test",
          )
        }

        return
      } else if (title.contains(Utils.T127_TITLE) && text.lowercase().contains(Utils.T127_FILTER).not()) {
        // Debug only
        if (Utils.BUILD_TYPE == "Debug") {
          Notifier.showNotification(
            this,
            "Notification from 'Telebirr' didn't have the proper content to be sent, so dropping",
            title = "Test",
          )
        }

        return
      } else if (title.contains(Utils.BOA_TITLE) && text.lowercase().contains(Utils.BOA_FILTER).not()) {
        // Debug only
        if (Utils.BUILD_TYPE == "Debug") {
          Notifier.showNotification(
            this,
            "Notification from 'BOA' didn't have the proper content to be sent, so dropping",
            title = "Test",
          )
        }

        return
      }
    }

    // Truncate the message
    val truncateMessage = true
    var message = text

    if (truncateMessage) {
      if (title.contains(Utils.CBE_TITLE)) {
        message = text.substringBefore(Utils.CBE_TRUNCATE)
      } else if (title.contains(Utils.BOA_TITLE)) {
        message = text.substringBefore(Utils.BOA_TRUNCATE)
      } else if (title.contains(Utils.T127_TITLE)) {
        message = text.substringBefore(Utils.T127_TRUNCATE)
      }
    }

    var amount = 0.00
    var amountCheckFailed = false

    if (title == "CBE") {
      var amountStr = message.substringAfter("ETB ").substringBefore(". ")
      if (amountStr.all { it.isDigit() || it == ',' || it == '.' }) {
        if (amountStr.contains(",")) {
          amountStr.replace(",", "")
        }
        amount = amountStr.toDouble()
      } else {
        amountCheckFailed = true
      }
    } else {
      val amountStr = message.substringAfter("ETB ").substringBefore(" ")
      if (amountStr.all { it.isDigit() || it == ',' || it == '.' }) {
        if (amountStr.contains(",")) {
          amountStr.replace(",", "")
        }
        amount = amountStr.toDouble()
      } else {
        amountCheckFailed = true
      }
    }

    if (
      !amountCheckFailed &&
      amount > Utils.MAX_ALLOWED_AMOUNT
    ) {
      if (Utils.BUILD_TYPE == "Debug") {
        Notifier.showNotification(
          this,
          "Amount of money '$amount' is more than the allowed value '${Utils.MAX_ALLOWED_AMOUNT}' so dropping",
          2,
          id = 1,
          title = "Test",
        )
      }

      return
    }

    // if (connectionString.isEmpty() || connectionString.isBlank()) {
    //   Notifier.showNotification(this, "Connection string is not set, cannot send notification", 2, id = 1)
    //   return
    // }

    if (Utils.BUILD_TYPE == "Debug") {
      Notifier.showNotification(this, "Sending notification from $title saying $message", title = "Test")
    }

    val context: Context = this
    sendToServer(context, connectionString, title, message, System.currentTimeMillis().toString())
  }

  override fun onListenerConnected() {
    super.onListenerConnected()
    Notifier.showNotification(this, "Listener connected!", id = 1)
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
            .url(Utils.SERVER_POST_URL)
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
            val titleShown = if (title == "127") "Telebirr" else title
            Notifier.showNotification(this, "Notification from '$titleShown' sent successfully")
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
