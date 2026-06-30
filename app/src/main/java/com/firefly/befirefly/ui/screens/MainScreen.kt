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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.unit.dp
import java.io.File
import com.firefly.befirefly.data.CryptoWallet
import com.firefly.befirefly.domain.model.ChatPreview
import com.firefly.befirefly.utils.AudioRecorder
import com.firefly.befirefly.utils.AudioPlayer
import androidx.compose.ui.platform.LocalContext

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
    onPinMessage: ((Message) -> Unit)? = null
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
                    modifier = Modifier.fillMaxSize()
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
                        StoriesTab(isAuroraAnimated = isAuroraAnimated)
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
                            onThemeToggle = { }
                        )
                    }
                }
                }

                // Floating nav bar — overlaid directly on aurora, no Scaffold slot
                FloatingBottomBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it },
                    modifier = Modifier.align(Alignment.BottomCenter)
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
fun StoriesTab(isAuroraAnimated: Boolean = true) {
    val stories = listOf(
        MockStory("computer", "C", "📡 Mesh node active in Bandra", "2m", listOf(AuroraColors.Teal, AuroraColors.SoftPink, AuroraColors.WarmGolden), listOf(Color(0xFFFF6E40), Color(0xFFFFAB40))),
        MockStory("mere", "M", "🎵 New drop incoming 🔥", "18m", listOf(AuroraColors.Lavender, AuroraColors.SoftPink, AuroraColors.WarmGolden), listOf(Color(0xFFEA80FC), Color(0xFFCE93D8))),
        MockStory("holla", "H", "📸 IIT Campus today", "1h", listOf(AuroraColors.WarmGolden, Color(0xFFFF6B35)), listOf(Color(0xFF7C4DFF), Color(0xFFB388FF)))
    )

    val context = LocalContext.current

    Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Column(modifier = Modifier.padding(top = 24.dp, start = 20.dp, end = 20.dp)) {
                Text(
                    text = "Stories",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AuroraColors.SoftPink,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Encrypted · 24h",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 3.dp, bottom = 16.dp)
                )
            }

            // Add Story Clickable Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(AuroraColors.SoftPink.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .border(1.dp, AuroraColors.SoftPink.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .clickable {
                        android.widget.Toast.makeText(context, "Story engine initialized (Offline Relaying)", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "✨ Add Your Story",
                    color = AuroraColors.SoftPink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(8.dp))

            // Story List
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 100.dp)
            ) {
                items(stories.size) { index ->
                    val story = stories[index]

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(cornerRadius = 16.dp, alpha = 0.03f)
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                            .clickable {
                                android.widget.Toast.makeText(context, "Viewing ${story.name}'s encrypted status update", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Story Outer Multi-color Ring
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Brush.linearGradient(story.ringColors), CircleShape)
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Inner Spacer to separate ring from avatar
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black, CircleShape)
                                        .padding(2.5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Gradient Avatar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Brush.linearGradient(story.avatarColors), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = story.initial,
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            // Story Info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = story.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = story.preview,
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            // Timestamp
                            Text(
                                text = story.time,
                                color = Color.White.copy(alpha = 0.25f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }

// SettingsTab is now in SettingsTab.kt.