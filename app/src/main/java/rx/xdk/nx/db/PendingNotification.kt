package rx.xdk.nx.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_notifications")
data class PendingNotification(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val connectionString: String,
  val title: String,
  val message: String,
  val time: String,
)
