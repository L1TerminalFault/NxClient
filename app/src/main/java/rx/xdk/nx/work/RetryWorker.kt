package rx.xdk.nx.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import rx.xdk.nx.Notifier
import rx.xdk.nx.Utils
import rx.xdk.nx.db.AppDatabase
import rx.xdk.nx.db.PendingNotification

class RetryWorker(
  context: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
  private val client = OkHttpClient()
  private val dao = AppDatabase.getInstance(context).notificationDao()

  val context: Context = context

  override suspend fun doWork(): Result {
    val pending = dao.getAll()

    for (notif in pending) {
      val json =
        """
        {
          "connectionString": "${notif.connectionString}",
          "title": "${notif.title}",
          "message": "${notif.message}",
          "time": ${notif.time}
        }
        """.trimIndent()

      try {
        val body = json.toRequestBody("application/json".toMediaType())
        val req =
          Request
            .Builder()
            .url(Utils.SERVER_URL)
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
          // Notifier.showNotification(context, "Notification from '$title' sent successfully", 1)
          if (resp.isSuccessful) {
            dao.delete(notif)
            Notifier.showNotification(
              context,
              "Successfully sent stored notification from '${notif.title}'",
              id = 1,
            )
          } else {
            Notifier.showNotification(
              context,
              "Failed to send stored notification from '${notif.title}'",
              id = 1,
            )
            return Result.retry()
          }
        }
      } catch (e: Exception) {
        Notifier.showNotification(
          context,
          "Failed to send stored notification from '${notif.title}'",
          id = 1,
        )
        return Result.retry()
      }
    }

    return Result.success()
  }
}
