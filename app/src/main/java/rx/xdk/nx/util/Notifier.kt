package rx.xdk.nx

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {
  private const val CHANNEL_ID = "default_channel_id"

  fun showNotification(
    context: Context,
    content: String,
    severity: Int = 0,
    title: String = "Nx",
    id: Int = System.currentTimeMillis().toInt(),
  ) {
    val manager = NotificationManagerCompat.from(context)

    val numberOfActiveNotifications = manager.activeNotifications.size

    if (numberOfActiveNotifications >= 24) {
      manager.cancelAll()
    }

    var icon = android.R.drawable.ic_dialog_info
    if (severity == 1) {
      // Warning
      icon = android.R.drawable.ic_dialog_alert
    } else if (severity == 2) {
      // Error
      icon = android.R.drawable.stat_notify_error
    }

    val notification =
      NotificationCompat
        .Builder(context, CHANNEL_ID)
        .setSmallIcon(icon)
        .setContentTitle(title)
        .setContentText(content)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()

    // Permission check (Android 13+)
    if (Build.VERSION.SDK_INT < 33 ||
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
    ) {
      manager.notify(id, notification)
    }
  }
}
