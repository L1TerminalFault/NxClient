package rx.xdk.nx.ui.components

import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clerk.api.Clerk
import com.clerk.ui.userbutton.UserButton
import rx.xdk.nx.Utils.VERSION
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar() {
  val user by Clerk.userFlow.collectAsStateWithLifecycle()

  TopAppBar(
    title = {
      Row(
        modifier = Modifier.fillMaxWidth() // .background(color = Color.Black)
          .padding(top = 28.dp, bottom = 14.dp, end = 20.dp, start = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(modifier = Modifier, verticalAlignment = Alignment.Bottom) {
          Text(text = "NxClient ")
          Text(text = "v$VERSION", lineHeight = 20.sp, color = Color.Gray, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.weight(1f))

        Text(
          text = user?.username ?: user?.firstName ?: "",
          color = if (user != null) Color.Unspecified else Color.Gray,
          fontSize = 14.sp,
        )
        UserButton()
      }
    },
  )
}
