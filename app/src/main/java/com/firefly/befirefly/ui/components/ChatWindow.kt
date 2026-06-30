package com.firefly.befirefly.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import coil.compose.AsyncImage
import com.firefly.befirefly.ui.screens.Message
import com.firefly.befirefly.utils.AudioPlayer
import kotlinx.coroutines.launch
import kotlin.math.*

@Composable
fun ChatWindow(
    chatName: String,
    chatAvatar: String?,
    messages: List<Message>,
    onSendMessage: (String) -> Unit,
    onSendImage: (android.net.Uri) -> Unit,
    onSendFile: (android.net.Uri) -> Unit,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    isRecording: Boolean,
    audioPlayer: AudioPlayer,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onDeleteMessage: ((Long) -> Unit)? = null,
    onClearChat: (() -> Unit)? = null,
    onExportChat: (() -> Unit)? = null,
    onWallpaperClick: (() -> Unit)? = null,
    wallpaperGradientIndex: Int? = null,
    wallpaperImageUri: String? = null,
    connectionState: Int = 2,
    remoteIsTyping: Boolean = false,
    isDarkTheme: Boolean = true,
    // Tier 1 features
    contacts: List<com.firefly.befirefly.domain.model.ChatPreview> = emptyList(),
    replyingTo: Message? = null,
    onReact: ((Message, String) -> Unit)? = null,
    onReply: ((Message) -> Unit)? = null,
    onCancelReply: (() -> Unit)? = null,
    onForward: ((Message, String) -> Unit)? = null,
    onEditMessage: ((Message, String) -> Unit)? = null,
    onDeleteForEveryone: ((Message) -> Unit)? = null,
    onTyping: (() -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
    onClearSearch: (() -> Unit)? = null,
    searchResults: List<Message> = emptyList(),
    disappearingSeconds: Long = 0L,
    onSetDisappearing: ((Long) -> Unit)? = null,
    isVerified: Boolean = false,
    myPublicKey: String = "",
    contactPublicKey: String = "",
    safetyNumber: String = "",
    onSetVerified: ((Boolean) -> Unit)? = null,
    isMuted: Boolean = false,
    onToggleMute: (() -> Unit)? = null,
    initialDraft: String = "",
    onDraftChanged: ((String) -> Unit)? = null,
    onStar: ((Message) -> Unit)? = null,
    onPin: ((Message) -> Unit)? = null
) {
    var messageText by remember(contactPublicKey) { mutableStateOf(initialDraft) }
    var showMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hiddenMessageIds = remember { mutableStateListOf<Long>() }
    val listState = rememberLazyListState()

    // Tier 1 UI state
    var actionMessage by remember { mutableStateOf<Message?>(null) }
    var forwardingMessage by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showDisappearingPicker by remember { mutableStateOf(false) }
    var showVerifySheet by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    fun deleteLocally(id: Long) {
        hiddenMessageIds.add(id)
        scope.launch {
            val result = snackbarHostState.showSnackbar("Message deleted", "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) hiddenMessageIds.remove(id)
            else { onDeleteMessage?.invoke(id); hiddenMessageIds.remove(id) }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onSendImage(uri) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onSendFile(uri) }

    val networkMode = when (connectionState) {
        2 -> NetworkMode.CLOUD_CONNECTED
        1 -> NetworkMode.MESH_ACTIVE
        else -> NetworkMode.OFFLINE
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    BeFireflyAuroraBackground(isAnimated = true, networkMode = networkMode) {
        Box(modifier = modifier.fillMaxSize().imePadding()) {
            // ── FLOATING PILL HEADER STATE ──
            var isPillExpanded by remember { mutableStateOf(false) }

            val pillHeight by animateFloatAsState(
                targetValue = if (isPillExpanded) 180f else 64f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "pill_h"
            )
            val pillWidth by animateFloatAsState(
                targetValue = if (isPillExpanded) 0.92f else 0.65f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "pill_w"
            )
            val pillAlpha by animateFloatAsState(
                targetValue = if (isPillExpanded) 0.28f else 0.06f,
                animationSpec = tween(300), label = "pill_a"
            )
            val pillGlowAlpha by animateFloatAsState(
                targetValue = if (isPillExpanded) 0.25f else 0f,
                animationSpec = tween(400), label = "pill_glow"
            )
            val bdrAlpha by animateFloatAsState(
                targetValue = if (isPillExpanded) 0.65f else 0f,
                animationSpec = tween(350), label = "bdr_alpha"
            )

            // Animated linear gradient border setup
            val infiniteTransition = rememberInfiniteTransition(label = "border")
            val bdrOffset by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 1000f,
                animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
                label = "bdr_anim"
            )
            val animatedBorderBrush = Brush.linearGradient(
                colors = listOf(AuroraColors.Teal, Color(0xFFFF79C6), Color(0xFFFFD700), AuroraColors.Teal),
                start = Offset(bdrOffset, 0f),
                end = Offset(bdrOffset + 500f, 500f)
            )

            // ── SCROLLING MESSAGES (Underneath overlays) ──
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 110.dp, bottom = 100.dp) // Generous padding to clear floating top/bottom bars
            ) {
                // Today Date Separator Badge
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Today", color = Color.White.copy(alpha = 0.25f), fontSize = 11.sp,
                            modifier = Modifier.background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(20.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                        )
                    }
                }

                val visibleMessages = messages.filter { it.id !in hiddenMessageIds }
                itemsIndexed(visibleMessages, key = { _, msg -> msg.id }) { index, msg ->
                    AuroraMessageBubble(
                        message = msg, index = index, audioPlayer = audioPlayer,
                        onLongPress = { actionMessage = it },
                        onDoubleTap = { onReact?.invoke(it, "❤️") }
                    )
                }
            }

            // ── FLOATING HEADER ROW OVERLAY ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color(0xFF0A0518).copy(alpha = 0.65f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Back button
                    Box(
                        modifier = Modifier.size(42.dp).padding(top = 10.dp)
                            .glassCard(cornerRadius = 21.dp, alpha = 0.07f)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(18.dp)) }

                    // ── THE PILL ──
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(pillWidth)
                            .height(pillHeight.dp)
                    ) {
                        // Pill glow behind
                        if (isPillExpanded) {
                            Box(
                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = pillGlowAlpha }
                                    .background(Brush.radialGradient(listOf(AuroraColors.Teal.copy(alpha = 0.15f), Color.Transparent)), RoundedCornerShape(32.dp))
                                    .blur(20.dp)
                            )
                        }

                        // Heavy frosted blur layers — only when expanded
                        if (isPillExpanded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(Color(0xFF0D1117).copy(alpha = 0.82f), RoundedCornerShape(32.dp))
                                    .blur(40.dp)
                            )
                            // Teal-tinted frost overlay for aurora bleed
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                AuroraColors.Teal.copy(alpha = 0.06f),
                                                Color(0xFF0D1117).copy(alpha = 0.60f),
                                                Color(0xFF0D1117).copy(alpha = 0.75f)
                                            )
                                        ),
                                        RoundedCornerShape(32.dp)
                                    )
                            )
                        }

                        // Pill body
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .glassCard(cornerRadius = 32.dp, alpha = pillAlpha)
                                .border(
                                    1.dp,
                                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), AuroraColors.Teal.copy(alpha = if (isPillExpanded) 0.15f else 0.05f), Color.White.copy(alpha = 0.06f))),
                                    RoundedCornerShape(32.dp)
                                )
                                .then(
                                    // Inject Animated linear gradient border when open
                                    if (bdrAlpha > 0f) Modifier.border(1.dp, animatedBorderBrush, RoundedCornerShape(32.dp)) else Modifier
                                )
                                .clickable { isPillExpanded = !isPillExpanded }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            // Top row
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                    Box(modifier = Modifier.size(40.dp).background(AuroraColors.Teal.copy(alpha = 0.20f), CircleShape).blur(10.dp))
                                    Box(
                                        modifier = Modifier.size(40.dp).background(Brush.linearGradient(listOf(Color(0xFFFF6E40), Color(0xFFFFAB40))), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (chatAvatar != null) {
                                            AsyncImage(model = chatAvatar, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                        } else {
                                            Text(chatName.take(1).uppercase(), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(chatName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(modifier = Modifier.size(6.dp).background(networkMode.color, CircleShape))
                                        Text(networkMode.label, color = networkMode.color.copy(alpha = 0.85f), fontSize = 11.sp, letterSpacing = 0.5.sp)
                                    }
                                }

                                val chevRotation by animateFloatAsState(targetValue = if (isPillExpanded) 180f else 0f, animationSpec = tween(400), label = "chev")
                                Icon(
                                    Icons.Default.KeyboardArrowDown, "Expand",
                                    tint = if (isPillExpanded) AuroraColors.Teal else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevRotation }
                                )
                            }

                            // Expanded content
                            if (isPillExpanded) {
                                Spacer(Modifier.height(12.dp))
                                Box(modifier = Modifier.fillMaxWidth(0.55f).height(1.dp).align(Alignment.CenterHorizontally).background(Brush.horizontalGradient(listOf(Color.Transparent, AuroraColors.Teal.copy(alpha = 0.3f), Color.Transparent))))
                                Spacer(Modifier.height(10.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp)).border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                                        Icon(if (isVerified) Icons.Default.VerifiedUser else Icons.Default.Lock, null, tint = if (isVerified) AuroraColors.Teal else AuroraColors.Teal.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                                        Text(if (isVerified) "Verified" else "E2E Encrypted", color = if (isVerified) AuroraColors.Teal.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp)).border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                                        PulsingStatus(connectionType = if (networkMode == NetworkMode.OFFLINE) ConnectionType.OFFLINE else ConnectionType.ONLINE, size = 6.dp)
                                        Text(networkMode.label, color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    PillActionButton(icon = Icons.Default.Search, label = "Search") { showSearch = true; isPillExpanded = false }
                                    PillActionButton(icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, label = if (isMuted) "Muted" else "Mute") { onToggleMute?.invoke() }
                                    PillActionButton(icon = Icons.Default.MoreHoriz, label = "More") { showMenu = true }
                                }
                            }
                        }
                    }

                    // Menu button animates scale/opacity out when open
                    val menuScale by animateFloatAsState(targetValue = if (isPillExpanded) 0.7f else 1f, label = "menu_s")
                    val menuAlpha by animateFloatAsState(targetValue = if (isPillExpanded) 0f else 1f, label = "menu_a")

                    Box(
                        modifier = Modifier.size(42.dp).padding(top = 10.dp)
                            .graphicsLayer { scaleX = menuScale; scaleY = menuScale; alpha = menuAlpha }
                            .glassCard(cornerRadius = 21.dp, alpha = 0.07f)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            .clickable(enabled = !isPillExpanded) { showMenu = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.MoreVert, "Options", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp)) }
                }

                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(Color(0xFF1A1F30))) {
                    DropdownMenuItem(text = { Text("🗑️  Clear Chat", color = Color.White) }, onClick = { showMenu = false; onClearChat?.invoke() })
                    DropdownMenuItem(text = { Text("📤  Export Chat", color = Color.White) }, onClick = { showMenu = false; onExportChat?.invoke() })
                    DropdownMenuItem(text = { Text("🎨  Wallpaper", color = Color.White) }, onClick = { showMenu = false; onWallpaperClick?.invoke() })
                    DropdownMenuItem(text = { Text("⏱️  Disappearing messages", color = Color.White) }, onClick = { showMenu = false; showDisappearingPicker = true })
                    DropdownMenuItem(text = { Text(if (isVerified) "🛡️  Verified ✓" else "🛡️  Verify Contact", color = Color.White) }, onClick = { showMenu = false; showVerifySheet = true })
                }
            }

            // Remote Typing Indicator overlay
            AnimatedVisibility(
                visible = remoteIsTyping,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 80.dp)
            ) {
                AuroraTypingIndicator()
            }

            // Reply preview bar (sits just above the input)
            AnimatedVisibility(
                visible = replyingTo != null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 74.dp).fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                replyingTo?.let { rm ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .glassCard(cornerRadius = 12.dp, alpha = 0.10f)
                            .border(1.dp, AuroraColors.Teal.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Box(Modifier.width(3.dp).height(34.dp).background(AuroraColors.Teal, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (rm.isSentByMe) "Replying to yourself" else "Replying", color = AuroraColors.Teal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(rm.text.ifBlank { "[media]" }, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Default.Close, "Cancel reply", tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp).clickable { onCancelReply?.invoke() })
                    }
                }
            }

            // Long-press action menu
            actionMessage?.let { msg ->
                MessageActionSheet(
                    message = msg,
                    onDismiss = { actionMessage = null },
                    onReact = { emoji -> onReact?.invoke(msg, emoji); actionMessage = null },
                    onReply = { onReply?.invoke(msg); actionMessage = null },
                    onForward = { forwardingMessage = msg; actionMessage = null },
                    onCopy = {
                        val clip = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clip.setPrimaryClip(android.content.ClipData.newPlainText("message", msg.text))
                        actionMessage = null
                    },
                    onEdit = if (msg.isSentByMe && msg.type == "text" && !msg.isDeleted) ({ editingMessage = msg; actionMessage = null }) else null,
                    onStar = { onStar?.invoke(msg); actionMessage = null },
                    onPin = { onPin?.invoke(msg); actionMessage = null },
                    onDeleteForMe = { deleteLocally(msg.id); actionMessage = null },
                    onDeleteForEveryone = if (msg.isSentByMe && !msg.isDeleted) ({ onDeleteForEveryone?.invoke(msg); actionMessage = null }) else null
                )
            }

            // Forward picker
            forwardingMessage?.let { msg ->
                ForwardPickerDialog(
                    contacts = contacts,
                    onDismiss = { forwardingMessage = null },
                    onPick = { id -> onForward?.invoke(msg, id); forwardingMessage = null }
                )
            }

            // Edit dialog
            editingMessage?.let { msg ->
                EditMessageDialog(
                    initialText = msg.text,
                    onDismiss = { editingMessage = null },
                    onConfirm = { newText -> onEditMessage?.invoke(msg, newText); editingMessage = null }
                )
            }

            // Search overlay
            if (showSearch) {
                MessageSearchOverlay(
                    results = searchResults,
                    onQueryChange = { onSearch?.invoke(it) },
                    onResultClick = { id ->
                        showSearch = false
                        onClearSearch?.invoke()
                        val visible = messages.filter { it.id !in hiddenMessageIds }
                        val idx = visible.indexOfFirst { it.id == id }
                        if (idx >= 0) scope.launch { listState.animateScrollToItem(idx + 1) } // +1 for the date header
                    },
                    onClose = { showSearch = false; onClearSearch?.invoke() }
                )
            }

            // Disappearing-messages active banner
            if (disappearingSeconds > 0L) {
                Row(
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 76.dp)
                        .background(AuroraColors.Teal.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                        .border(1.dp, AuroraColors.Teal.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Timer, null, tint = AuroraColors.Teal, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Disappearing · ${formatDisappearing(disappearingSeconds)}", color = AuroraColors.Teal.copy(alpha = 0.9f), fontSize = 11.sp)
                }
            }

            // Pinned message banner
            val pinnedMsg = messages.firstOrNull { it.isPinned && !it.isDeleted }
            if (pinnedMsg != null) {
                Row(
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
                        .padding(top = if (disappearingSeconds > 0L) 104.dp else 76.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth()
                        .glassCard(cornerRadius = 12.dp, alpha = 0.12f)
                        .clickable {
                            val visible = messages.filter { it.id !in hiddenMessageIds }
                            val idx = visible.indexOfFirst { it.id == pinnedMsg.id }
                            if (idx >= 0) scope.launch { listState.animateScrollToItem(idx + 1) }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PushPin, null, tint = AuroraColors.Teal, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Pinned message", color = AuroraColors.Teal.copy(alpha = 0.8f), fontSize = 10.sp)
                        Text(pinnedMsg.text.ifBlank { "[media]" }, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.Close, "Unpin", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp).clickable { onPin?.invoke(pinnedMsg) })
                }
            }

            // Disappearing timer picker
            if (showDisappearingPicker) {
                DisappearingTimerDialog(
                    currentSeconds = disappearingSeconds,
                    onDismiss = { showDisappearingPicker = false },
                    onPick = { secs -> onSetDisappearing?.invoke(secs); showDisappearingPicker = false }
                )
            }

            // Verify contact sheet (safety number + QR)
            if (showVerifySheet) {
                VerifyContactSheet(
                    contactName = chatName,
                    isVerified = isVerified,
                    myPublicKey = myPublicKey,
                    contactPublicKey = contactPublicKey,
                    safetyNumber = safetyNumber,
                    onSetVerified = { v -> onSetVerified?.invoke(v) },
                    onDismiss = { showVerifySheet = false }
                )
            }

            // ── FLOATING INPUT BAR OVERLAY ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0A0518).copy(alpha = 0.85f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        modifier = Modifier.size(38.dp).glassCard(cornerRadius = 19.dp, alpha = 0.08f).border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape).clickable { filePickerLauncher.launch(arrayOf("*/*")) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.AttachFile, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(17.dp)) }

                    Box(
                        modifier = Modifier.size(38.dp).glassCard(cornerRadius = 19.dp, alpha = 0.08f).border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape).clickable { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Image, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(17.dp)) }

                    Box(
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                            .glassCard(cornerRadius = 20.dp, alpha = 0.08f)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    ) {
                        OutlinedTextField(
                            value = messageText, onValueChange = { messageText = it; if (it.isNotBlank()) onTyping?.invoke(); onDraftChanged?.invoke(it) },
                            placeholder = { Text("Message...", color = Color.White.copy(alpha = 0.25f)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                                cursorColor = AuroraColors.Teal
                            ),
                            maxLines = 4
                        )
                    }

                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val btnScale by animateFloatAsState(targetValue = if (isPressed) 0.85f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy), label = "send_btn")
                    val btnColor by animateColorAsState(targetValue = if (isRecording) Color(0xFFFF5252) else AuroraColors.Teal, animationSpec = tween(300), label = "btn_color")

                    Box(
                        modifier = Modifier.size(42.dp)
                            .graphicsLayer { scaleX = btnScale; scaleY = btnScale }
                            .background(Brush.linearGradient(if (messageText.isNotBlank()) listOf(AuroraColors.Teal, Color(0xFF0099BB)) else listOf(btnColor, btnColor.copy(alpha = 0.7f))), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .then(
                                if (messageText.isNotBlank()) Modifier.clickable(interactionSource = interactionSource, indication = null) { onSendMessage(messageText); messageText = ""; onDraftChanged?.invoke("") }
                                else Modifier.pointerInput(Unit) { detectTapGestures(onPress = { onRecordStart(); try { awaitRelease() } finally { onRecordStop() } }) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                messageText.isNotBlank() -> Icons.Default.Send
                                isRecording -> Icons.Default.Stop
                                else -> Icons.Default.Mic
                            },
                            contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }
}

// ============================================================================
// AURORA MESSAGE BUBBLE
// ============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuroraMessageBubble(
    message: Message,
    index: Int,
    audioPlayer: AudioPlayer,
    onLongPress: ((Message) -> Unit)? = null,
    onDoubleTap: ((Message) -> Unit)? = null
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(index * 30L); visible = true }

    val enterProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "bubble_enter"
    )

    val alignment = if (message.isSentByMe) Alignment.End else Alignment.Start
    val context = androidx.compose.ui.platform.LocalContext.current
    val timeFormat = remember { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(java.util.Date(message.timestamp)) }

    Column(
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            alpha = enterProgress
            translationY = (1f - enterProgress) * 20f // Subtler vertical slide from HTML design
        },
        horizontalAlignment = alignment
    ) {
        // Replicating exact HTML background colors
        val bubbleBrush = if (message.isSentByMe) {
            Brush.linearGradient(listOf(Color(0xFF00C8E6).copy(alpha = 0.28f), Color(0xFF0078B4).copy(alpha = 0.22f)))
        } else {
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.05f)))
        }
        val borderColor = if (message.isSentByMe) Color(0xFF00E5FF).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.1f)
        val shape = if (message.isSentByMe) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            val avatarCircle = @Composable {
                Box(
                    modifier = Modifier.size(28.dp)
                        .background(
                            Brush.linearGradient(if (message.isSentByMe) listOf(Color(0xFF7C4DFF), Color(0xFFB388FF)) else listOf(Color(0xFFFF6E40), Color(0xFFFFAB40))),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = if (message.isSentByMe) "K" else "C"
                    Text(initial, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            val bubbleBox = @Composable {
                Box(
                    modifier = Modifier.widthIn(max = 280.dp)
                        .background(bubbleBrush, shape).border(1.dp, borderColor, shape)
                        .combinedClickable(
                            onClick = {
                                if (message.type == "file" && message.mediaUri != null) {
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                            setDataAndType(android.net.Uri.parse(message.mediaUri), message.mimeType ?: "*/*")
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "Open File"))
                                    } catch (_: Exception) {}
                                }
                            },
                            onLongClick = { onLongPress?.invoke(message) },
                            onDoubleClick = { onDoubleTap?.invoke(message) }
                        )
                        .padding(horizontal = 13.dp, vertical = 10.dp)
                ) {
                    Column {
                        // Reply quote
                        if (message.replyToText != null && !message.isDeleted) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                                    .padding(start = 8.dp, top = 5.dp, bottom = 5.dp, end = 8.dp)
                            ) {
                                Box(Modifier.width(3.dp).height(28.dp).background(AuroraColors.Teal, RoundedCornerShape(2.dp)))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(if (message.replyToSentByMe) "You" else "Them", color = AuroraColors.Teal.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text(message.replyToText.ifBlank { "[media]" }, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (message.isDeleted) {
                            Text("🚫 This message was deleted", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        }
                        if (!message.isDeleted && message.imageUri != null) {
                            AsyncImage(model = message.imageUri, contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.height(6.dp))
                        }
                        if (!message.isDeleted && message.audioPath != null) {
                            AuroraAudioWaveform(audioPath = message.audioPath, audioPlayer = audioPlayer, isSentByMe = message.isSentByMe)
                            Spacer(Modifier.height(4.dp))
                        }
                        if (!message.isDeleted && message.type == "file" && message.fileName != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Default.InsertDriveFile, null, tint = AuroraColors.Teal, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(message.fileName, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (message.fileSize != null && message.fileSize > 0) {
                                        Text(formatFileSize(message.fileSize), color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                                    }
                                }
                                if (message.transferProgress != null && message.transferProgress < 100) {
                                    CircularProgressIndicator(progress = { message.transferProgress / 100f }, modifier = Modifier.size(20.dp), color = AuroraColors.Teal, strokeWidth = 2.dp)
                                }
                            }
                        }
                        if (!message.isDeleted && message.text.isNotBlank() && message.type != "file") {
                            Text(formatMessageText(message.text), color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, lineHeight = 20.sp)
                        }

                        // Integrated Footer (Time + Tick inside bubble as per HTML prototype)
                        Row(
                            modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(formattedTime, color = Color.White.copy(alpha = 0.28f), fontSize = 10.sp)
                            if (message.isStarred) {
                                Icon(Icons.Default.Star, null, tint = AuroraColors.WarmGolden.copy(alpha = 0.8f), modifier = Modifier.size(11.dp))
                            }
                            if (message.isEdited && !message.isDeleted) {
                                Text("edited", color = Color.White.copy(alpha = 0.28f), fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                            if (message.isSentByMe) {
                                Icon(
                                    if (message.status == "READ" || message.status == "DELIVERED") Icons.Default.DoneAll else Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF).copy(alpha = 0.8f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (message.isSentByMe) {
                bubbleBox()
                avatarCircle()
            } else {
                avatarCircle()
                bubbleBox()
            }
        }
        // Reaction chip
        if (message.reaction != null && !message.isDeleted) {
            Box(
                modifier = Modifier.padding(top = 2.dp, start = 36.dp, end = 36.dp)
                    .background(Color(0xFF1A1F30), RoundedCornerShape(12.dp))
                    .border(1.dp, AuroraColors.Teal.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(message.reaction, fontSize = 13.sp)
            }
        }
    }
}

// ============================================================================
// AUDIO WAVEFORM
// ============================================================================

@Composable
fun AuroraAudioWaveform(audioPath: String, audioPlayer: AudioPlayer, isSentByMe: Boolean) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                progress = audioPlayer.progress()
                kotlinx.coroutines.delay(80)
            }
        } else {
            progress = 0f
        }
    }
    var durationText by remember(audioPath) { mutableStateOf("0:00") }
    LaunchedEffect(audioPath) {
        durationText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(audioPath)
                val durMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                retriever.release()
                val totalSec = durMs / 1000
                "%d:%02d".format(totalSec / 60, totalSec % 60)
            } catch (e: Exception) {
                "0:00"
            }
        }
    }
    val waveHeights = remember { listOf(0.4f, 0.7f, 0.5f, 0.9f, 0.6f, 0.8f, 0.4f, 1.0f, 0.6f, 0.7f, 0.5f, 0.8f, 0.3f, 0.9f, 0.5f, 0.7f, 0.4f, 0.8f, 0.6f, 0.5f) }
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart), label = "wave_anim"
    )

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val interactionSource = remember { MutableInteractionSource() }
        val btnPressed by interactionSource.collectIsPressedAsState()
        val btnScale by animateFloatAsState(targetValue = if (btnPressed) 0.88f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy), label = "play_btn")

        // Animated gradient switch between Default (Teal) and Playing (Ember)
        val playBtnBrush = if (isPlaying) {
            Brush.linearGradient(listOf(Color(0xFFFF6B35), Color(0xFFFF4500)))
        } else {
            Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF0099BB)))
        }

        Box(
            modifier = Modifier.size(34.dp)
                .graphicsLayer { scaleX = btnScale; scaleY = btnScale }
                .background(playBtnBrush, CircleShape)
                .clickable(interactionSource = interactionSource, indication = null) {
                    if (isPlaying) { audioPlayer.stop(); isPlaying = false }
                    else { isPlaying = true; audioPlayer.playFile(java.io.File(audioPath)) { isPlaying = false } }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }

        Canvas(modifier = Modifier.weight(1f).height(26.dp)) {
            val barWidth = 3.dp.toPx()
            val barGap = 2.dp.toPx()
            waveHeights.forEachIndexed { i, heightFraction ->
                val animH = if (isPlaying) {
                    (heightFraction + sin(waveOffset * 2f * PI.toFloat() + i * 0.5f).toFloat() * 0.2f).coerceIn(0.2f, 1.0f)
                } else heightFraction
                val barH = size.height * animH
                val x = i * (barWidth + barGap)
                val y = (size.height - barH) / 2f
                // Bars up to the play position are bright; the rest are dimmed.
                val played = (i + 1f) / waveHeights.size <= progress
                val barColor = if (played) Color(0xFF00E5FF) else Color(0xFF00E5FF).copy(alpha = 0.35f)
                drawRoundRect(color = barColor, topLeft = Offset(x, y), size = Size(barWidth, barH), cornerRadius = CornerRadius(barWidth / 2f))
            }
        }

        Text(durationText, color = Color.White.copy(alpha = 0.38f), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

// ============================================================================
// TYPING INDICATOR
// ============================================================================

@Composable
fun AuroraTypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    val dot1 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "d1")
    val dot2 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(600, delayMillis = 200), RepeatMode.Reverse), label = "d2")
    val dot3 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(600, delayMillis = 400), RepeatMode.Reverse), label = "d3")

    Box(
        modifier = Modifier.width(72.dp).height(36.dp)
            .glassCard(cornerRadius = 18.dp, alpha = 0.06f)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
    ) {
        Row(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).offset(y = (-4f * dot1).dp).background(AuroraColors.Teal, CircleShape))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(6.dp).offset(y = (-4f * dot2).dp).background(AuroraColors.Teal, CircleShape))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(6.dp).offset(y = (-4f * dot3).dp).background(AuroraColors.Teal, CircleShape))
        }
    }
}

fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

// ============================================================================
// PILL ACTION BUTTON
// ============================================================================

@Composable
fun PillActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "pill_btn"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(Color.White.copy(alpha = 0.06f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = AuroraColors.Teal.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp)
    }
}

// ============================================================================
// MESSAGE ACTION SHEET (long-press menu)
// ============================================================================

@Composable
fun MessageActionSheet(
    message: Message,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (() -> Unit)?,
    onStar: () -> Unit,
    onPin: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: (() -> Unit)?
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF12172A), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .padding(vertical = 12.dp)
        ) {
            // Quick emoji reactions
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDC4D", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDD25").forEach { emoji ->
                    Box(
                        modifier = Modifier.size(42.dp)
                            .background(
                                if (message.reaction == emoji) AuroraColors.Teal.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                                CircleShape
                            )
                            .clickable { onReact(emoji) },
                        contentAlignment = Alignment.Center
                    ) { Text(emoji, fontSize = 20.sp) }
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 16.dp).height(1.dp).background(Color.White.copy(alpha = 0.06f)))
            MessageActionRow(Icons.Default.Reply, "Reply", onReply)
            if (message.type == "text" && !message.isDeleted) MessageActionRow(Icons.Default.ContentCopy, "Copy", onCopy)
            MessageActionRow(Icons.Default.Forward, "Forward", onForward)
            MessageActionRow(if (message.isStarred) Icons.Default.Star else Icons.Default.StarBorder, if (message.isStarred) "Unstar" else "Star", onStar)
            MessageActionRow(Icons.Default.PushPin, if (message.isPinned) "Unpin" else "Pin", onPin)
            if (onEdit != null) MessageActionRow(Icons.Default.Edit, "Edit", onEdit)
            MessageActionRow(Icons.Default.DeleteOutline, "Delete for me", onDeleteForMe)
            if (onDeleteForEveryone != null) MessageActionRow(Icons.Default.Delete, "Delete for everyone", onDeleteForEveryone, danger = true)
        }
    }
}

