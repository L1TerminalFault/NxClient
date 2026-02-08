package rx.xdk.nx

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.collection.mutableIntIntMapOf
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig
import rx.xdk.nx.Notifier
import rx.xdk.nx.Utils
import rx.xdk.nx.ui.theme.NxTheme
import kotlin.collections.mutableSetOf

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    Utils.createNotificationChannel(this)
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
		
    if (!Utils.isNotificationServiceEnabled(this)) {
      val intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      startActivity(intent)
    }

    val prefs = getSharedPreferences("nx_prefs", Context.MODE_PRIVATE)
    if (prefs.getStringSet("all_channels", null) == null ||
      prefs.getStringSet("all_channels", null) == emptySet<String>()
    ) {
      val allChannels = setOf<String>("CBE", "BoA", "127")
      prefs.edit().putStringSet("all_channels", allChannels).apply()
    }

    setContent {
      NxTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          mainView(prefs = prefs, qrScanner = ::startQrScanner, modifier = Modifier.padding(innerPadding))
        }
      }
    }
  }

  private val scanQrCodeLauncher =
    registerForActivityResult(ScanCustomCode()) { result ->
      when (result) {
        is QRResult.QRSuccess -> {
          val scannedString = result.content.rawValue ?: ""
          currentCallback?.invoke(scannedString)
          currentCallback = null
        }

        is QRResult.QRUserCanceled -> {
          Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
        }

        is QRResult.QRError -> {
          Toast.makeText(this, "Error scanning: ${result.exception.message}", Toast.LENGTH_LONG).show()
        }

        is QRResult.QRMissingPermission -> {
          Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
        }
      }
    }

  private var currentCallback: ((String) -> Unit)? = null

  private fun startQrScanner(onCodeScanned: (String) -> Unit) {
    currentCallback = onCodeScanned
    scanQrCodeLauncher.launch(
      ScannerConfig
        .Builder()
        .setBarcodeFormats(listOf(BarcodeFormat.FORMAT_QR_CODE))
        .build(),
    )
  }
}

