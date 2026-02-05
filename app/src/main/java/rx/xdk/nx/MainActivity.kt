package rx.xdk.nx

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.collection.mutableIntIntMapOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import rx.xdk.nx.Notifier
import rx.xdk.nx.Utils
import rx.xdk.nx.ui.theme.NxTheme

class MainActivity : ComponentActivity() {
  private val channelID = "default_channel_id"

  private fun isNotificationServiceEnabled(context: Context): Boolean {
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

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val name = "General"
      val descriptionText = "General notifications"
      val importance = NotificationManager.IMPORTANCE_HIGH
	
      val channel =
        NotificationChannel(channelID, name, importance).apply {
          description = descriptionText
        }
	
      val notificationManager = getSystemService(NotificationManager::class.java)
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun checkNotificationPermission(): Boolean {
    var granted = true
    if (Build.VERSION.SDK_INT >= 33) {
      granted =
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    }

    return granted
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    createNotificationChannel()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    requestPermissions(
      arrayOf(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE, Manifest.permission.POST_NOTIFICATIONS),
      100,
    )

    val runtimePermissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      runtimePermissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
		
    val permissionsToRequest =
      runtimePermissions.filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
      }
		
    if (permissionsToRequest.isNotEmpty()) {
      ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 100)
    }
		
    if (!isNotificationServiceEnabled(this)) {
      val intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      startActivity(intent)
    }

    val prefs = getSharedPreferences("nx_prefs", Context.MODE_PRIVATE)
    val allowdedChannels = setOf("CBE")
    prefs.edit().putStringSet("allowed_channels", allowdedChannels).apply()

    setContent {
      NxTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          mainView(prefs = prefs, modifier = Modifier.padding(innerPadding))
        }
      }
    }
  }
}

@Composable
fun mainView(
  prefs: SharedPreferences,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  Column(
    modifier = modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
  ) {
    Spacer(modifier = Modifier.fillMaxHeight(0.08f))
    Text("NX Notification Listener", modifier = Modifier, fontSize = 30.sp)
    Text(
      "This app will allow you to pipe your notifications to the configured server, for safety it will block any unregistered notifications from being piped also it will pair only to one device through the link provided, for any questions contact the developer.",
      color = Color.Gray,
      fontSize = 12.sp,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(10.dp).widthIn(max = 450.dp),
    )
    Spacer(modifier = Modifier.height(18.dp))
    val connectionString =
      remember {
        mutableStateOf(
          prefs.getString("connection_string", null),
        )
      }
    val lastConnectionString =
      remember {
        mutableStateOf(
          prefs.getString("last_connection_string", null),
        )
      }
    // val connectionString = prefs.getString("connection_string", null)
    var focusReq = remember { FocusRequester() }
    if (connectionString.value == null || connectionString.value!!.isEmpty()) {
      var textState by remember { mutableStateOf("") }
      val keyboardController = LocalSoftwareKeyboardController.current

      fun submit() {
        if (textState.isEmpty()) {
          Toast
            .makeText(
              context,
              "Connection string cannot be empty",
              Toast.LENGTH_SHORT,
            ).show()
          return
        }
        prefs.edit().putString("connection_string", textState).apply()
        connectionString.value = textState
        keyboardController?.hide()
        Toast
          .makeText(
            context,
            "Connection string saved successfully",
            Toast.LENGTH_SHORT,
          ).show()
      }

      Text("Listener Not Configured", fontSize = 20.sp)
      Spacer(modifier = Modifier.height(14.dp))
      Column(modifier = Modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LaunchedEffect(Unit) {
          focusReq.requestFocus()
        }

        Row(
          modifier = Modifier.widthIn(max = 400.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = textState,
            shape = RoundedCornerShape(20.dp),
            placeholder = { Text("Enter code") },
            onValueChange = {
              textState =
                it
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
              KeyboardActions(onDone = {
                keyboardController?.hide()
                submit()
              }),
            singleLine = true,
            modifier = Modifier.focusRequester(focusReq).weight(1f),
          )

          Button(onClick = {
            submit()
          }) {
            Text("Done")
          }
        }

        val show = remember { mutableStateOf(true) }
        if (lastConnectionString.value != null && show.value) {
          Row(
            modifier = Modifier,
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Button(onClick = {
              textState = lastConnectionString.value ?: ""
              show.value = false
            }) {
              Text("Use last connection")
            }
            Text("${lastConnectionString.value}", fontSize = 12.sp, color = Color.Gray)
          }
        }
      }
    } else {
      Column(
        modifier = Modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text("Listener Running", color = Color.Gray, fontSize = 20.sp)
        Row(
          modifier = Modifier,
          horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val str = connectionString.value ?: ""
          Column(modifier = Modifier) {
            Text("Connection: $str")

            Spacer(modifier = Modifier.height(8.dp))
            Button(modifier = Modifier.padding(1.dp), onClick = {
              prefs.edit().putString("last_connection_string", connectionString.value).apply()
              lastConnectionString.value = str
              prefs.edit().remove("connection_string").apply()
              connectionString.value = null
            }) { Text("Change Connection") }
          }
        }
        Text("Pipe: ${Utils.SERVER_ENDPOINT}/${connectionString.value}", fontSize = 14.sp, color = Color.Gray)
      }
    }

    // Button(onClick = { Notifier.showNotification(context, "Test notification") }) {
    //   Text("Send test notification")
    // }
    // Button(onClick = { Notifier.showNotification(context, "Test notification", title = "CBE") }) {
    //   Text("Send allowed notification")
    // }

    var allowedChannels =
      prefs.getStringSet("allowed_channels", emptySet())?.joinToString(", ")
    if (!allowedChannels.isNullOrEmpty()) {
      allowedChannels = "Allowed channels: $allowedChannels"
    } else {
      allowedChannels = null
    }
    Spacer(modifier = Modifier.weight(1f))
    Text(
      allowedChannels ?: "No allowed channels configured",
      fontSize = 12.sp,
      color = Color.Gray,
    )
    Spacer(modifier = Modifier.height(0.8.dp))
    Text("2026", fontSize = 6.sp, color = Color.Gray)
    Spacer(modifier = Modifier.height(0.1.dp))
  }
}
