package rx.xdk.nx.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PendingNotification::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun notificationDao(): NotificationDao

  companion object {
    @Volatile private var instance: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase =
      instance ?: synchronized(this) {
        instance ?: Room
          .databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "app_db",
          ).build()
          .also { instance = it }
      }
  }
}
