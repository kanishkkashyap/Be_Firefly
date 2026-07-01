package com.firefly.befirefly.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.firefly.befirefly.ui.components.AuroraColors
import com.firefly.befirefly.ui.components.glassCard
import com.firefly.befirefly.ui.components.*
import dev.chrisbanes.haze.hazeChild
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Per-tab accent colors matching HTML prototype
private fun tabColor(tab: MainTab): Color = when (tab) {
    MainTab.CHATS -> AuroraColors.Teal
    MainTab.CONTACTS -> Color(0xFFFF79C6) // Pink
    MainTab.STORIES -> Color(0xFFFFD700) // Gold
    MainTab.SETTINGS -> AuroraColors.Teal
}

@Composable
fun FloatingBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: dev.chrisbanes.haze.HazeState? = null
) {
    val tabs = MainTab.values()

    var barVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { barVisible = true }
    val barTranslation by animateFloatAsState(
        targetValue = if (barVisible) 0f else 100f,
        animationSpec = tween(durationMillis = 600, easing = AuroraEasings.Bounce),
        label = "bar_enter"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
            .graphicsLayer { translationY = barTranslation },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Nav pill — frosted glass with shadow
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .height(68.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(34.dp),
                    ambientColor = Color.Black.copy(alpha = 0.3f),
                    spotColor = Color.Black.copy(alpha = 0.3f)
                )
                .clip(RoundedCornerShape(34.dp))
                // Real backdrop blur: sample + blur whatever is scrolling behind the bar.
                // Falls back to a subtle translucent glass if no haze source is provided.
                .then(
                    if (hazeState != null) {
                        Modifier.hazeChild(state = hazeState, shape = RoundedCornerShape(34.dp))
                    } else {
                        Modifier.background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.07f),
                                    Color.White.copy(alpha = 0.02f)
                                )
                            ),
                            shape = RoundedCornerShape(34.dp)
                        )
                    }
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(34.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                tabs.forEach { tab ->
                    AuroraTabItem(
                        tab = tab,
                        isSelected = currentTab == tab,
                        accentColor = tabColor(tab),
                        onClick = { onTabSelected(tab) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))


    }
}

@Composable
fun AuroraTabItem(
    tab: MainTab,
    isSelected: Boolean,
    accentColor: Color = AuroraColors.Teal,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.82f
            isSelected -> 1.2f
            else -> 1.0f
        },
        animationSpec = tween(durationMillis = 400, easing = AuroraEasings.Bounce),
        label = "tab_scale"
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.28f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "tab_alpha"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.35f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "tab_glow"
    )

    // Animated bar width
    val barWidth by animateDpAsState(
        targetValue = if (isSelected) 24.dp else 0.dp,
        animationSpec = tween(durationMillis = 400, easing = AuroraEasings.Bounce),
        label = "bar_w"
    )
    val barAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(200), label = "bar_a"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Glow
            if (glowAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer { alpha = glowAlpha }
                        .background(
                            Brush.radialGradient(listOf(accentColor.copy(alpha = 0.5f), Color.Transparent)),
                            CircleShape
                        )
                        .blur(10.dp)
                )
            }
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.title,
                tint = if (isSelected) accentColor else Color.White.copy(alpha = iconAlpha),
                modifier = Modifier.size(23.dp)
            )
        }

        Spacer(Modifier.height(4.dp))

        // Dot indicator
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(3.dp)
                .graphicsLayer { alpha = barAlpha }
                .background(accentColor, RoundedCornerShape(2.dp))
        )
    }
}
