package com.firefly.befirefly.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ProfileSheet(
    username: String,
    publicKey: String,
    profilePictureUri: String?,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onUpdateProfilePicture: (String) -> Unit
) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onUpdateProfilePicture(uri.toString()) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

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
                    .fillMaxWidth(0.92f)
                    .graphicsLayer { scaleX = cardScale; scaleY = cardScale; alpha = cardAlpha }
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                    .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0F0518).copy(alpha = 0.98f), Color(0xFF0A0E27).copy(alpha = 0.98f))
                    ),
                    RoundedCornerShape(28.dp)
                )
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                .padding(20.dp)
        ) {
            Column {
                // Handle bar
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .align(Alignment.CenterHorizontally)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                )

                Spacer(Modifier.height(20.dp))

                // Avatar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Glow
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(AuroraColors.SoftPink.copy(alpha = 0.15f), CircleShape)
                                .blur(16.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(
                                    Brush.linearGradient(listOf(AuroraColors.SoftPink, AuroraColors.Lavender)),
                                    CircleShape
                                )
                                .border(2.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    launcher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (profilePictureUri != null) {
                                coil.compose.AsyncImage(
                                    model = profilePictureUri,
                                    contentDescription = "Profile",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    username.take(1).uppercase(),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Name
                    Text(
                        username,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // ID
                    val shortId = if (publicKey.length > 36) publicKey.substring(36).take(8) else publicKey.take(8)
                    Text(
                        "@$shortId · HS Walker",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Public Key card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(12.dp, 14.dp)
                ) {
                    Text(
                        "YOUR PUBLIC KEY",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "secp256r1 · ${publicKey.take(20)}...${publicKey.takeLast(10)}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Action buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    ProfileAction("✏️", "Edit") { onDismiss() }
                    ProfileAction("🔑", "Keys") {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(publicKey))
                        android.widget.Toast.makeText(context, "Key copied!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    ProfileAction("📤", "Share") {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, "Add me on Be Firefly: $publicKey")
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share Key"))
                    }
                    ProfileAction("🎨", "Theme") { onDismiss() }
                }
            }
            }
        }
    }
}

@Composable
private fun ProfileAction(emoji: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}
