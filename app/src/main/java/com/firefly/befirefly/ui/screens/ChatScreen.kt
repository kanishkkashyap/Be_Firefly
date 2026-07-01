package com.firefly.befirefly.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.firefly.befirefly.data.CryptoWallet
import com.firefly.befirefly.ui.theme.*
import com.firefly.befirefly.utils.AudioRecorder
import com.firefly.befirefly.utils.AudioPlayer
import java.io.File
import com.firefly.befirefly.ui.components.ChatList
import com.firefly.befirefly.ui.components.ChatWindow

// Data Classes
data class Contact(val id: String, val name: String, val status: String, val imageUrl: String)
data class GroupMemberUi(val id: String, val name: String, val isAdmin: Boolean)
data class StatusUi(val authorName: String, val text: String, val timestamp: Long, val isMine: Boolean, val mediaPath: String? = null, val mediaType: String? = null)
data class Message(
    val id: Long = 0,
    val text: String,
    val isSentByMe: Boolean,
    val imageUri: String? = null,
    val audioPath: String? = null,
    val status: String = "SENT",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "text", // "text", "image", "audio", "file"
    val fileName: String? = null,
    val mediaUri: String? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null,
    val transferProgress: Int? = null,
    val packetId: String? = null,
    val reaction: String? = null,
    val replyToPacketId: String? = null,
    val replyToText: String? = null,
    val replyToSentByMe: Boolean = false,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val isStarred: Boolean = false,
    val isPinned: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    wallet: CryptoWallet,
    username: String,
    profilePictureUri: String?,
    messages: List<Message>,
    chats: List<com.firefly.befirefly.domain.model.ChatPreview>,
    isWifiConnected: Boolean,
    onSendMessage: (String) -> Unit,
    onSendImage: (android.net.Uri) -> Unit,
    onSendFile: (android.net.Uri) -> Unit,
    onSendAudio: (File) -> Unit,
    onContactSelected: (String) -> Unit,
    onAddContact: (String, String) -> Unit, // Name, Key
    onCreateGroup: (String, List<String>) -> Unit,
    onUpdateProfilePicture: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)
    val scope = rememberCoroutineScope()
    var selectedChat by remember { mutableStateOf<com.firefly.befirefly.domain.model.ChatPreview?>(null) }
    
    // Audio State
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    val player = remember { AudioPlayer(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingFile: File? by remember { mutableStateOf(null) }
    
    // Aurora mode is always on

    // Auto-select first chat for demo
    LaunchedEffect(chats) {
        if (chats.isNotEmpty() && selectedChat == null) {
            selectedChat = chats[0]
        }
    }
    
    LaunchedEffect(selectedChat) {
        selectedChat?.let { onContactSelected(it.id) }
    }

    var showProfileSheet by remember { mutableStateOf(false) }
    var showAddContactSheet by remember { mutableStateOf(false) }
    var showCreateGroupSheet by remember { mutableStateOf(false) }

    if (showProfileSheet) {
        com.firefly.befirefly.ui.components.ProfileSheet(
            username = username,
            publicKey = wallet.publicKey,
            profilePictureUri = profilePictureUri,
            onDismiss = { showProfileSheet = false },
            onLogout = onLogout,
            onUpdateProfilePicture = onUpdateProfilePicture
        )
    }

    if (showAddContactSheet) {
        com.firefly.befirefly.ui.components.AddContactSheet(
            myPublicKey = wallet.publicKey,
            myUsername = username,
            suggestedContacts = emptyList(), // ChatScreen doesn't have discoveredUsers yet, need to update if we want to support it here too
            onDismiss = { showAddContactSheet = false },
            onAddContact = { name, key ->
                onAddContact(name, key)
                showAddContactSheet = false
            }
        )
    }
    
    if (showCreateGroupSheet) {
        com.firefly.befirefly.ui.components.CreateGroupSheet(
            contacts = chats,
            onDismiss = { showCreateGroupSheet = false },
            onCreateGroup = { name, members ->
                onCreateGroup(name, members)
                showCreateGroupSheet = false
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = NavyBackground,
                modifier = Modifier.fillMaxWidth(1f) // Full Screen Width
            ) {
                ChatList(
                    chats = chats,
                    profilePictureUri = profilePictureUri,
                    isWifiConnected = isWifiConnected,
                    onChatClick = { id, isGroup -> 
                         selectedChat = chats.find { it.id == id }
                    },
                    onProfileClick = { showProfileSheet = true },
                    onAddContactClick = { showAddContactSheet = true },
                    onCreateGroupClick = { showCreateGroupSheet = true }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent
        ) { innerPadding ->
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
                    onBack = { selectedChat = null },
                    onDeleteMessage = null,
                    onClearChat = null,
                    onExportChat = null,
                    onWallpaperClick = null,
                    connectionState = if (isWifiConnected) 2 else 1,
                    modifier = Modifier.padding(innerPadding)
                )
            } else {
                com.firefly.befirefly.ui.components.BeFireflyAuroraBackground(isAnimated = true) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Select a contact to start chatting", 
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
                }
            }
        }
    }
}

@Preview
@Composable
fun ChatScreenPreview() {
    BeFireflyTheme {
        ChatScreen(
            wallet = CryptoWallet("public_key", java.security.KeyPairGenerator.getInstance("EC").generateKeyPair().private),
            username = "Test User",
            profilePictureUri = null,
            messages = listOf(
                Message(id = 1L, text = "Hello!", isSentByMe = false),
                Message(id = 2L, text = "Hi there!", isSentByMe = true),
                Message(id = 3L, text = "Check this out", isSentByMe = true, imageUri = "https://example.com/image.jpg")
            ),
            chats = listOf(
                com.firefly.befirefly.domain.model.ChatPreview("1", "User 1", "Hello", 1000, false),
                com.firefly.befirefly.domain.model.ChatPreview("2", "Group A", "Hi all", 2000, true)
            ),
            isWifiConnected = true,
            onSendMessage = {},
            onSendImage = {},
            onSendFile = {},
            onSendAudio = {},
            onContactSelected = {},
            onAddContact = { _, _ -> },
            onCreateGroup = { _, _ -> },
            onUpdateProfilePicture = {},
            onLogout = {}
        )
    }
}


