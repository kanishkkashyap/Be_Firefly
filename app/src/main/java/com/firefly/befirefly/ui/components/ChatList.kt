package com.firefly.befirefly.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.firefly.befirefly.domain.model.ChatPreview
import kotlinx.coroutines.delay

// Gradient accent colors for avatars
private val avatarColors = listOf(
    Color(0xFF7C4DFF), Color(0xFF2842A6), Color(0xFF70397E),
    Color(0xFF00E5FF), Color(0xFF76FF03), Color(0xFFFF5252),
    Color(0xFFFFD740), Color(0xFF448AFF)
)

@Composable
fun ChatList(
    chats: List<ChatPreview>,
    profilePictureUri: String?,
    isWifiConnected: Boolean,
    onChatClick: (String, Boolean) -> Unit,
    onProfileClick: () -> Unit,
    onAddContactClick: () -> Unit,
    onCreateGroupClick: () -> Unit,
    onEditContact: ((String, String) -> Unit)? = null,
    onDeleteContact: ((String) -> Unit)? = null,
    onPinChat: ((String) -> Unit)? = null,
    networkMode: NetworkMode = NetworkMode.OFFLINE,
    onQrScan: () -> Unit = {},
    isAuroraAnimated: Boolean = true,
    onThemeToggle: () -> Unit = {}
) {
    // ── Dialog state ──────────────────────────────────────────
    var editingContact by remember { mutableStateOf<ChatPreview?>(null) }
    var editName by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // ── Edit dialog ───────────────────────────────────────────
    if (editingContact != null) {
        AlertDialog(
            onDismissRequest = { editingContact = null },
            containerColor = Color(0xFF1A1F30),
            shape = RoundedCornerShape(20.dp),
            title = { Text("Edit Contact", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Name", color = Color.White.copy(alpha = 0.5f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AuroraColors.Teal,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                    ),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onEditContact?.invoke(editingContact!!.id, editName)
                    editingContact = null
                }) { Text("Save", color = AuroraColors.Teal) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("Delete", color = Color(0xFFFF5252))
                    }
                    TextButton(onClick = { editingContact = null }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        )
    }

    // ── Delete confirm dialog ─────────────────────────────────
    if (showDeleteConfirm && editingContact != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xFF1A1F30),
            shape = RoundedCornerShape(20.dp),
            title = { Text("Delete Contact?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will remove \"${editingContact!!.name}\" permanently.",
                    color = Color.White.copy(alpha = 0.7f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteContact?.invoke(editingContact!!.id)
                    showDeleteConfirm = false
                    editingContact = null
                }) { Text("Delete", color = Color(0xFFFF5252)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
    }

    // ── Main list ─────────────────────────────────────────────
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        AuroraGradientText(text = "Be Firefly", fontSize = 34.sp)
                        Text(
                            text = "V2.0 BETA",
                            color = Color.White.copy(alpha = 0.30f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 2.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Theme toggle (sun/moon)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(AuroraColors.WarmGolden.copy(alpha = 0.12f), CircleShape)
                                .border(1.5.dp, AuroraColors.WarmGolden.copy(alpha = 0.25f), CircleShape)
                                .clickable(onClick = onThemeToggle),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isAuroraAnimated) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme",
                                tint = if (isAuroraAnimated) AuroraColors.WarmGolden else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Profile
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        listOf(AuroraColors.SoftPink, AuroraColors.Lavender)
                                    ),
                                    shape = CircleShape
                                )
                                .clickable(onClick = onProfileClick),
                            contentAlignment = Alignment.Center
                        ) {
                            if (profilePictureUri != null) {
                                AsyncImage(
                                    model = profilePictureUri,
                                    contentDescription = "Profile",
                                    modifier = Modifier.size(46.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Profile",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetworkDot(networkMode = networkMode)
                }
            }
        }

        // Search bar
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .height(52.dp)
                    .glassCard(cornerRadius = 16.dp, alpha = 0.05f)
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Search chats...",
                        color = Color.White.copy(alpha = 0.25f),
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Action buttons
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BeFireflyActionButton(
                    label = "Add Contact",
                    icon = Icons.Default.PersonAdd,
                    gradientColors = listOf(AuroraColors.Teal, AuroraColors.Cyan),
                    onClick = onAddContactClick,
                    modifier = Modifier.weight(1f)
                )
                BeFireflyActionButton(
                    label = "New Group",
                    icon = Icons.Default.GroupAdd,
                    gradientColors = listOf(AuroraColors.SoftPink, AuroraColors.Lavender),
                    onClick = onCreateGroupClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Chat cards with spring physics
        itemsIndexed(chats) { index, chat ->
            val avatarColor = avatarColors[
                chat.name.hashCode().and(0x7FFFFFFF) % avatarColors.size
            ]
            val connType = when {
                chat.isGroup -> ConnectionType.BLE_MESH
                isWifiConnected -> ConnectionType.ONLINE
                else -> ConnectionType.OFFLINE
            }

            AuroraChatCard(
                chat = chat,
                index = index,
                avatarColor = avatarColor,
                connectionType = connType,
                onClick = { onChatClick(chat.id, chat.isGroup) },
                onLongClick = {
                    editName = chat.name
                    editingContact = chat
                }
            )
        }

        // Empty state
        if (chats.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedFireflyLogo(modifier = Modifier.size(120.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No chats yet",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 16.sp
                        )
                        Text(
                            "Add a contact to start messaging",
                            color = Color.White.copy(alpha = 0.25f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// AURORA CHAT CARD — glass card with spring physics
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuroraChatCard(
    chat: ChatPreview,
    index: Int,
    avatarColor: Color,
    connectionType: ConnectionType,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(index * 80L)
        visible = true
    }

    val enterProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "enter"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    // ── SAFE TIMESTAMP: handles missing field gracefully ─────
    // If your ChatPreview has `lastMessageTime` (Long), uncomment below.
    // Otherwise this safely shows nothing.
    val formattedTime: String = remember(chat) {
        // TODO: Replace with your actual timestamp field name
        // Options: chat.lastMessageTimestamp, chat.updatedAt, chat.lastActivity
        // Example with reflection fallback:
        runCatching {
            val field = chat::class.java.getDeclaredField("lastMessageTime")
            field.isAccessible = true
            val time = field.get(chat) as? Long ?: 0L
            if (time > 0) {
                val fmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                fmt.format(java.util.Date(time))
            } else ""
        }.getOrDefault("")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = (1f - enterProgress) * 50f
                alpha = enterProgress
            }
            .glassCard(cornerRadius = 20.dp, alpha = 0.04f)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Avatar with glow
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(avatarColor.copy(alpha = 0.20f), CircleShape)
                        .blur(14.dp)
                )

                Surface(
                    shape = CircleShape,
                    color = avatarColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(52.dp),
                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (chat.avatarUri != null) {
                            AsyncImage(
                                model = chat.avatarUri,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = chat.name.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Status indicator
                if (connectionType != ConnectionType.OFFLINE || chat.isGroup) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 3.dp, y = 3.dp)
                    ) {
                        PulsingStatus(
                            connectionType = if (chat.isGroup) ConnectionType.BLE_MESH else connectionType,
                            size = 10.dp
                        )
                    }
                }
            }

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = chat.name,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (chat.isGroup) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AuroraColors.SoftPink.copy(alpha = 0.12f),
                            border = BorderStroke(
                                0.5.dp,
                                AuroraColors.SoftPink.copy(alpha = 0.25f)
                            )
                        ) {
                            Text(
                                text = "GROUP",
                                color = AuroraColors.SoftPink.copy(alpha = 0.9f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (formattedTime.isNotEmpty()) {
                        Text(
                            text = formattedTime,
                            color = Color.White.copy(alpha = 0.25f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Status
                Text(
                    text = when (connectionType) {
                        ConnectionType.ONLINE -> "Online"
                        ConnectionType.BLE_MESH -> "Mesh Connected"
                        ConnectionType.OFFLINE -> if (chat.isGroup) "Group Chat" else "Offline"
                    },
                    color = when (connectionType) {
                        ConnectionType.ONLINE -> AuroraColors.Teal.copy(alpha = 0.85f)
                        ConnectionType.BLE_MESH -> AuroraColors.Cyan.copy(alpha = 0.85f)
                        ConnectionType.OFFLINE -> Color.White.copy(alpha = 0.35f)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )

                // Last message preview
                if (chat.lastMessage.isNotEmpty()) {
                    Text(
                        text = chat.lastMessage,
                        color = Color.White.copy(alpha = 0.40f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Chevron
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}