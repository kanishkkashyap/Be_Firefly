package com.firefly.befirefly.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firefly.befirefly.ui.theme.*
import com.firefly.befirefly.ui.components.BeFireflyAuroraBackground
import com.firefly.befirefly.ui.components.AuroraGradientText
import com.firefly.befirefly.ui.components.glassCard
import com.firefly.befirefly.ui.components.AuroraColors

// Constant header for P-256 EC Private Keys (PKCS#8 unencrypted)
// Using this to strip the static part so users see the random part immediately.
const val KEY_HEADER = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg"

enum class LoginStep {
    WELCOME,
    BACKUP,
    PERMISSIONS
}

@Composable
fun LoginScreen(
    onLoginSuccess: (String, String, String, Boolean) -> Unit, // privateKey, publicKey, username, isNewUser
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableStateOf(LoginStep.WELCOME) }
    var username by remember { mutableStateOf("") }

    // Temporary storage for generated phrase
    var generatedPhrase by remember { mutableStateOf<List<String>>(emptyList()) }
    var generatedPrivateKeyFull by remember { mutableStateOf("") } // Full PKCS#8
    var generatedPrivateKeyDisplay by remember { mutableStateOf("") } // Stripped
    var generatedPublicKey by remember { mutableStateOf("") }
    var isNewUser by remember { mutableStateOf(true) }

    BeFireflyAuroraBackground(isAnimated = true) {
        Box(modifier = modifier.fillMaxSize()) {
            when (currentStep) {
                LoginStep.WELCOME -> {
                    WelcomeContent(
                        username = username,
                        onUsernameChange = { username = it },
                        onJoinClick = { input, login ->
                            if (login) {
                                // Existing User Login
                                // User might paste Stripped or Full key.
                                // We need to support both.
                                var fullKey = input.trim()
                                if (!fullKey.startsWith("MIGH")) {
                                    // Assume it's a stripped key, add header back
                                    fullKey = KEY_HEADER + fullKey
                                }

                                generatedPrivateKeyFull = fullKey
                                // username is already set by the state variable (maybe empty if just logging in?)
                                // If logging in, username might be needed or derived?
                                // Current flow requires username input even for login.
                                isNewUser = false
                                currentStep = LoginStep.PERMISSIONS
                            } else {
                                // New User: Generate Real Key -> Backup -> Permissions
                                try {
                                    val keyPairGenerator = java.security.KeyPairGenerator.getInstance("EC")
                                    val ecSpec = java.security.spec.ECGenParameterSpec("secp256r1")
                                    val secureRandom = java.security.SecureRandom()
                                    keyPairGenerator.initialize(ecSpec, secureRandom)

                                    val keyPair = keyPairGenerator.generateKeyPair()
                                    val privateKeyBytes = keyPair.private.encoded
                                    val publicKeyBytes = keyPair.public.encoded
                                    val privateKeyString = android.util.Base64.encodeToString(privateKeyBytes, android.util.Base64.NO_WRAP)
                                    val publicKeyString = android.util.Base64.encodeToString(publicKeyBytes, android.util.Base64.NO_WRAP)

                                    android.util.Log.d("LoginScreen", "Generated New Identity: ${publicKeyString.take(10)}...")

                                    // Strip Header for Display
                                    val displayKey = if (privateKeyString.startsWith(KEY_HEADER)) {
                                        privateKeyString.substring(KEY_HEADER.length)
                                    } else {
                                        privateKeyString
                                    }

                                    val chunks = displayKey.chunked(4)
                                    generatedPhrase = chunks
                                    generatedPrivateKeyDisplay = displayKey
                                    generatedPrivateKeyFull = privateKeyString
                                    generatedPublicKey = publicKeyString
                                    isNewUser = true
                                    currentStep = LoginStep.BACKUP
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    )
                }
                LoginStep.BACKUP -> {
                    BackupContent(
                        phrase = generatedPhrase,
                        rawKey = generatedPrivateKeyDisplay,
                        onComplete = {
                            currentStep = LoginStep.PERMISSIONS
                        }
                    )
                }
                LoginStep.PERMISSIONS -> {
                    PermissionsContent(
                        onPermissionsGranted = {
                            onLoginSuccess(generatedPrivateKeyFull, generatedPublicKey, username, isNewUser)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WelcomeContent(
    username: String,
    onUsernameChange: (String) -> Unit,
    onJoinClick: (String, Boolean) -> Unit // input, isLogin
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Welcome Chip
        Surface(
            color = NavySurface,
            shape = RoundedCornerShape(50),
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Text(
                text = "Welcome",
                color = TextGray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 14.sp
            )
        }

        // Header
        Surface(
            color = NavySurface,
            shape = RoundedCornerShape(50),
            modifier = Modifier.padding(bottom = 48.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("👋 Let's Get started", color = TextWhite)
            }
        }

        // Inputs
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            placeholder = { Text("Set Your Username", color = TextGray) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .background(InputBackground, RoundedCornerShape(12.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        var loginKey by remember { mutableStateOf("") }

        OutlinedTextField(
            value = loginKey,
            onValueChange = { loginKey = it },
            placeholder = { Text("Enter Key to Login", color = TextGray) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .background(InputBackground, RoundedCornerShape(12.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            enabled = true
        )

        // Join / Login Button
        val isLogin = loginKey.isNotBlank()

        Button(
            onClick = {
                if (username.isBlank()) {
                    android.widget.Toast.makeText(context, "Please enter a username", android.widget.Toast.LENGTH_SHORT).show()
                    return@Button
                }

                if (isLogin) {
                    onJoinClick(loginKey, true)
                } else {
                    onJoinClick(username, false)
                }
            },
            enabled = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF1A1A2E), Color(0xFF1A1A2E))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(1.dp, NavySurface, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isLogin) "Login with Key" else "Create Identity",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF00C6FF), Color(0xFF0072FF))
                        )
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupContent(
    phrase: List<String>,
    rawKey: String,
    onComplete: () -> Unit
) {
    var isChecked by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = "Security",
            tint = Color(0xFFFFD700),
            modifier = Modifier.size(64.dp).padding(bottom = 24.dp)
        )

        Text(
            "Back Up Your Identity",
            color = TextWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            "This is your 'Recovery Key'. It is unique to you. Save it to log in on other devices.",
            color = TextGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Phrase Grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFFFD700), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                phrase.forEachIndexed { index, word ->
                    Text(
                        text = word,
                        color = TextWhite,
                        modifier = Modifier
                            .padding(4.dp)
                            .background(NavySurface, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Copy Button
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(rawKey))
                    android.widget.Toast.makeText(context, "Key copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NavySurface)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Key", color = TextWhite)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Checkbox
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(NavySurface, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = { isChecked = it },
                colors = CheckboxDefaults.colors(checkedColor = PrimaryPurple)
            )
            Text(
                "I have securely stored my recovery key.",
                color = TextGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onComplete,
            enabled = isChecked,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NavySurface,
                disabledContainerColor = NavySurface.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Next Step", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PermissionsContent(
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current

    val permissionsToRequest = remember {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            list.add(Manifest.permission.READ_MEDIA_IMAGES)
            list.add(Manifest.permission.READ_MEDIA_VIDEO)
            list.add(Manifest.permission.READ_MEDIA_AUDIO)
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        list.toTypedArray()
    }

    // Lenient check: only location is truly required to proceed
    fun hasMinimumPermissions(): Boolean {
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    // Track whether the launcher has been fired
    var launcherFired by remember { mutableStateOf(false) }

    // Auto-advance if permissions already granted (e.g., AppContent already asked)
    LaunchedEffect(Unit) {
        if (hasMinimumPermissions()) {
            android.util.Log.d("LoginScreen", "Permissions already granted. Auto-advancing.")
            onPermissionsGranted()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Always proceed — cloud-only mode works even without location
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            android.util.Log.d("LoginScreen", "Location permissions granted via launcher.")
        } else {
            android.util.Log.w("LoginScreen", "Location denied, proceeding in cloud-only mode.")
        }
        onPermissionsGranted()
    }

    // Fallback: if launcher was fired but dialog didn't appear (permissions already asked),
    // re-check after a short delay and proceed if we have minimum permissions
    LaunchedEffect(launcherFired) {
        if (launcherFired) {
            kotlinx.coroutines.delay(1500)
            // If we're still on this screen after 1.5s, check if permissions are already granted
            if (hasMinimumPermissions()) {
                android.util.Log.d("LoginScreen", "Fallback: permissions already granted, proceeding.")
                onPermissionsGranted()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Permissions",
            tint = PrimaryBlue,
            modifier = Modifier.size(64.dp).padding(bottom = 24.dp)
        )

        Text(
            "Grant Permissions",
            color = TextWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            "To connect with people nearby and share media, Be Firefly needs access to:",
            color = TextGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavySurface, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            PermissionItem("Location & Bluetooth", "To find nearby devices")
            PermissionItem("Camera & Microphone", "To send photos and voice notes")
            PermissionItem("Storage & Media", "To share photos, videos and files")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                launcherFired = true
                launcher.launch(permissionsToRequest)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Grant All Permissions", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Skip button as fallback — always allow users to proceed
        TextButton(
            onClick = {
                android.util.Log.d("LoginScreen", "User skipped permissions.")
                onPermissionsGranted()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Skip for now",
                color = TextGray,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun PermissionItem(title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF00C6FF),
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(description, color = TextGray, fontSize = 12.sp)
        }
    }
}