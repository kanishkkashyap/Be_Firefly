package com.firefly.befirefly.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.firefly.befirefly.domain.model.ChatPreview
import com.firefly.befirefly.ui.theme.*

@Composable
fun CreateGroupSheet(
    contacts: List<ChatPreview>,
    onDismiss: () -> Unit,
    onCreateGroup: (String, List<String>) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isMeshOnly by remember { mutableStateOf(false) }
    
    val selectedMembers = remember { mutableStateListOf<String>() }
    val availableContacts = remember(contacts) { contacts.filter { !it.isGroup } }

    val avatarGradients = listOf(
        listOf(Color(0xFFFF6E40), Color(0xFFFFAB40)), // Orange
        listOf(Color(0xFFEA80FC), Color(0xFFCE93D8)), // Pink
        listOf(Color(0xFF7C4DFF), Color(0xFFB388FF)), // Purple
        listOf(Color(0xFF00BFA5), Color(0xFF00E5FF)),
        listOf(Color(0xFFFF79C6), Color(0xFFB388FF))
    )

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
            // Modal Card
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .graphicsLayer { scaleX = cardScale; scaleY = cardScale; alpha = cardAlpha }
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { /* block clicks */ }
            ) {
                // Background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF0C0A14).copy(alpha = 0.98f),
                                    Color(0xFF080C1E).copy(alpha = 0.97f)
                                )
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                )

                Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text("New Group", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("End-to-End Encrypted · Mesh Ready", color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
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

                    Spacer(Modifier.height(20.dp))

                    // Segmented Control
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF10121C), RoundedCornerShape(12.dp))
                            .padding(4.dp)
                    ) {
                        // Encrypted Tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (!isMeshOnly) Brush.linearGradient(listOf(Color(0xFF13364A), Color(0xFF0D253A))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)), RoundedCornerShape(8.dp))
                                .border(1.dp, if (!isMeshOnly) AuroraColors.Teal.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { isMeshOnly = false }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = if (!isMeshOnly) AuroraColors.Teal else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Encrypted", color = if (!isMeshOnly) AuroraColors.Teal else Color.White.copy(alpha = 0.4f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Mesh Only Tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isMeshOnly) Brush.linearGradient(listOf(Color(0xFF2A2A35), Color(0xFF1A1A22))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)), RoundedCornerShape(8.dp))
                                .border(1.dp, if (isMeshOnly) Color.White.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { isMeshOnly = true }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SatelliteAlt, contentDescription = null, tint = if (isMeshOnly) Color.White else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Mesh Only", color = if (isMeshOnly) Color.White else Color.White.copy(alpha = 0.4f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Group Name Input
                    Text("GROUP NAME", color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 6.dp))
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        placeholder = { Text("e.g. IIT Squad", color = Color.White.copy(alpha = 0.2f)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF10121C), unfocusedContainerColor = Color(0xFF10121C),
                            focusedBorderColor = AuroraColors.Teal.copy(alpha = 0.3f), unfocusedBorderColor = Color.White.copy(alpha = 0.05f),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp), singleLine = true
                    )

                    Spacer(Modifier.height(16.dp))

                    // Description Input
                    Text("DESCRIPTION (OPTIONAL)", color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 6.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text("What's this group about?", color = Color.White.copy(alpha = 0.2f)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF10121C), unfocusedContainerColor = Color(0xFF10121C),
                            focusedBorderColor = AuroraColors.Teal.copy(alpha = 0.3f), unfocusedBorderColor = Color.White.copy(alpha = 0.05f),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp), singleLine = true
                    )

                    Spacer(Modifier.height(20.dp))

                    // Member Selection Header
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                        Text("SELECT MEMBERS", color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF3B152B), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFFF79C6).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${selectedMembers.size} selected", color = Color(0xFFFF79C6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Member List
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableContacts.size) { index ->
                            val contact = availableContacts[index]
                            val gradIndex = index % avatarGradients.size
                            val isSelected = selectedMembers.contains(contact.id)
                            
                            // Mocking the status string matching screenshot
                            val statusSubtitle = when (index % 3) {
                                0 -> "Online · Via Cloud"
                                1 -> "Online · P2P Mesh"
                                else -> "Group Chat" // For demonstration if we didn't filter
                            }
                            val statusColor = if (statusSubtitle.contains("Cloud")) AuroraColors.Cyan else if (statusSubtitle.contains("Mesh")) AuroraColors.Teal else Color.White.copy(alpha = 0.4f)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Brush.linearGradient(listOf(Color(0xFF131A2A), Color(0xFF0D1220))), RoundedCornerShape(12.dp))
                                    .border(1.dp, if (isSelected) AuroraColors.Teal.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (isSelected) selectedMembers.remove(contact.id) else selectedMembers.add(contact.id)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Brush.linearGradient(avatarGradients[gradIndex]), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(contact.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                                
                                Spacer(Modifier.width(16.dp))
                                
                                // Details
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(statusSubtitle, color = statusColor, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                                
                                // Selection Circle
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(if (isSelected) AuroraColors.Teal else Color.Transparent, CircleShape)
                                        .border(2.dp, if (isSelected) AuroraColors.Teal else Color.White.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
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

                        val canCreate = groupName.isNotBlank() && selectedMembers.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .weight(1f).height(48.dp)
                                .background(if (canCreate) Brush.linearGradient(listOf(AuroraColors.Teal, Color(0xFF00BFA5))) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))), RoundedCornerShape(12.dp))
                                .clickable(enabled = canCreate) {
                                    onCreateGroup(groupName, selectedMembers.toList())
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) { Text("Create 🔥", color = if (canCreate) Color.Black else Color.White.copy(alpha = 0.3f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}
