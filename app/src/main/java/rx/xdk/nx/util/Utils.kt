package rx.xdk.nx

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Utils {
  // val Utils.SERVER_URL = "http://localhost:3000/api/notifications/postNotifications"
  const val SERVER_ENDPOINT = "https://nxsv.vercel.app"
  const val SERVER_URL = "https://nxsv.vercel.app/api/notifications/postNotification"

  const val CBE_FILTER = " has been Credited with "
  const val T127_FILTER = "You have received "

  // TODO: Add BOA filter phrase
  const val BOA_FILTER = ""

  val channelID = "default_channel_id"

  fun checkNotificationPermission(context: Context): Boolean {
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
  }

  fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat =
      Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners",
      )
    if (!flat.isNullOrEmpty()) {
      val names = flat.split(":")
      for (name in names) {
        val cn = android.content.ComponentName.unflattenFromString(name)
        if (cn != null && cn.packageName == pkgName) {
          return true
        }
      }
    }
    return false
  }

  fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val name = "General"
      val descriptionText = "General notifications"
      val importance = NotificationManager.IMPORTANCE_HIGH
	
      val channel =
        NotificationChannel(channelID, name, importance).apply {
          description = descriptionText
        }
	
      val notificationManager = context.getSystemService(NotificationManager::class.java)
      notificationManager.createNotificationChannel(channel)
    }
  }
}
