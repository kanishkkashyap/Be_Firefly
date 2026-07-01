package com.firefly.befirefly.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.firefly.befirefly.ui.theme.DarkTextPrimary
import com.firefly.befirefly.ui.theme.DarkTextSecondary
import com.firefly.befirefly.ui.components.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.io.File
import com.firefly.befirefly.data.CryptoWallet
import com.firefly.befirefly.domain.model.ChatPreview
import com.firefly.befirefly.utils.AudioRecorder
import com.firefly.befirefly.utils.AudioPlayer
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

enum class MainTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    CHATS("Chats", Icons.Default.Chat),
    CONTACTS("Contacts", Icons.Default.Contacts),
    STORIES("Stories", Icons.Default.Star),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun MainScreen(
    wallet: CryptoWallet,
    username: String,
    profilePictureUri: String?,
    messages: List<Message>,
    chats: List<ChatPreview>,
    contacts: List<ChatPreview>,
    discoveredUsers: List<ChatPreview>,
    isWifiConnected: Boolean,
    onSendMessage: (String) -> Unit,
    onSendImage: (android.net.Uri) -> Unit,
    onSendFile: (android.net.Uri) -> Unit,
    onSendAudio: (File) -> Unit,
    onContactSelected: (String) -> Unit,
    onAddContact: (String, String) -> Unit,
    onEditContact: (String, String) -> Unit, // id, newName
    onDeleteContact: (String) -> Unit,
    onCreateGroup: (String, List<String>) -> Unit,
    onUpdateProfilePicture: (String) -> Unit,
    onLogout: () -> Unit,
    onResetIdentity: () -> Unit,
    onTestCloud: () -> Unit,
    onDeleteMessage: ((Long) -> Unit)? = null,
    onClearChat: (() -> Unit)? = null,
    onExportChat: ( ((android.net.Uri) -> Unit) -> Unit )? = null,
    onPinChat: ((String) -> Unit)? = null,
    onWallpaperGradient: ((String, Int) -> Unit)? = null,
    onWallpaperImage: ((String, String) -> Unit)? = null,
    onWallpaperReset: ((String) -> Unit)? = null,
    wallpaperGradientIndex: Int? = null,
    wallpaperImageUri: String? = null,
    isAdvertising: Boolean,
    isDiscovering: Boolean,
    peerCount: Int,
    isCloudConnected: Boolean,
    lastError: String?,
    replyingTo: Message? = null,
    typingFrom: String? = null,
    searchResults: List<Message> = emptyList(),
    onReact: ((Message, String) -> Unit)? = null,
    onReply: ((Message) -> Unit)? = null,
    onCancelReply: (() -> Unit)? = null,
    onForward: ((Message, String) -> Unit)? = null,
    onEditMessage: ((Message, String) -> Unit)? = null,
    onDeleteForEveryone: ((Message) -> Unit)? = null,
    onTyping: (() -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
    onClearSearch: (() -> Unit)? = null,
    disappearingSeconds: Long = 0L,
    onSetDisappearingTimer: ((String, Long) -> Unit)? = null,
    isContactVerified: Boolean = false,
    safetyNumber: String = "",
    onVerifyContact: ((String, Boolean) -> Unit)? = null,
    isChatMuted: Boolean = false,
    onToggleMute: ((String) -> Unit)? = null,
    initialDraft: String = "",
    onDraftChanged: ((String) -> Unit)? = null,
    onStarMessage: ((Message) -> Unit)? = null,
    onPinMessage: ((Message) -> Unit)? = null,
    isGroup: Boolean = false,
    groupMembers: List<GroupMemberUi> = emptyList(),
    groupIsOwner: Boolean = false,
    onRenameGroup: ((String, String) -> Unit)? = null,
    onAddGroupMember: ((String, String) -> Unit)? = null,
    onRemoveGroupMember: ((String, String) -> Unit)? = null,
    onLeaveGroup: ((String) -> Unit)? = null,
    isContactBlocked: Boolean = false,
    onToggleBlock: ((String) -> Unit)? = null,
    onShareLocation: (() -> Unit)? = null,
    onAddSharedContact: ((String, String) -> Unit)? = null,
    statuses: List<StatusUi> = emptyList(),
    onPostStatus: (String) -> Unit = {},
    onPostStatusMedia: (android.net.Uri, Boolean) -> Unit = { _, _ -> },
    onStartCall: ((String, Boolean) -> Unit)? = null,
    onExportBackup: ((java.io.OutputStream, String, (Boolean, String) -> Unit) -> Unit)? = null,
    onImportBackup: ((java.io.InputStream, String, (Boolean, String) -> Unit) -> Unit)? = null
) {
    var currentTab by remember { mutableStateOf(MainTab.CHATS) }
    var selectedChat by remember { mutableStateOf<ChatPreview?>(null) }

    // Compute network mode for aurora UI
    val networkMode = when {
        isCloudConnected -> NetworkMode.CLOUD_CONNECTED
        peerCount > 0 -> NetworkMode.MESH_ACTIVE
        else -> NetworkMode.OFFLINE
    }

    var showAddContactSheet by remember { mutableStateOf(false) }
    var showCreateGroupSheet by remember { mutableStateOf(false) }
    var showWallpaperPicker by remember { mutableStateOf(false) }
    var isAuroraAnimated by remember { mutableStateOf(true) }

    // Backdrop-blur source for the floating nav bar (real glass blur via Haze).
    val hazeState = remember { HazeState() }

    // Audio State
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    val player = remember { AudioPlayer(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingFile: File? by remember { mutableStateOf(null) }

    // If a chat is selected, show ChatWindow (Full Screen overlay logic)
    if (selectedChat != null) {
        ChatWindow(
            chatName = selectedChat!!.name,
            chatAvatar = selectedChat!!.avatarUri,
            messages = messages,
            isDarkTheme = true,
            onSendMessage = onSendMessage,
            onSendImage = onSendImage,
            onSendFile = onSendFile,
            onRecordStart = {
                val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                recordingFile = file
                recorder.startRecording(file)
                isRecording = true
            },
            onRecordStop = {
                val file = recorder.stopRecording()
                isRecording = false
                if (file != null && file.exists()) {
                    onSendAudio(file)
                }
            },
            isRecording = isRecording,
            audioPlayer = player,
            onBack = {
                selectedChat = null
                onContactSelected("")
            },
            onDeleteMessage = onDeleteMessage,
            onClearChat = onClearChat,
            onExportChat = {
                onExportChat?.invoke { uri ->
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Export Chat"))
                }
            },
            onWallpaperClick = { showWallpaperPicker = true },
            wallpaperGradientIndex = wallpaperGradientIndex,
            wallpaperImageUri = wallpaperImageUri,
            connectionState = when {
                isCloudConnected -> 2
                peerCount > 0 -> 1
                else -> 0
            },
            contacts = contacts,
            replyingTo = replyingTo,
            remoteIsTyping = (typingFrom != null && typingFrom == selectedChat?.id),
            onReact = onReact,
            onReply = onReply,
            onCancelReply = onCancelReply,
            onForward = onForward,
            onEditMessage = onEditMessage,
            onDeleteForEveryone = onDeleteForEveryone,
            onTyping = onTyping,
            onSearch = onSearch,
            onClearSearch = onClearSearch,
            searchResults = searchResults,
            disappearingSeconds = disappearingSeconds,
            onSetDisappearing = { secs -> selectedChat?.let { onSetDisappearingTimer?.invoke(it.id, secs) } },
            isVerified = isContactVerified,
            myPublicKey = wallet.publicKey,
            contactPublicKey = selectedChat?.id ?: "",
            safetyNumber = safetyNumber,
            onSetVerified = { v -> selectedChat?.let { onVerifyContact?.invoke(it.id, v) } },
            isMuted = isChatMuted,
            onToggleMute = { selectedChat?.let { onToggleMute?.invoke(it.id) } },
            initialDraft = initialDraft,
            onDraftChanged = { text -> onDraftChanged?.invoke(text) },
            onStar = { msg -> onStarMessage?.invoke(msg) },
            onPin = { msg -> onPinMessage?.invoke(msg) },
            isGroup = isGroup,
            groupMembers = groupMembers,
            groupIsOwner = groupIsOwner,
            onRenameGroup = { name -> selectedChat?.let { onRenameGroup?.invoke(it.id, name) } },
            onAddMember = { uid -> selectedChat?.let { onAddGroupMember?.invoke(it.id, uid) } },
            onRemoveMember = { uid -> selectedChat?.let { onRemoveGroupMember?.invoke(it.id, uid) } },
            onLeaveGroup = { selectedChat?.let { onLeaveGroup?.invoke(it.id) } },
            isBlocked = isContactBlocked,
            onToggleBlock = { selectedChat?.let { onToggleBlock?.invoke(it.id) } },
            onShareLocation = onShareLocation,
            onAddSharedContact = { name, key -> onAddSharedContact?.invoke(name, key) },
            onStartVoiceCall = { selectedChat?.let { onStartCall?.invoke(it.id, false) } },
            onStartVideoCall = { selectedChat?.let { onStartCall?.invoke(it.id, true) } },
            modifier = Modifier // Fill
        )
        // Back handler to clear selectedChat
        androidx.activity.compose.BackHandler {
            selectedChat = null
            onContactSelected("") // Clear in VM
        }
    } else {
        BeFireflyAuroraBackground(isAnimated = isAuroraAnimated, networkMode = networkMode) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Screen content
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        val slideDirection = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(
                            animationSpec = tween(500, easing = AuroraEasings.Smooth),
                            initialOffsetX = { fullWidth -> slideDirection * fullWidth }
                        ) + fadeIn(animationSpec = tween(400))) togetherWith (slideOutHorizontally(
                            animationSpec = tween(500, easing = AuroraEasings.Smooth),
                            targetOffsetX = { fullWidth -> -slideDirection * fullWidth }
                        ) + fadeOut(animationSpec = tween(400)))
                    },
                    label = "tab_transition",
                    modifier = Modifier.fillMaxSize().haze(
                        state = hazeState,
                        backgroundColor = Color(0xFF0A0518),
                        tint = Color.White.copy(alpha = 0.04f),
                        blurRadius = 24.dp
                    )
                ) { tab ->
                    when (tab) {
                    MainTab.CHATS -> {
                        ChatList(
                            chats = chats,
                            profilePictureUri = profilePictureUri,
                            isWifiConnected = isWifiConnected,
                            onChatClick = { id, _ ->
                                selectedChat = chats.find { it.id == id }
                                onContactSelected(id)
                            },
                            onProfileClick = { currentTab = MainTab.SETTINGS },
                            onAddContactClick = { showAddContactSheet = true },
                            onCreateGroupClick = { showCreateGroupSheet = true },
                            onEditContact = { id, newName -> onEditContact(id, newName) },
                            onDeleteContact = { id -> onDeleteContact(id) },
                            onPinChat = { id -> onPinChat?.invoke(id) },
                            networkMode = networkMode,
                            isAuroraAnimated = isAuroraAnimated,
                            onThemeToggle = { isAuroraAnimated = !isAuroraAnimated }
                        )
                    }
                    MainTab.CONTACTS -> {
                        ContactsTab(contacts, isAuroraAnimated = isAuroraAnimated)
                    }
                    MainTab.STORIES -> {
                        StoriesTab(statuses = statuses, onPostStatus = onPostStatus, onPostStatusMedia = onPostStatusMedia, isAuroraAnimated = isAuroraAnimated)
                    }
                    MainTab.SETTINGS -> {
                        SettingsTab(
                            username = username,
                            publicKey = wallet.publicKey,
                            profilePictureUri = profilePictureUri,
                            onUpdateProfilePicture = onUpdateProfilePicture,
                            onLogout = onLogout,
                            onResetIdentity = onResetIdentity,
                            onTestCloud = onTestCloud,
                            isAdvertising = isAdvertising,
                            isDiscovering = isDiscovering,
                            peerCount = peerCount,
                            isCloudConnected = isCloudConnected,
                            lastError = lastError,
                            isDarkTheme = true,
                            onThemeToggle = { },
                            onExportBackup = onExportBackup,
                            onImportBackup = onImportBackup
                        )
                    }
                }
                }

                // Floating nav bar — overlaid directly on aurora, no Scaffold slot
                FloatingBottomBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    hazeState = hazeState
                )

                if (showAddContactSheet) {
                    com.firefly.befirefly.ui.components.AddContactSheet(
                        myPublicKey = wallet.publicKey,
                        myUsername = username,
                        suggestedContacts = discoveredUsers,
                        onDismiss = { showAddContactSheet = false },
                        onAddContact = { name, key ->
                            onAddContact(name, key)
                            showAddContactSheet = false
                        }
                    )
                }

                if (showCreateGroupSheet) {
                    com.firefly.befirefly.ui.components.CreateGroupSheet(
                        contacts = contacts,
                        onDismiss = { showCreateGroupSheet = false },
                        onCreateGroup = { name, members ->
                            onCreateGroup(name, members)
                            showCreateGroupSheet = false
                        }
                    )
                }

                if (showWallpaperPicker && selectedChat != null) {
                    com.firefly.befirefly.ui.components.WallpaperPickerSheet(
                        currentGradientIndex = wallpaperGradientIndex,
                        currentImageUri = wallpaperImageUri,
                        onSelectGradient = { index ->
                            onWallpaperGradient?.invoke(selectedChat!!.id, index)
                        },
                        onSelectImage = { uri ->
                            onWallpaperImage?.invoke(selectedChat!!.id, uri)
                        },
                        onReset = {
                            onWallpaperReset?.invoke(selectedChat!!.id)
                        },
                        onDismiss = { showWallpaperPicker = false }
                    )
                }
            }
        }
    }
}

