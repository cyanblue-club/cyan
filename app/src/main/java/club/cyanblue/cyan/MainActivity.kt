package club.cyanblue.cyan

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import club.cyanblue.cyan.ui.theme.CyanTheme
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.firebase.appcheck.ktx.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Firebase.initialize(context = this)
        Firebase.appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
        auth = Firebase.auth
        val db = Firebase.firestore
        val userService = FireStoreService(db)

        val statusBarManager: StatusBarManager = getSystemService(StatusBarManager::class.java)

        startService(Intent(this, MeTileService::class.java))

        setContent {
            CyanTheme {
                var currentUser by remember { mutableStateOf(auth.currentUser) }

                var user: User? by remember { mutableStateOf(null) }

                val pullRefreshState = rememberPullToRefreshState()

                if (pullRefreshState.isRefreshing) {
                    currentUser?.uid?.let { uid ->
                        userService.getUser(uid, true, onError = {
                            Log.e("Log", it.toString())
                        }) {
                            user = it
                            userService.getPrivateAccounts(user!!, onError = { it2 ->
                                Log.e("Log", it2.toString())
                            }) { it2 ->
                                user!!.personalAccounts = it2 as MutableList<Account>
                            }
                            pullRefreshState.endRefresh()
                        }
                    }
                }

                currentUser?.uid?.let { uid ->
                    userService.getUser(uid, true, onError = {
                        Log.e("Log", it.toString())
                    }) {
                        user = it
                        userService.getPrivateAccounts(user!!, onError = { it2 ->
                            Log.e("Log", it2.toString())
                        }) { it2 ->
                            user!!.personalAccounts = it2 as MutableList<Account>
                        }
                    }
                }

                var settings by remember { mutableStateOf(false) }
                var edit by remember { mutableStateOf(false) }
                var account by remember { mutableStateOf(Account("", apps[0], "")) }
                var personal by remember { mutableStateOf(false) }
                var new by remember { mutableStateOf(false) }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "Cyan",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { settings = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = "Menu"
                                    )
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = {
                                account = Account("", apps[0], "")
                                personal = true
                                edit = true
                                new = true
                            },
                        ) {
                            Icon(Icons.Filled.Add, null)
                        }
                    },
                    modifier = Modifier.nestedScroll(pullRefreshState.nestedScrollConnection)
                ) { innerPadding ->
                    if (user == null) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(innerPadding)
                                .consumeWindowInsets(
                                    innerPadding
                                )
                                .verticalScroll(rememberScrollState())
                        ) {
                            Column {
                                Me(this@MainActivity, user!!) { a, p ->
                                    account = a
                                    personal = p
                                    edit = true
                                    new = false
                                }
                            }

                            PullToRefreshContainer(
                                modifier = Modifier.align(alignment = Alignment.TopCenter),
                                state = pullRefreshState,
                            )
                        }
                        if (edit) {
                            val previouslyPersonal by remember { mutableStateOf(personal) }
                            var username by remember { mutableStateOf(account.username) }
                            var app by remember { mutableStateOf(account.app) }
                            ModalBottomSheet(
                                onDismissRequest = {
                                    edit = false
                                    account.username = username
                                    account.app = app
                                    if (new)
                                        userService.addAccount(user!!, account, personal, {}, {})
                                    else userService.updateAccount(
                                        user!!,
                                        account,
                                        personal,
                                        previouslyPersonal,
                                        {},
                                        {})
                                },
                                sheetState = SheetState(true, LocalDensity.current),
                                windowInsets = BottomSheetDefaults.windowInsets
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        username,
                                        { username = it },
                                        prefix = if ((app == App.Phone) || (app == App.Email)) null else {
                                            { Text("@") }
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            KeyboardCapitalization.None,
                                            false,
                                            when (app) {
                                                App.Email -> KeyboardType.Email
                                                App.Phone -> KeyboardType.Phone
                                                else -> KeyboardType.Text
                                            },
                                            ImeAction.Done
                                        )
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.Center, modifier = Modifier
                                        .fillMaxWidth()
                                        .selectableGroup()
                                        .padding(vertical = 20.dp, horizontal = 30.dp)
                                ) {
                                    apps.forEach {
                                        val selected by remember { derivedStateOf { app == it } }
                                        IconButton(
                                            onClick = {
                                                app = it
                                            }, modifier = Modifier
                                                .size(60.dp)
                                                .padding(horizontal = 5.dp)
                                        ) {
                                            AppIcon(
                                                context = this@MainActivity,
                                                app = it,
                                                enabled = selected
                                            )
                                        }
                                    }
                                }

                                val interactionSource = remember { MutableInteractionSource() }
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = interactionSource,
                                            // This is for removing ripple when Row is clicked
                                            indication = null,
                                            role = Role.Switch,
                                            onClick = {
                                                personal = !personal
                                            }
                                        )
                                        .padding(horizontal = 20.dp)) {
                                    Text("Personal:", fontSize = 20.sp)
                                    Switch(
                                        personal,
                                        { personal = it },
                                        thumbContent = if (personal) {
                                            {
                                                Box {
                                                    Icon(
                                                        Icons.Rounded.Lock,
                                                        "Personal",
                                                        modifier = Modifier.padding(4.dp)
                                                    )
                                                }
                                            }
                                        } else null
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                ) {
                                    Button(onClick = {
                                        edit = false
                                        userService.removeAccount(
                                            user!!,
                                            account,
                                            previouslyPersonal,
                                            {},
                                            {
                                                Log.e("Log", it.toString())
                                            })
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Remove")
                                    }
                                }

                                Spacer(modifier = Modifier.padding(bottom = 20.dp))
                            }
                        }
                    }
                    if (settings) {
                        BasicAlertDialog(onDismissRequest = {
                            settings = false
                        }) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                tonalElevation = 1.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(all = 24.dp)
                                ) {
                                    if (currentUser == null) {
                                        Row {
                                            LoginButton(onIdToken = { idToken ->
                                                if (idToken != null) {
                                                    // Got an ID token from Google. Use it to authenticate
                                                    // with Firebase.
                                                    val firebaseCredential =
                                                        GoogleAuthProvider.getCredential(
                                                            idToken,
                                                            null
                                                        )
                                                    auth.signInWithCredential(firebaseCredential)
                                                        .addOnCompleteListener(this@MainActivity) { task ->
                                                            if (task.isSuccessful) {
                                                                Log.d(
                                                                    "LOG",
                                                                    "signInWithCredential:success"
                                                                )
                                                                currentUser = auth.currentUser
                                                            } else {
                                                                Log.w(
                                                                    "LOG",
                                                                    "signInWithCredential:failure",
                                                                    task.exception
                                                                )
                                                            }
                                                        }
                                                } else {
                                                    Log.d("LOG", "Null Token")
                                                }
                                            })
                                        }
                                    } else {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            Row {
                                                Button(onClick = {
                                                    statusBarManager.requestAddTileService(
                                                        ComponentName(
                                                            this@MainActivity,
                                                            MeTileService::class.java
                                                        ),
                                                        "Me",
                                                        android.graphics.drawable.Icon.createWithResource(
                                                            this@MainActivity,
                                                            R.drawable.ic_launcher_foreground
                                                        ),
                                                        {}
                                                    ) {}
                                                }) {
                                                    Text(text = "Add tile")
                                                }
                                            }
                                            Row {
                                                Button(onClick = {
                                                    auth.signOut()
                                                    currentUser = null
                                                }) {
                                                    Text(text = "Logout")
                                                }
                                            }
                                        }
                                    }
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { settings = false }) {
                                            Text(text = "Close")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoginButton(onIdToken: (String?) -> Unit) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK)
            return@rememberLauncherForActivityResult

        val oneTapClient = Identity.getSignInClient(context)
        val credential = oneTapClient.getSignInCredentialFromIntent(result.data)
        onIdToken(credential.googleIdToken)
    }

    val scope = rememberCoroutineScope()
    Button(
        onClick = {
            scope.launch {
                signIn(
                    context = context,
                    launcher = launcher
                )
            }
        }
    ) {
        Text("Sign in")
    }
}

suspend fun signIn(
    context: Context,
    launcher: ActivityResultLauncher<IntentSenderRequest>
) {
    val oneTapClient = Identity.getSignInClient(context)
    val signInRequest = BeginSignInRequest.builder()
        .setGoogleIdTokenRequestOptions(
            BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                .setSupported(true)
                // Your server's client ID, not your Android client ID.
                .setServerClientId(context.getString(R.string.CLIENT_ID))
                // Only show accounts previously used to sign in.
                .setFilterByAuthorizedAccounts(false)
                .build()
        )
        // Automatically sign in when exactly one credential is retrieved.
        .setAutoSelectEnabled(true)
        .build()

    try {
        // Use await() from https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-play-services
        // Instead of listeners that aren't cleaned up automatically
        val result = oneTapClient.beginSignIn(signInRequest).await()

        // Now construct the IntentSenderRequest the launcher requires
        val intentSenderRequest = IntentSenderRequest.Builder(result.pendingIntent).build()
        launcher.launch(intentSenderRequest)
    } catch (e: Exception) {
        // No saved credentials found. Launch the One Tap sign-up flow, or
        // do nothing and continue presenting the signed-out UI.
        Log.d("LOG", e.message.toString())
    }
}
