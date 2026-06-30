package com.firefly.befirefly

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.firefly.befirefly.data.service.MeshService
import com.firefly.befirefly.ui.theme.BeFireflyTheme
import com.firefly.befirefly.ui.screens.LoginScreen
import com.firefly.befirefly.ui.screens.ChatScreen
import com.firefly.befirefly.viewmodel.UserViewModel

class MainActivity : FragmentActivity() {

    private val userViewModel: UserViewModel by viewModels()
    private var meshService: MeshService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MeshService.MeshBinder
            meshService = binder.getService()
            isBound = true
            userViewModel.setService(meshService!!)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            meshService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Edge-to-edge: works on ALL Android phones (API 26+) ──
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            )
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Transparent system bars across all API levels
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // Remove navigation bar scrim on API 29+ (prevents dark tint)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        // Allow layout behind cutouts/notches on API 28+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Light status bar icons = false (white icons on dark background)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        window.setBackgroundDrawableResource(android.R.color.black)

        // Start the service explicitly so it keeps running even if unbound
        // Start the service explicitly only after permissions (handled in Composable)
        // val intent = Intent(this, MeshService::class.java)
        // startService(intent)

        setContent {
            BeFireflyTheme {
                AppContent(userViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        userViewModel.isAppForeground = true
        // Only bind if we have permissions, otherwise wait for the permission flow to start it
        if (hasPermissions()) {
            try {
                Intent(this, MeshService::class.java).also { intent ->
                    bindService(intent, connection, Context.BIND_AUTO_CREATE)
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to bind MeshService", e)
            }
        }
    }

    private fun hasPermissions(): Boolean {
        // Check for key permissions — either FINE or COARSE location is sufficient
        val fine = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    override fun onStop() {
        super.onStop()
        userViewModel.isAppForeground = false
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    fun bindMeshService() {
        Intent(this, MeshService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }
}

@Composable
fun AppContent(userViewModel: UserViewModel) {
    val context = LocalContext.current

    // Android 13+ requires runtime permission to post notifications. New users grant it during
    // the LoginScreen flow; returning users are prompted here if it's still missing.
    val notifPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Permission check for returning users — new users will go through LoginScreen's permission flow
    val permissionsToCheck = remember {
        mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    // Check if we already have permissions (returning user) and start service if so
    LaunchedEffect(Unit) {
        val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            android.util.Log.d("Permissions", "Returning user: permissions already granted. Starting service...")
            val intent = android.content.Intent(context, MeshService::class.java)
            context.startService(intent)
            (context as? MainActivity)?.bindMeshService()
            userViewModel.startOfflineMode()
        } else {
            android.util.Log.d("Permissions", "New user or permissions not yet granted. Will go through LoginScreen flow.")
        }

        // Also load credentials
        userViewModel.loadSavedCredentials()
    }

    val appLockManager = remember { com.firefly.befirefly.utils.AppLockManager(context) }
    var isUnlocked by remember { androidx.compose.runtime.mutableStateOf(!appLockManager.isLockEnabled()) }

    // ── In-app update check (self-hosted, no store) ──
    val updateScope = rememberCoroutineScope()
    var updateInfo by remember { androidx.compose.runtime.mutableStateOf<com.firefly.befirefly.utils.UpdateChecker.UpdateInfo?>(null) }
    var updateDownloading by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) {
        updateInfo = com.firefly.befirefly.utils.UpdateChecker.check(context)
    }
    updateInfo?.let { info ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!updateDownloading) updateInfo = null },
            title = { androidx.compose.material3.Text("Update available — v${info.versionName}") },
            text = {
                androidx.compose.material3.Text(
                    if (updateDownloading) "Downloading update…"
                    else info.changelog.ifBlank { "A new version of Be Firefly is available." }
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = !updateDownloading,
                    onClick = {
                        updateDownloading = true
                        updateScope.launch {
                            val apk = com.firefly.befirefly.utils.UpdateChecker.downloadApk(context, info.apkUrl)
                            updateDownloading = false
                            if (apk != null) {
                                com.firefly.befirefly.utils.UpdateChecker.installApk(context, apk)
                                updateInfo = null
                            }
                        }
                    }
                ) { androidx.compose.material3.Text(if (updateDownloading) "Downloading…" else "Update now") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    enabled = !updateDownloading,
                    onClick = { updateInfo = null }
                ) { androidx.compose.material3.Text("Later") }
            }
        )
    }

    // Main app navigation
    if (userViewModel.wallet != null && userViewModel.username.isNotEmpty()) {
        if (!isUnlocked) {
            // Show Lock Screen (PIN, with optional hardware biometric unlock)
            val activity = context as? androidx.fragment.app.FragmentActivity
            val biometricAuth = remember(activity) { activity?.let { com.firefly.befirefly.utils.BiometricAuthenticator(it) } }
            val biometricOn = remember { appLockManager.isBiometricEnabled() && (biometricAuth?.isAvailable() == true) }
            com.firefly.befirefly.ui.screens.LockScreen(
                onUnlocked = { isUnlocked = true },
                onVerifyPin = { pin -> appLockManager.verifyPin(pin) },
                biometricEnabled = biometricOn,
                onBiometricRequested = {
                    biometricAuth?.authenticate(
                        title = "Unlock Be Firefly",
                        subtitle = "Confirm it's you",
                        onSuccess = { isUnlocked = true },
                        onError = { /* fall back to PIN entry */ }
                    )
                }
            )
        } else if (userViewModel.nearbyManager != null) {
            // Collect chats
            val chats by userViewModel.chatPreviews.collectAsState(initial = emptyList())
            val contacts by userViewModel.allContacts.collectAsState(initial = emptyList())
            val discovered by userViewModel.discoveredUsers.collectAsState(initial = emptyList())
            val messages = userViewModel.messages

            val isAdvertising by userViewModel.isAdvertising.collectAsState(initial = false)
            val isDiscovering by userViewModel.isDiscovering.collectAsState(initial = false)
            val peerCount by userViewModel.connectedPeersCount.collectAsState(initial = 0)
            val isCloudConnected by userViewModel.isCloudConnected.collectAsState(initial = false)
            val lastError by userViewModel.lastNetworkError.collectAsState(initial = null)

            com.firefly.befirefly.ui.screens.MainScreen(
                wallet = userViewModel.wallet!!,
                username = userViewModel.username,
                profilePictureUri = userViewModel.profilePictureUri,
                messages = messages,
                chats = chats,
                contacts = contacts,
                discoveredUsers = discovered,
                isWifiConnected = userViewModel.isWifiConnected,
                onSendMessage = { text -> userViewModel.sendMessage(text) },
                onSendImage = { uri -> userViewModel.sendImage(uri) },
                onSendFile = { uri -> userViewModel.sendFile(uri) },
                onSendAudio = { file -> userViewModel.sendAudio(file) },
                onContactSelected = { id -> userViewModel.selectContact(id) },
                onAddContact = { name, key -> userViewModel.addContact(name, key) },
                onEditContact = { id, newName -> userViewModel.updateContactName(id, newName) },
                onDeleteContact = { id -> userViewModel.deleteContact(id) },
                onCreateGroup = { name, members -> userViewModel.createGroup(name, members) },
                onUpdateProfilePicture = { uri -> userViewModel.updateProfilePicture(uri) },
                onLogout = { userViewModel.logout() },
                onResetIdentity = { userViewModel.resetIdentity() },
                onTestCloud = { userViewModel.testCloudRelay() },
                onDeleteMessage = { id -> userViewModel.deleteMessage(id) },
                onClearChat = { userViewModel.clearChat() },
                onExportChat = { callback -> userViewModel.exportChat(callback) },
                onPinChat = { id -> userViewModel.togglePinChat(id) },
                onWallpaperGradient = { convId, index -> userViewModel.setWallpaperGradient(convId, index) },
                onWallpaperImage = { convId, uri -> userViewModel.setWallpaperImage(convId, uri) },
                onWallpaperReset = { convId -> userViewModel.resetWallpaper(convId) },
                wallpaperGradientIndex = userViewModel.currentWallpaperGradient,
                wallpaperImageUri = userViewModel.currentWallpaperImage,
                isAdvertising = isAdvertising,
                isDiscovering = isDiscovering,
                peerCount = peerCount,
                isCloudConnected = isCloudConnected,
                lastError = lastError,
                replyingTo = userViewModel.replyingTo,
                typingFrom = userViewModel.typingFrom,
                searchResults = userViewModel.searchResults,
                onReact = { msg, emoji -> userViewModel.reactToMessage(msg, emoji) },
                onReply = { msg -> userViewModel.setReplyTo(msg) },
                onCancelReply = { userViewModel.setReplyTo(null) },
                onForward = { msg, targetId -> userViewModel.forwardMessage(msg, targetId) },
                onEditMessage = { msg, newText -> userViewModel.editMessage(msg, newText) },
                onDeleteForEveryone = { msg -> userViewModel.deleteForEveryone(msg) },
                onTyping = { userViewModel.notifyTyping() },
                onSearch = { q -> userViewModel.search(q) },
                onClearSearch = { userViewModel.clearSearch() },
                disappearingSeconds = userViewModel.currentDisappearingSeconds,
                onSetDisappearingTimer = { convId, secs -> userViewModel.setDisappearingTimer(convId, secs) },
                isContactVerified = userViewModel.currentContactVerified,
                safetyNumber = userViewModel.currentSafetyNumber,
                onVerifyContact = { id, v -> userViewModel.verifyContact(id, v) },
                isChatMuted = userViewModel.currentChatMuted,
                onToggleMute = { id -> userViewModel.toggleMute(id) },
                initialDraft = userViewModel.currentDraft,
                onDraftChanged = { text -> userViewModel.saveDraft(text) },
                onStarMessage = { msg -> userViewModel.toggleStar(msg) },
                onPinMessage = { msg -> userViewModel.togglePinMessage(msg) }
            )

            // NOTE: I passed createIdentity for onAddContact which is WRONG.
            // onAddContact needs to add to DB.
            // UserViewModel needs onAddContact method.
        } else {
            // Show Loading or "Starting Service..."
            androidx.compose.material3.Text("Starting Mesh Service...")
        }
    } else {
        LoginScreen(
            onLoginSuccess = { privateKey, publicKey, username, isNewUser ->
                // Start the MeshService now that permissions have been granted (LoginScreen handles this)
                try {
                    val intent = android.content.Intent(context, MeshService::class.java)
                    context.startService(intent)
                    (context as? MainActivity)?.bindMeshService()
                    android.util.Log.d("AppContent", "MeshService started after login flow.")
                } catch (e: Exception) {
                    android.util.Log.e("AppContent", "Failed to start MeshService after login", e)
                }
                userViewModel.createIdentity(username, privateKey, publicKey)
            },
            modifier = Modifier
        )
    }
}