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

  // const val BUILD_TYPE = "Debug"
  const val BUILD_TYPE = "Release"

  const val VERSION = "3.2.0"

  const val SERVER_ENDPOINT = "https://nxsv.vercel.app"
  // const val SERVER_ENDPOINT = "http://192.168.1.4:3000"

  const val SERVER_POST_URL = "$SERVER_ENDPOINT/api/notifications/postNotification"
  const val SERVER_ADDUSER_URL = "$SERVER_ENDPOINT/api/addSubscriber"
  const val SERVER_REMOVEUSER_URL = "$SERVER_ENDPOINT/api/removeSubscriber"
  const val SERVER_GETUSERS_URL = "$SERVER_ENDPOINT/api/getUsersList"

  const val CBE_TITLE = "CBE"
  const val T127_TITLE = "127"
  const val BOA_TITLE = "BOA"

  const val CBE_FILTER = "has been credited with etb"
  const val T127_FILTER = "you have received etb"
  const val BOA_FILTER = "was credited with etb"

  const val CBE_TRUNCATE = "Your Current Balance is "
  const val T127_TRUNCATE = "Your current E-Money Account balance is "
  const val BOA_TRUNCATE = "Available Balance: "

  const val MAX_ALLOWED_AMOUNT: Double = 10000.00

  // TODO: Refactor the logic to this
  // object CBE {
  //   val title = "CBE"
  //   val filterString = "has been credited with"
  //   val truncateString = "your current balance is "
  // }
  //
  // object BOA {
  //   val title = "BOA"
  //   val filterString = "was credited with"
  //   val truncateString = ""
  // }
  //
  // object T127 {
  //   val title = "127"
  //   val filterString = "you have received etb"
  //   val truncateString = ""
  // }

  val channelID = "default_channel_id"

  fun checkNotificationPermission(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

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
