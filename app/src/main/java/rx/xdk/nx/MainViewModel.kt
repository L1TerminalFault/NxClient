package rx.xdk.nx

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.onFailure
import com.clerk.api.network.serialization.onSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
// import kotlinx.coroutines.flow.collectLatest
// import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import rx.xdk.nx.Utils
import java.net.HttpURLConnection
import java.net.URL

class MainViewModel : ViewModel() {
  private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
  val uiState = _uiState.asStateFlow()

  private val _users = MutableStateFlow<List<User>?>(null) // users
  val users: StateFlow<List<User>?> = _users // users
  private val _loadingFetch = MutableStateFlow(false)
  val loadingFetch: StateFlow<Boolean> = _loadingFetch
  private val _errorFetch = MutableStateFlow<String?>(null)
  val errorFetch: StateFlow<String?> = _errorFetch

  fun clearErrorFetch() {
    _errorFetch.value = null
  }

  private val _addRes = MutableStateFlow<String?>(null)
  val addRes: StateFlow<String?> = _addRes
  private val _loadingAdd = MutableStateFlow(false)
  val loadingAdd: StateFlow<Boolean> = _loadingAdd

  fun clearAddRes() {
    _addRes.value = ""
  }

  private val _remRes = MutableStateFlow<String?>(null)
  val remRes: StateFlow<String?> = _remRes
  private val _loadingRem = MutableStateFlow(false)
  val loadingRem: StateFlow<Boolean> = _loadingRem

  fun clearRemRes() {
    _remRes.value = ""
  }

  init {
    // viewModelScope.launch {
    //   Clerk.sessionFlow.collectLatest { session ->
    //     while (session != null) {
    //       delay(10 * 60 * 1000L) // 10 minutes
    //       try {
    //         session.getToken()
    //       } catch (e: Exception) {
    //         // ignore network/refresh errors, retry next interval
    //       }
    //     }
    //   }
    // }

    combine(Clerk.isInitialized, Clerk.sessionFlow, Clerk.userFlow) { isInitialized, session, user ->
      val currentState = _uiState.value
      // _uiState.value =
      when {
        !isInitialized -> MainUiState.Loading

        user == null && session == null -> MainUiState.SignedOut

        // session != null && user == null -> MainUiState.Loading
        session != null && user != null -> MainUiState.SignedIn

        currentState is MainUiState.SignedIn && (session == null || user == null) -> {
          MainUiState.SignedIn
        }

        // session != null -> MainUiState.SignedIn
        else -> MainUiState.Loading
        // else -> MainUiState.SignedIn
      }
    }.distinctUntilChanged()
      // .debounce(200)
      .onEach { newState ->
        _uiState.value = newState
      }.launchIn(viewModelScope)
  }

  fun fetchUsers() =
    viewModelScope.launch {
      _loadingFetch.value = true
      _errorFetch.value = null
      try {
        val userId = Clerk.userFlow.value?.id

        if (userId != null) {
          val res =
            withContext(Dispatchers.IO) {
              val connection = URL("${Utils.SERVER_GETUSERS_URL}?userId=$userId").openConnection() as HttpURLConnection

              connection.requestMethod = "GET"

              connection.inputStream.bufferedReader().use { it.readText() }
            }

          val result = Json.decodeFromString<UsersResponse>(res)
          _users.value = result.users
        }
      } catch (e: Exception) {
        _errorFetch.value = "Error: ${e.message}"
      } finally {
        _loadingFetch.value = false
      }
    }

  fun addUser(subscriberId: String) =
    viewModelScope.launch {
      _loadingAdd.value = true
      try {
        val userId = Clerk.userFlow.value?.id

        if (userId != null) {
          val res =
            withContext(Dispatchers.IO) {
              val connection =
                URL(
                  "${Utils.SERVER_ADDUSER_URL}?subscriber=$subscriberId&userId=$userId",
                ).openConnection() as HttpURLConnection

              connection.requestMethod = "GET"

              connection.inputStream.bufferedReader().use { it.readText() }
            }

          val result = Json.decodeFromString<ActionResponse>(res)
          _addRes.value = result.status
        }
      } catch (e: Exception) {
        _addRes.value = "Add failed"
      } finally {
        _loadingAdd.value = false
      }
    }

  fun removeUser(subscriberId: String) =
    viewModelScope.launch {
      _loadingRem.value = true
      try {
        val userId = Clerk.userFlow.value?.id

        if (userId != null) {
          val res =
            withContext(Dispatchers.IO) {
              val connection =
                URL(
                  "${Utils.SERVER_REMOVEUSER_URL}?subscriberId=$subscriberId&userId=$userId",
                ).openConnection() as HttpURLConnection

              connection.requestMethod = "GET"

              connection.inputStream.bufferedReader().use { it.readText() }
            }

          val result = Json.decodeFromString<ActionResponse>(res)
          _remRes.value = result.status
        }
      } catch (e: Exception) {
        _remRes.value = "Remove failed"
      } finally {
        _loadingRem.value = false
      }
    }
}

@Serializable
data class ActionResponse(
  val status: String,
)

@Serializable
data class UsersResponse(
  val status: String,
  val users: List<User>? = null,
  val message: String? = "",
)

@Serializable
data class User(
  val userId: String,
  val userName: String,
  val profileImage: String,
)

sealed interface MainUiState {
  data object Loading : MainUiState

  data object SignedIn : MainUiState

  data object SignedOut : MainUiState
}

// import android.util.Log
// import com.clerk.network.serialization.longErrorMessageOrNull
//
// fun signOut() {
//     viewModelScope.launch() {
//         Clerk.shared.signOut()
//         .onSuccess { _uiState.value = MainUiState.SignedOut }
//         .onFailure {
//             // See custom flows error handling docs:
//             // https://clerk.com/docs/custom-flows/error-handling
//             // for more info on error handling
//             Log.e("MainViewModel") // , it.longErrorMessageOrNull, it.throwable)
//         }
//     }
// }
