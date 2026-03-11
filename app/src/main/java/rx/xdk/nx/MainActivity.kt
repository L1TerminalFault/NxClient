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
import androidx.activity.viewModels
import androidx.collection.mutableIntIntMapOf
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.clerk.ui.auth.AuthView
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig
import rx.xdk.nx.Notifier
import rx.xdk.nx.Utils
import rx.xdk.nx.ui.components.TopBar
import rx.xdk.nx.ui.theme.NxTheme
import rx.xdk.nx.R
import kotlin.collections.mutableListOf

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
      val allChannels = setOf<String>("CBE", "BOA", "127")
      prefs.edit().putStringSet("all_channels", allChannels).apply()
    }

    setContent {
      NxTheme {
        Scaffold(
          topBar = {
            TopBar()
          },
          modifier =
            Modifier
              .fillMaxSize(), // .background(Color.Black).containerColor = Color.Black
        ) { innerPadding ->

          val viewModel: MainViewModel by viewModels()
          val state by viewModel.uiState.collectAsStateWithLifecycle()

          Box(
            modifier = Modifier.padding(innerPadding).fillMaxSize(), // .background(Color(0x01000000)),
            // contentAlignment = Alignment.Center,
          ) {
            when (state) {
              is MainUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator()
                }
              }

              is MainUiState.SignedOut -> {
                AuthView()
              }

              is MainUiState.SignedIn -> {
                mainView(prefs = prefs, qrScanner = ::startQrScanner) // , modifier = Modifier.padding(innerPadding))
              }
            }
          }
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
  val vm: MainViewModel = viewModel()
  val context = LocalContext.current

  fun addUser(userId: String) {
    vm.addUser(userId)
  }

  fun fetchUsers() {
    vm.fetchUsers()
  }

  fun removeUser(userId: String) {
    vm.removeUser(userId)
  }

  val users by vm.users.collectAsStateWithLifecycle()
  val lFetch by vm.loadingFetch.collectAsStateWithLifecycle()
  val eFetch by vm.errorFetch.collectAsStateWithLifecycle()

  val addRes by vm.addRes.collectAsStateWithLifecycle()
  val lAdd by vm.loadingAdd.collectAsStateWithLifecycle()

  val remRes by vm.remRes.collectAsStateWithLifecycle()
  val lRem by vm.loadingRem.collectAsStateWithLifecycle()

  Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
    Box(modifier = Modifier.padding(vertical = 8.dp, horizontal = 18.dp).background(color = Color(0x1F000000), shape = RoundedCornerShape(30.dp)).padding(14.dp)) { // .shadow(elevation = 1.dp, shape = RoundedCornerShape(12.dp), clip = false).padding(14.dp)) {
      Row(
        modifier = Modifier.padding(30.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // }, shape = RoundedCornerShape(12.dp)) {
        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(100.dp))
        Text(
          "NxClient will allow you to pipe your notifications to the users of your choice",
          fontSize = 14.sp,
          lineHeight = 20.sp,
        )
      }
    }

    var serviceEnabled = remember { mutableStateOf(Utils.isNotificationServiceEnabled(context)) }
    var notificationPermissionGranted =
      remember { mutableStateOf<Boolean>(Utils.checkNotificationPermission(context)) }

    if (!(serviceEnabled.value && notificationPermissionGranted.value)) {
      // Box(modifier = Modifier.padding(10.dp).shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp), clip = false)) {
      Box(modifier = Modifier.padding(vertical = 8.dp, horizontal = 18.dp).background(color = Color(0x1F000000), shape = RoundedCornerShape(30.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) { // .shadow(elevation = 1.dp, shape = RoundedCornerShape(12.dp), clip = false).padding(14.dp)) {
        Row(
          modifier = Modifier.padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // }, shape = RoundedCornerShape(12.dp)) {
          Spacer(modifier = Modifier.weight(1f))

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

          if (!serviceEnabled.value) {
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
              Text("Grant Permission")
            }
          }

          if (!notificationPermissionGranted.value) {
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
              Text("Get Notifications")
            }
          }
        }
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

    // Box(modifier = Modifier.padding(10.dp).shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp), clip = false)) {
    Box(modifier = Modifier.padding(vertical = 8.dp, horizontal = 18.dp).background(color = Color(0x1F000000), shape = RoundedCornerShape(30.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) { // .shadow(elevation = 1.dp, shape = RoundedCornerShape(12.dp), clip = false).padding(14.dp)) {
      Row(
        modifier = Modifier.padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // }, shape = RoundedCornerShape(12.dp)) {
        val showMenu = remember { mutableStateOf(false) }
        Text(
          allowedChannelsText,
          fontSize = 15.sp,
          // color = Color.Gray,
          lineHeight = 12.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
          onClick = {
            showMenu.value = true
          },
          colors = IconButtonDefaults.iconButtonColors(containerColor = buttonColor, contentColor = Color.White),
        ) {
          Icon(Icons.Default.Settings, contentDescription = null)

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
                            "BOA" -> "Bank of Abyssinia"
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
    }

    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(500, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = ""
    )

    // Box(modifier = Modifier.padding(10.dp).shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp), clip = false)) {
    Box(modifier = Modifier.padding(vertical = 8.dp, horizontal = 18.dp).background(color = Color(0x1F000000), shape = RoundedCornerShape(30.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) { // .shadow(elevation = 1.dp, shape = RoundedCornerShape(12.dp), clip = false).padding(14.dp)) {
      LaunchedEffect(addRes) {
        when (addRes) {
          "success" -> {
            Toast
              .makeText(
                context,
                "User added successfully",
                Toast.LENGTH_SHORT,
              ).show()

            fetchUsers()
          }

          "Add failed" -> {
            Toast
              .makeText(
                context,
                "Failed to add user",
                Toast.LENGTH_SHORT,
              ).show()
          }

          else -> {}
        }

        vm.clearAddRes()
      }

      Row(
        modifier = Modifier.padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // }, shape = RoundedCornerShape(12.dp)) {
        Text("Add users by scanning QR code", fontSize = 15.sp, lineHeight = 12.sp)
        Spacer(modifier = Modifier.weight(1f))

        IconButton(
          modifier = Modifier.padding(0.5.dp),
          colors = IconButtonDefaults.iconButtonColors(containerColor = buttonColor, contentColor = Color.White),
          onClick = {
            qrScanner(::addUser)
          },
        ) {
          if (lAdd) {
            Icon(
              painter = painterResource(id = R.drawable.progress_activity_24px),
              contentDescription = null,
              modifier = Modifier.size(24.dp).rotate(angle),
            )
          } else {
            Icon(Icons.Default.Add, contentDescription = null)
          }
        }

        // Button(
        //   modifier = Modifier.padding(0.5.dp),
        //   colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
        //   onClick = {
        //     qrScanner(::addUser)
        //   },
        // ) {
        //   if (lAdd) {
        //     // CircularProgressIndicator()
        //     Text("Adding...")
        //   } else {
        //     Icon(Icons.Default.Add, contentDescription = null)
        //   }
        // }
      }
    }

    // Box(modifier = Modifier.padding(10.dp).shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp), clip = false)) {
    Box(modifier = Modifier.padding(vertical = 8.dp, horizontal = 18.dp).background(color = Color(0x1F000000), shape = RoundedCornerShape(30.dp))) { // .padding(horizontal = 14.dp, vertical = 8.dp)) { // .shadow(elevation = 1.dp, shape = RoundedCornerShape(12.dp), clip = false).padding(14.dp)) {

      LaunchedEffect(Unit) {
        vm.clearErrorFetch()
        fetchUsers()
      }

      var processingRemoveButton by remember { mutableStateOf<String?>(null) }

      LaunchedEffect(remRes) {
        val currentButton = processingRemoveButton ?: return@LaunchedEffect
        if (remRes!!.isEmpty()) return@LaunchedEffect

        when (remRes) {
          "success" -> {
            Toast
              .makeText(
                context,
                "User removed successfully",
                Toast.LENGTH_SHORT,
              ).show()

            fetchUsers()
          }

          "Remove failed" -> {
            Toast
              .makeText(
                context,
                "Failed to remove user",
                Toast.LENGTH_SHORT,
              ).show()
          }

          else -> {}
        }
        processingRemoveButton = null
        vm.clearRemRes()
      }

      Column(modifier = modifier.fillMaxWidth()) { // , verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Text("Users", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 2.dp, horizontal = 18.dp))

        if (eFetch != null) {
          Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center,) {
            Text(eFetch ?: "", fontSize = 8.sp, color = Color.Red)
          }
        }

        if (lFetch) {
          Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp), contentAlignment = Alignment.Center,) {
            CircularProgressIndicator()
          }
        } else if (users?.size == 0) {
          Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp), contentAlignment = Alignment.Center,) {
            Text("No users", fontSize = 18.sp, color = Color.Gray)
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(users ?: emptyList<User>()) { user ->
              Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                AsyncImage(
                  model = user.profileImage,
                  contentDescription = null,
                  modifier = Modifier.size(50.dp).clip(CircleShape),
                )
                Text(user.userName, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))

                IconButton(
                  modifier = Modifier.padding(0.5.dp),
                  enabled = processingRemoveButton == null,
                  colors = IconButtonDefaults.iconButtonColors(containerColor = buttonColor, contentColor = Color.White),
                  onClick = {
                    processingRemoveButton = user.userId
                    removeUser(user.userId)
                  },
                ) {
                  if (lRem && processingRemoveButton == user.userId) {
                    Icon(
                      painter = painterResource(id = R.drawable.progress_activity_24px),
                      contentDescription = null,
                      modifier = Modifier.size(24.dp).rotate(angle),
                    )
                  } else {
                    Icon(Icons.Default.Remove, contentDescription = null, tint = Color.Red)
                  }
                }

                // Button(
                //   modifier = Modifier.padding(0.5.dp),
                //   enabled = processingRemoveButton == null,
                //   colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
                //   onClick = {
                //     processingRemoveButton = user.userId
                //     removeUser(user.userId)
                //   },
                // ) {
                //   if (lRem && processingRemoveButton == user.userId) {
                //     // CircularProgressIndicator()
                //     Text("Removing...")
                //   } else {
                //     Icon(Icons.Default.Remove, contentDescription = null, tint = Color.Red)
                //   }
                // }
              }
            }
          }
        }
      }
    }

    // NOTE: Only in debug
    if (Utils.BUILD_TYPE == "Debug") {
      Button(
        onClick = {
          Notifier
            .showNotification(
              context,
              "Test that You have received ETB 32.00 Your current E-Money Account balance is not known",
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
              "Your account was credited with ETB 20000.00 Available Balance: ETB 40 something but not known",
              title = "BOA",
            )
        },
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
      ) {
        Text("Send from BOA")
      }

      Button(
        onClick = {
          Notifier
            .showNotification(
              context,
              "your account has been Credited with ETB 100.00. Your Current Balance is something test",
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
              "Testing your account has done been Credited with something, This is just a test",
              title = "BOA",
            )
        },
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
      ) {
        Text("Send from BOA non-eligible")
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
  }
}

// import androidx.compose.foundation.text.KeyboardActions
// import androidx.compose.foundation.text.KeyboardOptions
// import androidx.compose.ui.focus.FocusRequester
// import androidx.compose.ui.focus.focusRequester
// import androidx.compose.ui.platform.LocalSoftwareKeyboardController
//
//   Column(
//     modifier =
//       modifier
//         .fillMaxSize()
//         .systemBarsPadding()
//         .padding(16.dp),
//     horizontalAlignment = Alignment.CenterHorizontally,
//     verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
//   ) {
//     Spacer(modifier = Modifier.fillMaxHeight(0.08f))
//     Row(
//       modifier = Modifier,
//       horizontalArrangement = Arrangement.spacedBy(0.5.dp, Alignment.CenterHorizontally),
//     ) {
//       Text("NxClient ", modifier = Modifier, fontSize = 21.sp)
//       Text(
//         "v1.0.0",
//         color = Color.Gray,
//         fontSize = 9.sp,
//         lineHeight = 29.sp,
//         modifier = Modifier.align(Alignment.Bottom),
//       )
//     }
//     Text(
//       "This app will allow you to pipe your notifications to the configured server, it will pair only to one device through the code provided.",
//       color = Color.Gray,
//       fontSize = 13.sp,
//       lineHeight = 17.sp,
//       textAlign = TextAlign.Center,
//       modifier = Modifier.padding(10.dp).widthIn(max = 450.dp),
//     )
//     Spacer(modifier = Modifier.height(18.dp))
//     val connectionString =
//       remember {
//         mutableStateOf(
//           prefs.getString("connection_string", null),
//         )
//       }
//     val lastConnectionString =
//       remember {
//         mutableStateOf(
//           prefs.getString("last_connection_string", null),
//         )
//       }
//
//     var serviceEnabled = remember { mutableStateOf(Utils.isNotificationServiceEnabled(context)) }
//     var notificationPermissionGranted = remember { mutableStateOf<Boolean>(Utils.checkNotificationPermission(context)) }
//     val lifecycleOwner = LocalLifecycleOwner.current
//
//     DisposableEffect(lifecycleOwner) {
//       val observer =
//         LifecycleEventObserver { _, event ->
//           if (event == Lifecycle.Event.ON_RESUME) {
//             serviceEnabled.value = Utils.isNotificationServiceEnabled(context)
//             notificationPermissionGranted.value = Utils.checkNotificationPermission(context)
//           }
//         }
//       lifecycleOwner.lifecycle.addObserver(observer)
//       onDispose {
//         lifecycleOwner.lifecycle.removeObserver(observer)
//       }
//     }
//
//     var focusReq = remember { FocusRequester() }
//     if (connectionString.value == null || connectionString.value!!.isEmpty()) {
//       var textState by remember { mutableStateOf("") }
//       val keyboardController = LocalSoftwareKeyboardController.current
//
//       fun submit(text: String = textState) {
//         var textInput = text
//
//         if (textInput.isEmpty()) {
//           Toast
//             .makeText(
//               context,
//               "Connection string cannot be empty",
//               Toast.LENGTH_SHORT,
//             ).show()
//           return
//         }
//
//         if ((
//             (
//               textInput.contains("-") &&
//                 textInput.length == 9
//             ) || (textInput.contains("-").not() && textInput.length == 8)
//           ) && textInput.all { it.isDigit() || it == '-' }
//         ) {
//           if (textInput.contains("-").not()) {
//             textInput = textInput.substring(0, 4) + "-" + textInput.substring(4)
//           }
//
//           prefs.edit().putString("connection_string", textInput).apply()
//           connectionString.value = textInput
//           keyboardController?.hide()
//           Toast
//             .makeText(
//               context,
//               "Connection successful",
//               Toast.LENGTH_SHORT,
//             ).show()
//           return
//         }
//         Toast
//           .makeText(
//             context,
//             "Connection string contains only 8 numbers",
//             Toast.LENGTH_LONG,
//           ).show()
//       }
//
//       Text("Configure", fontSize = 16.sp, lineHeight = 18.sp)
//       Spacer(modifier = Modifier.height(14.dp))
//       Column(
//         modifier = Modifier.widthIn(max = 450.dp),
//         verticalArrangement = Arrangement.spacedBy(8.dp),
//         horizontalAlignment = Alignment.CenterHorizontally,
//       ) {
//         LaunchedEffect(Unit) {
//           focusReq.requestFocus()
//         }
//
//         Row(
//           modifier = Modifier, // .widthIn(max = 400.dp),
//           horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
//           verticalAlignment = Alignment.CenterVertically,
//         ) {
//           OutlinedTextField(
//             value = textState,
//             shape = RoundedCornerShape(20.dp),
//             placeholder = { Text("Enter code") },
//             onValueChange = {
//               textState =
//                 it
//             },
//             keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
//             keyboardActions =
//               KeyboardActions(onDone = {
//                 keyboardController?.hide()
//                 submit()
//               }),
//             singleLine = true,
//             modifier = Modifier.focusRequester(focusReq).weight(1f),
//             colors =
//               OutlinedTextFieldDefaults.colors(
//                 focusedBorderColor = Color(0xFA1E1F25),
//                 unfocusedBorderColor = Color(0xFA1E1F25),
//               ),
//           )
//
//           IconButton(
//             onClick = {
//               qrScanner(::submit)
//             },
//             colors = IconButtonDefaults.iconButtonColors(containerColor = buttonColor, contentColor = Color.White),
//           ) {
//             Icon(Icons.Default.QrCode, contentDescription = null)
//           }
//         }
//
//         Row(
//           modifier = Modifier.fillMaxWidth(),
//           horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
//           verticalAlignment = Alignment.CenterVertically,
//         ) {
//           val show = remember { mutableStateOf(true) }
//           if (lastConnectionString.value != null && lastConnectionString.value != "" && show.value) {
//             Column(
//               modifier = Modifier,
//               verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
//               horizontalAlignment = Alignment.CenterHorizontally,
//             ) {
//               Text("Use last connection", fontSize = 10.sp, color = Color.Gray, lineHeight = 10.sp)
//               Button(
//                 colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
//                 onClick = {
//                   textState = lastConnectionString.value ?: ""
//                   show.value = false
//                 },
//                 modifier = Modifier.padding(0.dp).height(30.dp).widthIn(max = 120.dp),
//               ) {
//                 val textString = lastConnectionString.value ?: ""
//                 Text(textString, fontSize = 10.sp, lineHeight = 12.sp)
//               }
//             }
//           }
//           Spacer(modifier = Modifier.weight(1f)) // .widthIn(max = 300.dp))
//
//           Button(onClick = {
//             submit()
//           }, colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White)) {
//             Text("Done")
//           }
//         }
//       }
//     } else {
//       Column(
//         modifier = Modifier,
//         verticalArrangement = Arrangement.spacedBy(12.dp),
//         horizontalAlignment = Alignment.CenterHorizontally,
//       ) {
//         if (!serviceEnabled.value) {
//           Text(
//             "Notifications permission not set",
//             fontSize = 16.sp,
//             lineHeight = 18.sp,
//           )
//         } else {
//           Text(
//             "Listener Connected",
//             fontSize = 16.sp,
//             lineHeight = 18.sp,
//           )
//         }
//         Row(
//           modifier = Modifier,
//           horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
//           verticalAlignment = Alignment.CenterVertically,
//         ) {
//           val str = connectionString.value ?: ""
//           Column(
//             modifier = Modifier,
//             horizontalAlignment = Alignment.CenterHorizontally,
//             verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
//           ) {
//             Spacer(modifier = Modifier.height(6.dp))
//             Text(
//               "Connection ID: $str",
//               fontSize = 12.sp,
//               color = Color.Gray,
//               lineHeight = 14.sp,
//             )
//             Spacer(modifier = Modifier.height(6.dp))
//             Button(
//               modifier = Modifier.padding(0.5.dp),
//               colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
//               onClick = {
//                 prefs.edit().putString("last_connection_string", connectionString.value).apply()
//                 lastConnectionString.value = str
//                 prefs.edit().remove("connection_string").apply()
//                 connectionString.value = null
//               },
//             ) { Text("Change Connection") }
//
//             Spacer(modifier = Modifier.height(10.dp))
//             Text(
//               "Pipe URL: ${Utils.SERVER_ENDPOINT}/${connectionString.value}",
//               fontSize = 12.sp,
//               lineHeight = 16.sp,
//               textAlign = TextAlign.Center,
//               modifier = Modifier.padding(3.dp).widthIn(max = 300.dp),
//             )
//             Text(
//               "Your notifications are getting piped through this URL you can check them out in your browser.",
//               fontSize = 11.sp,
//               color = Color.Gray,
//               lineHeight = 15.sp,
//               textAlign = TextAlign.Center,
//               modifier = Modifier.padding(3.dp).widthIn(max = 360.dp),
//             )
//           }
//         }
//       }
//     }
//
//     if (!serviceEnabled.value) {
//       Column(
//         modifier = Modifier,
//         horizontalAlignment = Alignment.CenterHorizontally,
//         verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
//       ) {
//         Spacer(modifier = Modifier.height(6.dp))
//         Button(
//           modifier = Modifier.padding(0.5.dp),
//           colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
//           onClick = {
//             val intent =
//               Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
//                 addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//               }
//             context.startActivity(intent)
//           },
//         ) {
//           Text("Enable Notification Access")
//         }
//         Text(
//           "Notification access is required for the app to listen for notification and to function properly",
//           color = Color.Gray,
//           fontSize = 10.sp,
//           lineHeight = 14.sp,
//           textAlign = TextAlign.Center,
//           modifier = Modifier.padding(3.dp).widthIn(max = 300.dp),
//         )
//       }
//     }
//
//     if (!notificationPermissionGranted.value) {
//       Column(
//         modifier = Modifier,
//         horizontalAlignment = Alignment.CenterHorizontally,
//         verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
//       ) {
//         Spacer(modifier = Modifier.height(6.dp))
//         Button(
//           modifier = Modifier.padding(0.5.dp),
//           colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
//           onClick = {
//             val intent =
//               Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
//                 putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
//                 addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//               }
//             try {
//               context.startActivity(intent)
//             } catch (e: Exception) {
//               val fallbackIntent =
//                 Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
//                   data = Uri.fromParts("package", context.packageName, null)
//                 }
//               context.startActivity(fallbackIntent)
//             }
//           },
//         ) {
//           Text("Grant Notification Permission")
//         }
//         Text(
//           "Notification permission is required to let you know the status of ongoing notifications to ensure proper functionality",
//           color = Color.Gray,
//           fontSize = 10.sp,
//           lineHeight = 14.sp,
//           textAlign = TextAlign.Center,
//           modifier = Modifier.padding(3.dp).widthIn(max = 300.dp),
//         )
//       }
//     }
//
//     // Debug only
//     if (Utils.BUILD_TYPE == "Debug") {
//       Button(
//         onClick = {
//           Notifier
//             .showNotification(
//               context,
//               "Test that You have received ETB 32.00 Your current E-Money Account balance is not known",
//               title = "127",
//             )
//         },
//         colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
//       ) {
//         Text("Send from telebirr")
//       }
//
//       Button(
//         onClick = {
//           Notifier
//             .showNotification(
//               context,
//               "Your account was credited with ETB 20000.00 Available Balance: ETB 40 something but not known",
//               title = "BOA",
//             )
//         },
//         colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
//       ) {
//         Text("Send from BOA")
//       }
//
//       Button(
//         onClick = {
//           Notifier
//             .showNotification(
//               context,
//               "your account has been Credited with ETB 100.00. Your Current Balance is something test",
//               title = "CBE",
//             )
//         },
//         colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
//       ) {
//         Text("Send from CBE")
//       }
//
//       Button(
//         onClick = {
//           Notifier
//             .showNotification(
//               context,
//               "Testing your account has done been Credited with something, This is just a test",
//               title = "BOA",
//             )
//         },
//         colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
//       ) {
//         Text("Send from BOA non-eligible")
//       }
//
//       Button(
//         onClick = {
//           Notifier
//             .showNotification(
//               context,
//               "Test notification this notification should be blocked by content filter",
//               title = "CBE",
//             )
//         },
//         colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
//       ) {
//         Text("Send from CBE non-eligible")
//       }
//     }
//
//     val allChannels = prefs.getStringSet("all_channels", emptySet()) ?: emptySet()
//     val allowedChannels = remember { mutableStateOf(prefs.getStringSet("allowed_channels", emptySet()) ?: emptySet()) }
//
//     var allowedChannelsText =
//       if (allowedChannels.value.isNotEmpty()) {
//         "Allowed notifications from ${allowedChannels.value.size} channels"
//       } else {
//         "No allowed channels configured"
//       }
//
//     Spacer(modifier = Modifier.weight(1f))
//     Row(
//       horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
//       verticalAlignment = Alignment.CenterVertically,
//     ) {
//       val showMenu = remember { mutableStateOf(false) }
//       Text(
//         allowedChannelsText,
//         fontSize = 12.sp,
//         color = Color.Gray,
//         lineHeight = 12.sp,
//       )
//       IconButton(
//         onClick = {
//           showMenu.value = true
//         },
//         colors = IconButtonDefaults.iconButtonColors(containerColor = buttonColor, contentColor = Color.White),
//       ) {
//         Icon(Icons.Default.Menu, contentDescription = null)
//
//         if (showMenu.value) {
//           val selectedChannels =
//             remember {
//               (allowedChannels.value).toMutableStateList()
//             }
//           AlertDialog(
//             onDismissRequest = {
//               showMenu.value = false
//             },
//             title = {
//               Text(
//                 "Choose Notification Channels",
//                 fontSize = 16.sp,
//                 lineHeight = 18.sp,
//                 textAlign = TextAlign.Center,
//               )
//             },
//             text = {
//               if (allChannels.isEmpty()) {
//                 Text("No channels available")
//               } else {
//                 Column(
//                   modifier = Modifier.fillMaxWidth(),
//                   verticalArrangement = Arrangement.spacedBy(0.dp),
//                 ) {
//                   allChannels.forEach { channel ->
//                     val isChecked = selectedChannels.contains(channel) == true
//
//                     Row(
//                       verticalAlignment = Alignment.CenterVertically,
//                       horizontalArrangement = Arrangement.spacedBy(8.dp),
//                       modifier =
//                         Modifier
//                           .fillMaxWidth()
//                           .clickable {
//                             if (!isChecked) {
//                               selectedChannels.add(channel)
//                             } else {
//                               selectedChannels.remove(channel)
//                             }
//                           }.padding(14.dp),
//                     ) {
//                       Checkbox(
//                         checked = isChecked,
//                         onCheckedChange = null,
//                       )
//                       Spacer(modifier = Modifier.widthIn(4.dp))
//                       val fullName =
//                         when (channel) {
//                           "CBE" -> "CBE"
//                           "BOA" -> "Bank of Abyssinia"
//                           "127" -> "Telebirr"
//                           else -> channel
//                         }
//                       Text(fullName, modifier = Modifier)
//                     }
//                   }
//                 }
//               }
//             },
//             confirmButton = {
//               Button(
//                 onClick = {
//                   val finalSet = selectedChannels.toSet()
//                   prefs.edit().putStringSet("allowed_channels", finalSet).apply()
//                   allowedChannels.value = finalSet
//                   showMenu.value = false
//                 },
//                 colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White),
//               ) {
//                 Text("Done")
//               }
//             },
//           )
//         }
//       }
//     }
//     Text("2026", fontSize = 6.sp, lineHeight = 6.sp, color = Color.Gray)
//   }
// }