@Composable
private fun MessageActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (danger) Color(0xFFFF5252) else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = if (danger) Color(0xFFFF5252) else Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
    }
}

// ============================================================================
// FORWARD PICKER
// ============================================================================

@Composable
fun ForwardPickerDialog(
    contacts: List<com.firefly.befirefly.domain.model.ChatPreview>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)
                .background(Color(0xFF12172A), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Text("Forward to", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            if (contacts.isEmpty()) {
                Text("No contacts to forward to", color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 20.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(contacts) { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onPick(c.id) }.padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(38.dp).background(Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFFB388FF))), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Text(c.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(12.dp))
                            Text(c.name, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// EDIT MESSAGE DIALOG
// ============================================================================

@Composable
fun EditMessageDialog(initialText: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF12172A), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text("Edit message", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = AuroraColors.Teal, unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    cursorColor = AuroraColors.Teal
                )
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                Text("Cancel", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.clickable { onDismiss() }.padding(12.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save", color = AuroraColors.Teal, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { if (text.isNotBlank()) onConfirm(text) }.padding(12.dp))
            }
        }
    }
}

// ============================================================================
// MESSAGE SEARCH OVERLAY
// ============================================================================

@Composable
fun MessageSearchOverlay(results: List<Message>, onQueryChange: (String) -> Unit, onClose: () -> Unit, onResultClick: (Long) -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    val timeFormat = remember { java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()) }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0518).copy(alpha = 0.97f))) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ArrowBack, "Close search", tint = Color.White, modifier = Modifier.size(24.dp).clickable { onClose() })
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; onQueryChange(it) },
                    placeholder = { Text("Search messages...", color = Color.White.copy(alpha = 0.3f)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = AuroraColors.Teal, unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        cursorColor = AuroraColors.Teal
                    )
                )
            }
            Spacer(Modifier.height(16.dp))
            if (query.isNotBlank() && results.isEmpty()) {
                Text("No messages found", color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(8.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { msg ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onResultClick(msg.id) }
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(msg.text, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${if (msg.isSentByMe) "You" else "Them"} \u00B7 ${timeFormat.format(java.util.Date(msg.timestamp))}",
                            color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// DISAPPEARING MESSAGES
// ============================================================================

fun formatDisappearing(seconds: Long): String = when (seconds) {
    0L -> "Off"
    in 1..3599 -> "${seconds / 60} min"
    in 3600..86399 -> "${seconds / 3600} hour${if (seconds / 3600 > 1) "s" else ""}"
    in 86400..604799 -> "${seconds / 86400} day${if (seconds / 86400 > 1) "s" else ""}"
    else -> "${seconds / 604800} week${if (seconds / 604800 > 1) "s" else ""}"
}

@Composable
fun DisappearingTimerDialog(currentSeconds: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val options = listOf(
        "Off" to 0L,
        "1 hour" to 3600L,
        "1 day" to 86400L,
        "1 week" to 604800L
    )
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF12172A), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text("Disappearing messages", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "New messages will vanish from both devices after the timer.",
                color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            options.forEach { (label, secs) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(secs) }.padding(vertical = 13.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (currentSeconds == secs) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        null,
                        tint = if (currentSeconds == secs) AuroraColors.Teal else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
                }
            }
        }
    }
}

// ============================================================================
// VERIFY CONTACT (safety number + QR key-pinning)
// ============================================================================

@Composable
fun VerifyContactSheet(
    contactName: String,
    isVerified: Boolean,
    myPublicKey: String,
    contactPublicKey: String,
    safetyNumber: String,
    onSetVerified: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var scanning by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0518).copy(alpha = 0.98f))) {
        if (scanning) {
            QrCodeScanner(
                onQrCodeScanned = { scanned ->
                    val clean = scanned.trim()
                    // The other person's QR may be a raw key or a {"n":..,"k":..} JSON — extract the key.
                    val scannedKey = try {
                        org.json.JSONObject(clean).optString("k", clean)
                    } catch (e: Exception) { clean }
                    if (scannedKey == contactPublicKey && contactPublicKey.isNotBlank()) {
                        onSetVerified(true)
                        android.widget.Toast.makeText(ctx, "✅ Verified — keys match", android.widget.Toast.LENGTH_SHORT).show()
                        scanning = false
                        onDismiss()
                    } else {
                        android.widget.Toast.makeText(ctx, "⚠️ Keys DON'T match — possible interception!", android.widget.Toast.LENGTH_LONG).show()
                        scanning = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier.statusBarsPadding().padding(16.dp).size(42.dp)
                    .glassCard(cornerRadius = 21.dp, alpha = 0.1f)
                    .clickable { scanning = false },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(20.dp)) }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ArrowBack, "Close", tint = Color.White, modifier = Modifier.size(24.dp).clickable { onDismiss() })
                    Spacer(Modifier.width(12.dp))
                    Text("Verify $contactName", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Compare this safety number on both phones, or scan each other's code. If they match, no one is intercepting your messages.",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp
                )
                Spacer(Modifier.height(20.dp))

                // My QR
                Box(
                    modifier = Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp),
                    contentAlignment = Alignment.Center
                ) { QrCodeGenerator(content = myPublicKey, size = 200.dp) }
                Spacer(Modifier.height(8.dp))
                Text("Your code — let $contactName scan it", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)

                Spacer(Modifier.height(20.dp))

                // Safety number
                Text("SAFETY NUMBER", color = AuroraColors.Teal.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    safetyNumber,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 16.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    lineHeight = 26.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                        .glassCard(cornerRadius = 14.dp, alpha = 0.05f)
                        .padding(16.dp)
                )

                Spacer(Modifier.height(24.dp))

                // Scan button
                Box(
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                        .background(Brush.linearGradient(listOf(AuroraColors.Teal, Color(0xFF0099BB))), RoundedCornerShape(16.dp))
                        .clickable { scanning = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("Scan ${contactName}'s code", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Manual verified toggle
                Box(
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                        .glassCard(cornerRadius = 16.dp, alpha = 0.06f)
                        .clickable { onSetVerified(!isVerified) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            if (isVerified) Icons.Default.VerifiedUser else Icons.Default.Shield,
                            null,
                            tint = if (isVerified) AuroraColors.Teal else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            if (isVerified) "Verified ✓  (tap to clear)" else "Mark as verified manually",
                            color = if (isVerified) AuroraColors.Teal else Color.White.copy(alpha = 0.8f),
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

// ============================================================================
// TEXT FORMATTING — *bold*  _italic_  ~strike~  `mono`
// ============================================================================

fun formatMessageText(raw: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var idx = 0
        while (idx < raw.length) {
            val c = raw[idx]
            val style = when (c) {
                '*' -> androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)
                '_' -> androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                '~' -> androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                '`' -> androidx.compose.ui.text.SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                else -> null
            }
            if (style != null) {
                val close = raw.indexOf(c, idx + 1)
                if (close > idx + 1) {
                    pushStyle(style)
                    append(raw.substring(idx + 1, close))
                    pop()
                    idx = close + 1
                    continue
                }
            }
            append(c)
            idx++
        }
    }
}
