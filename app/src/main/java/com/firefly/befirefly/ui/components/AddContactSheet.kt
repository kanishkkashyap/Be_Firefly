package com.firefly.befirefly.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.firefly.befirefly.domain.model.ChatPreview

@Composable
fun AddContactSheet(
    myPublicKey: String,
    myUsername: String = "?",
    suggestedContacts: List<ChatPreview> = emptyList(),
    onDismiss: () -> Unit,
    onAddContact: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var isShowingMyCode by remember { mutableStateOf(false) }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    // Entry animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val cardScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.9f,
        animationSpec = tween(durationMillis = 600, easing = AuroraEasings.Bounce),
        label = "card_scale"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = AuroraEasings.Smooth), label = "card_alpha"
    )

    if (showScanner) {
        Dialog(
            onDismissRequest = { showScanner = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                QrCodeScanner(
                    onQrCodeScanned = { scannedKey ->
                        publicKey = scannedKey
                        if (name.isBlank()) name = "Contact-${scannedKey.takeLast(6)}"
                        showScanner = false
                    },
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { showScanner = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).statusBarsPadding()
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.85f)
                        .graphicsLayer { scaleX = cardScale; scaleY = cardScale; alpha = cardAlpha }
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                ) {
                    // Modal Background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0C0A14).copy(alpha = 0.98f), Color(0xFF080C1E).copy(alpha = 0.97f))
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    )

                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text("Add Contact", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("secp256r1 · End-to-End Encrypted", color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                                    .clickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✕", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Active User Card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.linearGradient(listOf(Color(0xFF16192B), Color(0xFF101524))), RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PulsingStatus(ConnectionType.ONLINE, size = 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(myUsername, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("ID: ...${myPublicKey.takeLast(10)}", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // Segmented Control
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF10121C), RoundedCornerShape(12.dp))
                                .padding(4.dp)
                        ) {
                            // New Contact Tab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (!isShowingMyCode) Brush.linearGradient(listOf(Color(0xFF2A314A), Color(0xFF1E243A))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)), RoundedCornerShape(8.dp))
                                    .border(1.dp, if (!isShowingMyCode) Color.White.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { isShowingMyCode = false }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("New Contact", color = if (!isShowingMyCode) Color.White else Color.White.copy(alpha = 0.4f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // My Code Tab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isShowingMyCode) Brush.linearGradient(listOf(Color(0xFF2A314A), Color(0xFF1E243A))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)), RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isShowingMyCode) Color.White.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { isShowingMyCode = true }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("My Code", color = if (isShowingMyCode) Color.White else Color.White.copy(alpha = 0.4f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        if (isShowingMyCode) {
                            // ── MY CODE TAB ──
                            Column(modifier = Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.White, RoundedCornerShape(20.dp))
                                        .padding(16.dp)
                                ) {
                                    QrCodeGenerator(content = myPublicKey, size = 220.dp)
                                }
                                Spacer(Modifier.height(16.dp))
                                Text("Others scan this to add you", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)

                                Spacer(Modifier.weight(1f))

                                // Fingerprint Box
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF10121C), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                        .padding(16.dp)
                                ) {
                                    Text("FINGERPRINT", color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp, letterSpacing = 1.sp)
                                    val fingerprint = try {
                                        val md = java.security.MessageDigest.getInstance("SHA-256")
                                        val hash = md.digest(myPublicKey.toByteArray())
                                        hash.take(8).joinToString(":") { String.format("%02X", it) }
                                    } catch (e: Exception) { myPublicKey.takeLast(16) }
                                    Text(fingerprint, color = AuroraColors.Teal, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                                    Text("Key: ...${myPublicKey.takeLast(16)}", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
                                }

                                Spacer(Modifier.height(20.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .background(Brush.linearGradient(listOf(Color(0xFF2A233A), Color(0xFF1A152A))), RoundedCornerShape(12.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(myPublicKey))
                                                android.widget.Toast.makeText(context, "Key copied!", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("📋 Copy Key", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .background(Brush.linearGradient(listOf(Color(0xFF13364A), Color(0xFF0D253A))), RoundedCornerShape(12.dp))
                                            .border(1.dp, AuroraColors.Teal.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(android.content.Intent.EXTRA_TEXT, "Add me on BeFirefly!\nMy key: $myPublicKey")
                                                }
                                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Key"))
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("📤 Share", color = AuroraColors.Teal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        } else {
                            // ── NEW CONTACT TAB ──
                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                // Nearby Suggestions
                                item {
                                    Text("NEARBY SUGGESTIONS", color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    
                                    if (suggestedContacts.isEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.White.copy(alpha = 0.4f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Scanning for nearby devices...",
                                                color = Color.White.copy(alpha = 0.4f),
                                                fontSize = 14.sp,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                            )
                                        }
                                    } else {
                                        val avatarColors = listOf(Color(0xFF00E5FF), Color(0xFFFF79C6), Color(0xFFFFD700), Color(0xFFB388FF))
                                        suggestedContacts.forEachIndexed { index, contact ->
                                            val color = avatarColors[index % avatarColors.size]
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 10.dp)
                                                    .background(Brush.linearGradient(listOf(Color(0xFF131A2A), Color(0xFF0D1220))), RoundedCornerShape(12.dp))
                                                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(modifier = Modifier.size(40.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                                                    Text(contact.name.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(contact.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                                        Text("📡", fontSize = 10.sp)
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Mesh · ${contact.id.takeLast(10)}", color = AuroraColors.Teal, fontSize = 12.sp)
                                                    }
                                                }
                                                Icon(Icons.Default.Add, contentDescription = "Add", tint = AuroraColors.Teal, modifier = Modifier.clickable {
                                                    onAddContact(contact.name, contact.id)
                                                    onDismiss()
                                                })
                                            }
                                        }
                                    }
                                }

                                item {
                                    Spacer(Modifier.height(16.dp))
                                    Divider(color = Color.White.copy(alpha = 0.05f))
                                    Spacer(Modifier.height(20.dp))
                                    Text("MANUALLY ADD", color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
                                    
                                    Text("NAME", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 6.dp))
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        placeholder = { Text("e.g. Rahul", color = Color.White.copy(alpha = 0.2f)) },
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFF10121C), unfocusedContainerColor = Color(0xFF10121C),
                                            focusedBorderColor = AuroraColors.Teal.copy(alpha = 0.3f), unfocusedBorderColor = Color.White.copy(alpha = 0.05f),
                                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp), singleLine = true
                                    )

                                    Text("PUBLIC KEY", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 6.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = publicKey,
                                            onValueChange = { publicKey = it },
                                            placeholder = { Text("Paste secp256r1 key...", color = Color.White.copy(alpha = 0.2f)) },
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = Color(0xFF10121C), unfocusedContainerColor = Color(0xFF10121C),
                                                focusedBorderColor = AuroraColors.Teal.copy(alpha = 0.3f), unfocusedBorderColor = Color.White.copy(alpha = 0.05f),
                                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(12.dp), singleLine = true
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier.size(48.dp).background(Color(0xFF2A233A), RoundedCornerShape(12.dp)).clickable {
                                                val clip = clipboardManager.getText()?.text ?: ""
                                                if (clip.isNotBlank()) publicKey = clip.trim()
                                            },
                                            contentAlignment = Alignment.Center
                                        ) { Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier.size(48.dp).background(Color(0xFF13364A), RoundedCornerShape(12.dp)).border(1.dp, AuroraColors.Teal.copy(alpha = 0.2f), RoundedCornerShape(12.dp)).clickable { showScanner = true },
                                            contentAlignment = Alignment.Center
                                        ) { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", tint = AuroraColors.Teal, modifier = Modifier.size(20.dp)) }
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            // Bottom Buttons
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f).height(48.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                        .clickable { onDismiss() },
                                    contentAlignment = Alignment.Center
                                ) { Text("Cancel", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }

                                val canAdd = name.isNotBlank() && publicKey.isNotBlank()
                                Box(
                                    modifier = Modifier
                                        .weight(1f).height(48.dp)
                                        .background(if (canAdd) Brush.linearGradient(listOf(AuroraColors.Teal, Color(0xFF00BFA5))) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))), RoundedCornerShape(12.dp))
                                        .clickable(enabled = canAdd) {
                                            onAddContact(name, publicKey.trim())
                                            onDismiss()
                                        },
                                    contentAlignment = Alignment.Center
                                ) { Text("Add 🔥", color = if (canAdd) Color.Black else Color.White.copy(alpha = 0.3f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }
            }
        }
    }
}
