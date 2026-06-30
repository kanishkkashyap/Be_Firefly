package com.firefly.befirefly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.firefly.befirefly.ui.theme.*

// Preset gradient wallpapers
val wallpaperGradients = listOf(
    listOf(Color(0xFF0D0D1A), Color(0xFF1A1A2E), Color(0xFF16213E)),         // Default Dark
    listOf(Color(0xFFFFEBF2), Color(0xFFE1BEE7), Color(0xFFE3F2FD)),         // Ethereal (Default Light)
    listOf(Color(0xFF1A0033), Color(0xFF2D1B69), Color(0xFF11001C)),         // Deep Purple Night
    listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),         // Ocean Deep
    listOf(Color(0xFF141E30), Color(0xFF243B55)),                             // Royal Blue
    listOf(Color(0xFF200122), Color(0xFF6f0000)),                             // Dark Rose
    listOf(Color(0xFF000428), Color(0xFF004e92)),                             // Midnight Blue
    listOf(Color(0xFF1D2B64), Color(0xFFF8CDDA)),                             // Dusk Pink
    listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E)),         // Cosmic Purple
    listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF)),                             // Teal Storm
    listOf(Color(0xFF1F1C2C), Color(0xFF928DAB)),                             // Misty Lavender
    listOf(Color(0xFF0B486B), Color(0xFFF56217)),                             // Sunset Horizon
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperPickerSheet(
    currentGradientIndex: Int?,
    currentImageUri: String?,
    onSelectGradient: (Int) -> Unit,
    onSelectImage: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onSelectImage(uri.toString())
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0D1A),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                "Chat Wallpaper",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.horizontalGradient(
                        listOf(PrimaryPurple, PrimaryBlue)
                    )
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose a background for this chat",
                color = DarkTextSecondary,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(20.dp))

            // Gradient Presets Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 240.dp)
            ) {
                items(wallpaperGradients.size) { index ->
                    val isSelected = currentGradientIndex == index && currentImageUri == null
                    Box(
                        modifier = Modifier
                            .aspectRatio(0.7f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.verticalGradient(wallpaperGradients[index])
                            )
                            .then(
                                if (isSelected) Modifier.border(
                                    2.dp,
                                    Brush.linearGradient(listOf(PrimaryPurple, PrimaryBlue)),
                                    RoundedCornerShape(12.dp)
                                ) else Modifier
                            )
                            .clickable { onSelectGradient(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Surface(
                                shape = CircleShape,
                                color = PrimaryPurple.copy(alpha = 0.8f),
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Choose from Gallery
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .border(
                            1.5.dp,
                            Brush.horizontalGradient(listOf(PrimaryPurple, PrimaryBlue)),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { imagePickerLauncher.launch("image/*") }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(Icons.Default.Image, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Gallery", color = DarkTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Reset to Default
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .border(
                            1.5.dp,
                            Color(0xFF3A3F55),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onReset() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = DarkTextSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset", color = DarkTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
