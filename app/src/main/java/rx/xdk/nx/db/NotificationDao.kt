package rx.xdk.nx.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface NotificationDao {
  @Insert
  suspend fun insert(notification: PendingNotification)

  @Query("SELECT * FROM pending_notifications")
  suspend fun getAll(): List<PendingNotification>

  @Delete
  suspend fun delete(notification: PendingNotification)
}