@Composable
fun ContactsTab(
    contacts: List<ChatPreview>,
    onEditContact: ((String, String) -> Unit)? = null,
    onDeleteContact: ((String) -> Unit)? = null,
    isAuroraAnimated: Boolean = true
) {
    val avatarGradients = listOf(
        listOf(Color(0xFF7F53AC), Color(0xFF647DEE)),
        listOf(Color(0xFFFF6B6B), Color(0xFFFF8E53)),
        listOf(Color(0xFF00C9FF), Color(0xFF92FE9D)),
        listOf(Color(0xFFF093FB), Color(0xFFF5576C)),
        listOf(Color(0xFF4FACFE), Color(0xFF00F2FE)),
        listOf(Color(0xFFA18CD1), Color(0xFFFBC2EB)),
    )

    Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                text = "Contacts",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AuroraColors.Lavender,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "${contacts.size} contact${if (contacts.size == 1) "" else "s"} · secp256r1",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 3.dp, bottom = 20.dp)
            )

            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedFireflyLogo(modifier = Modifier.size(100.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No contacts found", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(count = contacts.size) { index ->
                        val contact = contacts[index]
                        val gradIndex = contact.name.hashCode().and(0x7FFFFFFF) % avatarGradients.size

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(cornerRadius = 14.dp, alpha = 0.03f)
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                                .clickable { }
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(Brush.linearGradient(avatarGradients[gradIndex]), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = contact.name.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "...${contact.id.takeLast(12)}",
                                        color = Color.White.copy(alpha = 0.25f),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.15f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

data class MockStory(
    val name: String,
    val initial: String,
    val preview: String,
    val time: String,
    val ringColors: List<Color>,
    val avatarColors: List<Color>
)

@Composable
fun StoriesTab(
    statuses: List<StatusUi> = emptyList(),
    onPostStatus: (String) -> Unit = {},
    onPostStatusMedia: (android.net.Uri, Boolean) -> Unit = { _, _ -> },
    isAuroraAnimated: Boolean = true
) {
    var showComposer by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<StoryGroup?>(null) }
    val timeFmt = remember { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()) }

    // Group statuses by author (they arrive isMine-first, newest-first). Within a group we sort
    // oldest -> newest so the full-screen viewer plays them in order.
    val groups = remember(statuses) {
        statuses.groupBy { it.authorName to it.isMine }
            .map { (k, v) -> StoryGroup(k.first, k.second, v.sortedBy { it.timestamp }) }
            .sortedByDescending { it.isMine }
    }
    val myGroup = groups.firstOrNull { it.isMine }
    val others = groups.filter { !it.isMine }

    if (showComposer) {
        StoryComposer(
            onDismiss = { showComposer = false },
            onPost = { onPostStatus(it); showComposer = false },
            onPostMedia = { uri, isVideo -> onPostStatusMedia(uri, isVideo); showComposer = false }
        )
    }
    viewing?.let { g -> StoryViewer(group = g, onClose = { viewing = null }) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Header
        Column(Modifier.padding(top = 24.dp, start = 20.dp, end = 20.dp, bottom = 4.dp)) {
            Text(
                "Stories", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                color = AuroraColors.SoftPink, letterSpacing = (-0.5).sp
            )
            Text(
                "Encrypted · disappears in 24h",
                color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }

        // Ring carousel — My Story first, then everyone else.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StoryRing(
                name = if (myGroup != null) "Your story" else "Add story",
                gradient = listOf(AuroraColors.SoftPink, AuroraColors.Lavender),
                hasStory = myGroup != null,
                showAdd = true,
                onClick = { if (myGroup != null) viewing = myGroup else showComposer = true },
                onAdd = { showComposer = true }
            )
            others.forEach { g ->
                StoryRing(
                    name = g.authorName,
                    gradient = gradientFor(g.authorName),
                    hasStory = true,
                    showAdd = false,
                    onClick = { viewing = g },
                    onAdd = {}
                )
            }
        }

        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp)
                .background(Color.White.copy(alpha = 0.06f))
        )

        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✨", fontSize = 40.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("No stories yet", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Share a moment — it vanishes in 24h",
                        color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                    Box(
                        Modifier
                            .background(Brush.linearGradient(listOf(AuroraColors.SoftPink, AuroraColors.Lavender)), RoundedCornerShape(30.dp))
                            .clickable { showComposer = true }
                            .padding(horizontal = 28.dp, vertical = 12.dp)
                    ) { Text("Add your story", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                }
            }
        } else {
            Text(
                "RECENT UPDATES",
                color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp)
            ) {
                items(groups) { g ->
                    val latest = g.items.last()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(cornerRadius = 16.dp, alpha = 0.03f)
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                            .clickable { viewing = g }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val grad = if (g.isMine) listOf(AuroraColors.SoftPink, AuroraColors.Lavender) else gradientFor(g.authorName)
                        Box(
                            modifier = Modifier.size(52.dp).background(Brush.sweepGradient(grad + grad.first()), CircleShape).padding(2.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color(0xFF0A0518), CircleShape).padding(2.5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (latest.mediaPath != null) {
                                    coil.compose.AsyncImage(
                                        model = java.io.File(latest.mediaPath!!),
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Brush.linearGradient(grad), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) { Text(g.authorName.take(1).uppercase(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (g.isMine) "Your story" else g.authorName,
                                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                                )
                                if (g.items.size > 1) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("${g.items.size}", color = AuroraColors.SoftPink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                when (latest.mediaType) {
                                    "image" -> "📷 Photo"
                                    "video" -> "🎥 Video"
                                    else -> latest.text
                                },
                                color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Text(timeFmt.format(java.util.Date(latest.timestamp)), color = Color.White.copy(alpha = 0.25f), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** A status author's collected stories. */
data class StoryGroup(val authorName: String, val isMine: Boolean, val items: List<StatusUi>)

private val storyGradients = listOf(
    listOf(Color(0xFF7F00FF), Color(0xFFE100FF)),
    listOf(Color(0xFFFF6E40), Color(0xFFFFAB40)),
    listOf(Color(0xFF00C9FF), Color(0xFF92FE9D)),
    listOf(Color(0xFFFC466B), Color(0xFF3F5EFB)),
    listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
    listOf(Color(0xFFF12711), Color(0xFFF5AF19))
)

private fun gradientFor(name: String): List<Color> =
    storyGradients[Math.abs(name.hashCode()) % storyGradients.size]

@Composable
private fun StoryRing(
    name: String,
    gradient: List<Color>,
    hasStory: Boolean,
    showAdd: Boolean,
    onClick: () -> Unit,
    onAdd: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp).clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(
                        if (hasStory) Brush.sweepGradient(gradient + gradient.first())
                        else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.05f))),
                        CircleShape
                    )
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0A0518), CircleShape).padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Brush.linearGradient(gradient), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text(name.take(1).uppercase(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                }
            }
            if (showAdd) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(AuroraColors.SoftPink, CircleShape)
                        .border(2.dp, Color(0xFF0A0518), CircleShape)
                        .clickable { onAdd() },
                    contentAlignment = Alignment.Center
                ) { Text("+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/** Full-screen, tap-to-advance story viewer with segmented progress bars and 5s auto-advance. */
@Composable
private fun StoryViewer(group: StoryGroup, onClose: () -> Unit) {
    val items = group.items
    var index by remember { mutableStateOf(0) }
    val timeFmt = remember { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        val current = items.getOrNull(index) ?: run { onClose(); return@Dialog }
        val grad = if (group.isMine) listOf(AuroraColors.SoftPink, AuroraColors.Lavender) else gradientFor(group.authorName)
        var progress by remember(index) { mutableStateOf(0f) }
        val isVideo = current.mediaType == "video"
        fun goNext() { if (index < items.size - 1) index++ else onClose() }

        // Text & photos auto-advance after 5s; videos advance when playback finishes.
        LaunchedEffect(index) {
            if (isVideo) return@LaunchedEffect
            progress = 0f
            val total = 5000L; val step = 40L; var e = 0L
            while (e < total) { kotlinx.coroutines.delay(step); e += step; progress = e.toFloat() / total }
            goNext()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(grad))
                .pointerInput(group) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (offset.x < size.width * 0.33f) {
                                if (index > 0) index--
                            } else {
                                if (index < items.size - 1) index++ else onClose()
                            }
                        }
                    )
                }
        ) {
            // Full-screen media layer (behind the scrim/controls).
            if (current.mediaPath != null) {
                if (isVideo) {
                    androidx.compose.runtime.key(index) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                android.widget.VideoView(ctx).apply {
                                    setVideoURI(android.net.Uri.fromFile(java.io.File(current.mediaPath!!)))
                                    setOnPreparedListener { mp -> mp.isLooping = false; start() }
                                    setOnCompletionListener { goNext() }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    coil.compose.AsyncImage(
                        model = java.io.File(current.mediaPath!!),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent, Color.Black.copy(alpha = 0.5f)))
                )
            )

            Column(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp)) {
                // Segmented progress bars
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items.forEachIndexed { i, _ ->
                        val p = when {
                            i < index -> 1f
                            i == index -> progress
                            else -> 0f
                        }
                        Box(
                            Modifier.weight(1f).height(3.dp)
                                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                        ) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(Color.White, RoundedCornerShape(2.dp)))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).background(Brush.linearGradient(grad), CircleShape)
                            .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text(group.authorName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (group.isMine) "Your story" else group.authorName,
                            color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                        )
                        Text(timeFmt.format(java.util.Date(current.timestamp)), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    Text("✕", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable { onClose() }.padding(8.dp))
                }
                // Body
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (current.mediaPath == null) {
                        Text(
                            current.text, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center, lineHeight = 34.sp, modifier = Modifier.padding(24.dp)
                        )
                    } else if (current.text.isNotBlank()) {
                        // Caption overlay for media stories.
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            Text(
                                current.text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Full-screen story composer: text on a gradient, or pick a photo/video to share. */
@Composable
private fun StoryComposer(
    onDismiss: () -> Unit,
    onPost: (String) -> Unit,
    onPostMedia: (android.net.Uri, Boolean) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var gradIndex by remember { mutableStateOf(0) }
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pickedIsVideo by remember { mutableStateOf(false) }
    val grad = storyGradients[gradIndex % storyGradients.size]

    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) { pickedUri = uri; pickedIsVideo = false } }
    val videoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) { pickedUri = uri; pickedIsVideo = true } }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val previewUri = pickedUri
        if (previewUri != null) {
            // Media preview + share.
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if (pickedIsVideo) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                setVideoURI(previewUri)
                                setOnPreparedListener { mp -> mp.isLooping = true; start() }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    coil.compose.AsyncImage(
                        model = previewUri, contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                    Text("✕", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable { pickedUri = null }.padding(6.dp))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().padding(bottom = 24.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .background(Brush.linearGradient(listOf(AuroraColors.SoftPink, AuroraColors.Lavender)), RoundedCornerShape(30.dp))
                                .clickable { onPostMedia(previewUri, pickedIsVideo) }
                                .padding(horizontal = 44.dp, vertical = 14.dp)
                        ) { Text("Share story", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    }
                }
            }
        } else {
            // Text composer.
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(grad))) {
                Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✕", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable { onDismiss() }.padding(6.dp))
                        Text("New story", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Box(
                            Modifier.size(34.dp)
                                .background(Brush.linearGradient(storyGradients[(gradIndex + 1) % storyGradients.size]), CircleShape)
                                .border(2.dp, Color.White, CircleShape)
                                .clickable { gradIndex = (gradIndex + 1) % storyGradients.size }
                        )
                    }

                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center, lineHeight = 36.sp
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.Center) {
                                    if (text.isEmpty()) Text(
                                        "Type something…", color = Color.White.copy(alpha = 0.55f),
                                        fontSize = 28.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                                    )
                                    inner()
                                }
                            }
                        )
                    }

                    // Media + share controls.
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ComposerChip("📷 Photo") { imagePicker.launch("image/*") }
                        ComposerChip("🎥 Video") { videoPicker.launch("video/*") }
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .background(Color.White, RoundedCornerShape(30.dp))
                                .clickable(enabled = text.isNotBlank()) { onPost(text) }
                                .padding(horizontal = 28.dp, vertical = 13.dp)
                        ) { Text("Share", color = grad.first(), fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) { Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

// SettingsTab is now in SettingsTab.kt.