@Composable
fun mainView(
  prefs: SharedPreferences,
  qrScanner: ((String) -> Unit) -> Unit,
  modifier: Modifier = Modifier,
) {
  // val mainBackgroundColor = Color(0xFF131314)
  val buttonColor = Color(0xFA272B31)

  val context = LocalContext.current
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .systemBarsPadding()
        .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
  ) {
    Spacer(modifier = Modifier.fillMaxHeight(0.08f))
    Row(
      modifier = Modifier,
      horizontalArrangement = Arrangement.spacedBy(0.5.dp, Alignment.CenterHorizontally),
    ) {
      Text("NxClient ", modifier = Modifier, fontSize = 21.sp)
      Text(
        "v1.0.0",
        color = Color.Gray,
        fontSize = 9.sp,
        lineHeight = 29.sp,
        modifier = Modifier.align(Alignment.Bottom),
      )
    }
    Text(
      "This app will allow you to pipe your notifications to the configured server, it will pair only to one device through the code provided.",
      color = Color.Gray,
      fontSize = 13.sp,
      lineHeight = 17.sp,
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

    var serviceEnabled = remember { mutableStateOf(Utils.isNotificationServiceEnabled(context)) }
    var notificationPermissionGranted = remember { mutableStateOf<Boolean>(Utils.checkNotificationPermission(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
      val observer =
        LifecycleEventObserver { _, event ->
          if (event == Lifecycle.Event.ON_RESUME) {
            serviceEnabled.value = Utils.isNotificationServiceEnabled(context)
            notificationPermissionGranted.value = Utils.checkNotificationPermission(context)
          }
        }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
      }
    }

    var focusReq = remember { FocusRequester() }
    if (connectionString.value == null || connectionString.value!!.isEmpty()) {
      var textState by remember { mutableStateOf("") }
      val keyboardController = LocalSoftwareKeyboardController.current

      fun submit(text: String = textState) {
        var textInput = text

        if (textInput.isEmpty()) {
          Toast
            .makeText(
              context,
              "Connection string cannot be empty",
              Toast.LENGTH_SHORT,
            ).show()
          return
        }

        if ((
            (
              textInput.contains("-") &&
                textInput.length == 9
            ) || (textInput.contains("-").not() && textInput.length == 8)
          ) && textInput.all { it.isDigit() || it == '-' }
        ) {
          if (textInput.contains("-").not()) {
            textInput = textInput.substring(0, 4) + "-" + textInput.substring(4)
          }

          prefs.edit().putString("connection_string", textInput).apply()
          connectionString.value = textInput
          keyboardController?.hide()
          Toast
            .makeText(
              context,
              "Connection successful",
              Toast.LENGTH_SHORT,
            ).show()
          return
        }
        Toast
          .makeText(
            context,
            "Connection string contains only 8 numbers",
            Toast.LENGTH_LONG,
          ).show()
      }

      Text("Configure", fontSize = 16.sp, lineHeight = 18.sp)
      Spacer(modifier = Modifier.height(14.dp))
      Column(
        modifier = Modifier.widthIn(max = 450.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        LaunchedEffect(Unit) {
          focusReq.requestFocus()
        }

        Row(
          modifier = Modifier, // .widthIn(max = 400.dp),
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
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFA1E1F25),
                unfocusedBorderColor = Color(0xFA1E1F25),
              ),
          )

          IconButton(
            onClick = {
              qrScanner(::submit)
            },
            colors = IconButtonDefaults.iconButtonColors(containerColor = buttonColor, contentColor = Color.White),
          ) {
            Icon(Icons.Default.QrCode, contentDescription = null)
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val show = remember { mutableStateOf(true) }
          if (lastConnectionString.value != null && lastConnectionString.value != "" && show.value) {
            Column(
              modifier = Modifier,
              verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text("Use last connection", fontSize = 10.sp, color = Color.Gray, lineHeight = 10.sp)
              Button(
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
                onClick = {
                  textState = lastConnectionString.value ?: ""
                  show.value = false
                },
                modifier = Modifier.padding(0.dp).height(30.dp).widthIn(max = 120.dp),
              ) {
                val textString = lastConnectionString.value ?: ""
                Text(textString, fontSize = 10.sp, lineHeight = 12.sp)
              }
            }
          }
          Spacer(modifier = Modifier.weight(1f)) // .widthIn(max = 300.dp))

          Button(onClick = {
            submit()
          }, colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White)) {
            Text("Done")
          }
        }
      }
    } else {
      Column(
        modifier = Modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (!serviceEnabled.value) {
          Text(
            "Notifications permission not set",
            fontSize = 16.sp,
            lineHeight = 18.sp,
          )
        } else {
          Text(
            "Listener Connected",
            fontSize = 16.sp,
            lineHeight = 18.sp,
          )
        }
        Row(
          modifier = Modifier,
          horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val str = connectionString.value ?: ""
          Column(
            modifier = Modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
          ) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              "Connection ID: $str",
              fontSize = 12.sp,
              color = Color.Gray,
              lineHeight = 14.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
              modifier = Modifier.padding(0.5.dp),
              colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
              onClick = {
                prefs.edit().putString("last_connection_string", connectionString.value).apply()
                lastConnectionString.value = str
                prefs.edit().remove("connection_string").apply()
                connectionString.value = null
              },
            ) { Text("Change Connection") }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
              "Pipe URL: ${Utils.SERVER_ENDPOINT}/${connectionString.value}",
              fontSize = 12.sp,
              lineHeight = 16.sp,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(3.dp).widthIn(max = 300.dp),
            )
            Text(
              "Your notifications are getting piped through this URL you can check them out in your browser.",
              fontSize = 11.sp,
              color = Color.Gray,
              lineHeight = 15.sp,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(3.dp).widthIn(max = 360.dp),
            )
          }
        }
      }
    }

    if (!serviceEnabled.value) {
      Column(
        modifier = Modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
      ) {
        Spacer(modifier = Modifier.height(6.dp))
        Button(
          modifier = Modifier.padding(0.5.dp),
          colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
          onClick = {
            val intent =
              Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
            context.startActivity(intent)
          },
        ) {
          Text("Enable Notification Access")
        }
        Text(
          "Notification access is required for the app to listen for notification and to function properly",
          color = Color.Gray,
          fontSize = 10.sp,
          lineHeight = 14.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(3.dp).widthIn(max = 300.dp),
        )
      }
    }

    if (!notificationPermissionGranted.value) {
      Column(
        modifier = Modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
      ) {
        Spacer(modifier = Modifier.height(6.dp))
        Button(
          modifier = Modifier.padding(0.5.dp),
          colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
          onClick = {
            val intent =
              Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
            try {
              context.startActivity(intent)
            } catch (e: Exception) {
              val fallbackIntent =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                  data = Uri.fromParts("package", context.packageName, null)
                }
              context.startActivity(fallbackIntent)
            }
          },
        ) {
          Text("Grant Notification Permission")
        }
        Text(
          "Notification permission is required to let you know the status of ongoing notifications to ensure proper functionality",
          color = Color.Gray,
          fontSize = 10.sp,
          lineHeight = 14.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(3.dp).widthIn(max = 300.dp),
        )
      }
    }

    // Debug only
    if (Utils.BUILD_TYPE == "Debug") {
      Button(
        onClick = {
          Notifier
            .showNotification(
              context,
              "Test that You have received something but not known",
              title = "127",
            )
        },
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
      ) {
        Text("Send from telebirr")
      }

      Button(
        onClick = {
          Notifier
            .showNotification(
              context,
              "You have received something but not known",
              title = "BoA",
            )
        },
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
      ) {
        Text("Send from BoA")
      }

      Button(
        onClick = {
          Notifier
            .showNotification(
              context,
              "Testing your account has been Credited with something, This is just a test",
              title = "CBE",
            )
        },
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
      ) {
        Text("Send from CBE")
      }

      Button(
        onClick = {
          Notifier
            .showNotification(
              context,
              "Test notification this notification should be blocked by content filter",
              title = "CBE",
            )
        },
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
      ) {
        Text("Send from CBE non-eligible")
      }
    }

    val allChannels = prefs.getStringSet("all_channels", emptySet()) ?: emptySet()
    val allowedChannels = remember { mutableStateOf(prefs.getStringSet("allowed_channels", emptySet()) ?: emptySet()) }

    var allowedChannelsText =
      if (allowedChannels.value.isNotEmpty()) {
        "Allowed notifications from ${allowedChannels.value.size} channels"
      } else {
        "No allowed channels configured"
      }

    Spacer(modifier = Modifier.weight(1f))
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val showMenu = remember { mutableStateOf(false) }
      Text(
        allowedChannelsText,
        fontSize = 12.sp,
        color = Color.Gray,
        lineHeight = 12.sp,
      )
      IconButton(
        onClick = {
          showMenu.value = true
        },
        colors = IconButtonDefaults.iconButtonColors(containerColor = buttonColor, contentColor = Color.White),
      ) {
        Icon(Icons.Default.Menu, contentDescription = null)

        if (showMenu.value) {
          val selectedChannels =
            remember {
              (allowedChannels.value).toMutableStateList()
            }
          AlertDialog(
            onDismissRequest = {
              showMenu.value = false
            },
            title = {
              Text(
                "Choose Notification Channels",
                fontSize = 16.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
              )
            },
            text = {
              if (allChannels.isEmpty()) {
                Text("No channels available")
              } else {
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                  allChannels.forEach { channel ->
                    val isChecked = selectedChannels.contains(channel) == true

                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      modifier =
                        Modifier
                          .fillMaxWidth()
                          .clickable {
                            if (!isChecked) {
                              selectedChannels.add(channel)
                            } else {
                              selectedChannels.remove(channel)
                            }
                          }.padding(14.dp),
                    ) {
                      Checkbox(
                        checked = isChecked,
                        onCheckedChange = null,
                      )
                      Spacer(modifier = Modifier.widthIn(4.dp))
                      val fullName =
                        when (channel) {
                          "CBE" -> "CBE"
                          "BoA" -> "Bank of Abyssinia"
                          "127" -> "Telebirr"
                          else -> channel
                        }
                      Text(fullName, modifier = Modifier)
                    }
                  }
                }
              }
            },
            confirmButton = {
              Button(
                onClick = {
                  val finalSet = selectedChannels.toSet()
                  prefs.edit().putStringSet("allowed_channels", finalSet).apply()
                  allowedChannels.value = finalSet
                  showMenu.value = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
              ) {
                Text("Done")
              }
            },
          )
        }
      }
    }
    Text("2026", fontSize = 6.sp, lineHeight = 6.sp, color = Color.Gray)
  }
}
