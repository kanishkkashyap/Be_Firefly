package com.firefly.befirefly.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.firefly.befirefly.ui.components.*

data class SettingItem(
    val emoji: String,
    val title: String,
    val subtitle: String? = null,
    val colorTint: String = "b", // b=blue, p=pink, g=gold, r=red, gr=green
    val onClick: () -> Unit
)

@Composable
fun SettingsTab(
    username: String,
    publicKey: String,
    profilePictureUri: String?,
    onUpdateProfilePicture: (String) -> Unit,
    onLogout: () -> Unit,
    onResetIdentity: () -> Unit,
    onTestCloud: () -> Unit,
    isAdvertising: Boolean,
    isDiscovering: Boolean,
    peerCount: Int,
    isCloudConnected: Boolean,
    lastError: String?,
    isDarkTheme: Boolean = true,
    onThemeToggle: () -> Unit = {}
) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? -> uri?.let { onUpdateProfilePicture(it.toString()) } }

    val context = androidx.compose.ui.platform.LocalContext.current
    val appLockManager = remember { com.firefly.befirefly.utils.AppLockManager(context) }
    var isLockEnabled by remember { mutableStateOf(appLockManager.isLockEnabled()) }
    var isBiometricEnabled by remember { mutableStateOf(appLockManager.isBiometricEnabled()) }
    var showPinSetup by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDeveloperDialog by remember { mutableStateOf(false) }

    val settingsItems = listOf(
        SettingItem("🔒", "App Lock",
            if (isLockEnabled) "Enabled (Tap to disable)" else "Disabled (Tap to setup PIN)", "b") {
            if (isLockEnabled) { appLockManager.clearLock(); isLockEnabled = false; isBiometricEnabled = false } else showPinSetup = true
        },
        SettingItem("👆", "Biometric Unlock",
            if (isBiometricEnabled) "Enabled — fingerprint / face" else "Use biometrics to unlock (needs App Lock)", "b") {
            val newVal = !isBiometricEnabled
            appLockManager.setBiometricEnabled(newVal)
            isBiometricEnabled = newVal
        },
        SettingItem("ℹ️", "About BeFirefly", "Version 2.0 (Aurora Edition)", "b") { showAboutDialog = true },
        SettingItem("👨‍💻", "Developer", "Kanishk Kashyap (HS Walker)", "p") { showDeveloperDialog = true },
        SettingItem("🔐", "Privacy and Security", "Passcode, Two-Step Verification", "p") {},
        SettingItem("🔔", "Notifications", "Sounds, Calls, Badges", "p") {},
        SettingItem("📁", "Chat Folders", "Sort chats into folders", "b") {},
        SettingItem("💻", "Devices", "Manage connected devices", "b") {},
        SettingItem("🔋", "Background Activity", "Allow mesh to run in background", "g") {
            try {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e: Exception) {
                try { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
            }
        },
        SettingItem("🌐", "Language", "English", "gr") {},
        SettingItem("🚪", "Logout", "Sign out of Be Firefly", "r") { onLogout() },
        SettingItem("💣", "Nuke Identity", "Reset Keys & Data", "r") { onResetIdentity() },
        SettingItem("🐛", "Network Debug", "Status Check", "b") {}
    )

    if (showPinSetup) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showPinSetup = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            LockScreen(
                onUnlocked = { isLockEnabled = true; showPinSetup = false },
                onVerifyPin = { false },
                isSettingPin = true,
                onPinSet = { pin -> appLockManager.setPin(pin) }
            )
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = Color(0xFF0A0E27),
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedFireflyLogo(modifier = Modifier.size(60.dp))
                    Spacer(Modifier.width(8.dp))
                    AuroraGradientText("BeFirefly", fontSize = 22.sp)
                }
            },
            text = {
                Text(
                    "BeFirefly is an intelligent, offline-first mesh messaging platform built for secure, unblockable communication.\n\nVersion 2.0.0 — Aurora Edition\n\n© 2026 BeFirefly Team.",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Close", color = AuroraColors.Teal) }
            }
        )
    }

    if (showDeveloperDialog) {
        AlertDialog(
            onDismissRequest = { showDeveloperDialog = false },
            containerColor = Color(0xFF0A0E27),
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👨‍💻", fontSize = 28.sp)
                    Spacer(Modifier.width(10.dp))
                    AuroraGradientText("Developer", fontSize = 22.sp)
                }
            },
            text = {
                Column {
                    Text("Kanishk Kashyap", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("aka HS Walker", color = AuroraColors.Teal.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Creator & developer of Be Firefly — an offline-first, end-to-end encrypted mesh messenger that keeps people connected when the internet can't.",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Designed, built, and secured with care. ✨", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeveloperDialog = false }) { Text("Close", color = AuroraColors.Teal) }
            }
        )
    }

    // Main layout
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp)
            ) {
                Text(
                    text = "Settings",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AuroraColors.Teal,
                    letterSpacing = (-0.5).sp
                )
            }
        }

        // Profile card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar with aurora glow
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(110.dp).background(AuroraColors.SoftPink.copy(alpha = 0.15f), CircleShape).blur(20.dp))
                        if (profilePictureUri != null) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    model = coil.request.ImageRequest.Builder(context).data(profilePictureUri).crossfade(true).build()
                                ),
                                contentDescription = "Profile",
                                modifier = Modifier.size(100.dp).clip(CircleShape).clickable { launcher.launch("image/*") },
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(100.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(AuroraColors.SoftPink, AuroraColors.Lavender)), CircleShape)
                                    .clickable { launcher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) { Text(username.take(1).uppercase(), fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Box(
                        modifier = Modifier.size(34.dp).offset(x = 4.dp, y = 4.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(AuroraColors.Teal, AuroraColors.Cyan)), CircleShape)
                            .clickable { launcher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.CameraAlt, "Change Photo", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }

                Spacer(Modifier.height(16.dp))
                AuroraGradientText(text = username, fontSize = 26.sp)

                val uniqueId = if (publicKey.length > 36) publicKey.substring(36).take(8) else publicKey.take(8)
                Text("@$uniqueId...", fontSize = 14.sp, color = Color.White.copy(alpha = 0.3f), letterSpacing = 1.sp)
                Spacer(Modifier.height(20.dp))
            }
        }

        // Settings items with emoji icons
        items(settingsItems) { item ->
            AuroraSettingRow(item)
        }

        // Network status card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    "NETWORK STATUS",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))

                val statusColor = @Composable { on: Boolean -> if (on) Color(0xFF00FF9F) else Color.White.copy(alpha = 0.4f) }

                Text("Advertising: ${if (isAdvertising) "ON" else "OFF"}", color = statusColor(isAdvertising), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(3.dp))
                Text("Discovery: ${if (isDiscovering) "ON" else "OFF"}", color = statusColor(isDiscovering), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(3.dp))
                Text("Cloud Gateway: ${if (isCloudConnected) "CONNECTED" else "DISCONNECTED"}", color = statusColor(isCloudConnected), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(3.dp))
                Text("Connected Peers: $peerCount", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)

                if (lastError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Error: $lastError", color = Color(0xFFFF5252), fontSize = 12.sp)
                }
            }
        }

        // Test Cloud Relay button
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(AuroraColors.Teal.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                    .border(1.dp, AuroraColors.Teal.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onTestCloud)
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("🧪 Test Cloud Relay", color = AuroraColors.Teal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
fun AuroraSettingRow(item: SettingItem) {
    val bgColor = when (item.colorTint) {
        "b" -> AuroraColors.Teal.copy(alpha = 0.1f)
        "p" -> Color(0xFFFF79C6).copy(alpha = 0.1f)
        "g" -> Color(0xFFFFD700).copy(alpha = 0.1f)
        "r" -> Color(0xFFFF6B35).copy(alpha = 0.1f)
        "gr" -> Color(0xFF00FF9F).copy(alpha = 0.1f)
        else -> AuroraColors.Teal.copy(alpha = 0.1f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
            .clickable { item.onClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Emoji icon box
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(bgColor, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(item.emoji, fontSize = 18.sp)
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            if (item.subtitle != null) {
                Text(item.subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.3f), modifier = Modifier.padding(top = 2.dp))
            }
        }

        Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.15f), modifier = Modifier.size(18.dp))
    }
}
