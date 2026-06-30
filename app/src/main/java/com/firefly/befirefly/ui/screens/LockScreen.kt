package com.firefly.befirefly.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firefly.befirefly.ui.theme.*
import com.firefly.befirefly.ui.components.BeFireflyAuroraBackground
import com.firefly.befirefly.ui.components.AuroraColors

@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    onVerifyPin: (String) -> Boolean,
    isSettingPin: Boolean = false, // true when user is creating a new PIN
    onPinSet: ((String) -> Unit)? = null,
    biometricEnabled: Boolean = false,
    onBiometricRequested: (() -> Unit)? = null
) {
    var enteredPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmPhase by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var shakeError by remember { mutableStateOf(false) }

    // Auto-prompt for biometrics when unlocking (not while setting a new PIN).
    LaunchedEffect(Unit) {
        if (biometricEnabled && !isSettingPin) {
            onBiometricRequested?.invoke()
        }
    }

    BeFireflyAuroraBackground(isAnimated = true) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Lock Icon with gradient glow
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                PrimaryPurple.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1F2833),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = PrimaryPurple,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Title
            Text(
                text = if (isSettingPin) {
                    if (isConfirmPhase) "Confirm Your PIN" else "Create a PIN"
                } else "Enter PIN",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (isSettingPin) {
                    if (isConfirmPhase) "Enter the same PIN again" else "Choose a 4-digit PIN to lock your app"
                } else "Enter your 4-digit PIN to unlock",
                fontSize = 14.sp,
                color = DarkTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // PIN Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val currentPin = if (isConfirmPhase) confirmPin else enteredPin
                repeat(4) { index ->
                    val filled = index < currentPin.length
                    val dotColor by animateColorAsState(
                        targetValue = when {
                            errorMessage != null -> Color(0xFFFF5252)
                            filled -> PrimaryPurple
                            else -> Color(0xFF2A2F45)
                        },
                        animationSpec = tween(200)
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .border(
                                1.5.dp,
                                if (filled) PrimaryBlue.copy(alpha = 0.5f) else Color(0xFF3A3F55),
                                CircleShape
                            )
                    )
                }
            }

            // Error message
            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    errorMessage!!,
                    color = Color(0xFFFF5252),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(40.dp))

            // Number Pad
            val numbers = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "⌫")
            )

            numbers.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(Modifier.size(72.dp))
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = if (key == "⌫") Color(0xFF2A2F45) else Color(0xFF1F2833),
                                modifier = Modifier
                                    .size(72.dp)
                                    .clickable {
                                        errorMessage = null
                                        val currentPinRef = if (isConfirmPhase) confirmPin else enteredPin

                                        if (key == "⌫") {
                                            if (isConfirmPhase) {
                                                if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                                            } else {
                                                if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                            }
                                        } else if (currentPinRef.length < 4) {
                                            if (isConfirmPhase) {
                                                confirmPin += key
                                                if (confirmPin.length == 4) {
                                                    // Check if confirm matches
                                                    if (confirmPin == enteredPin) {
                                                        onPinSet?.invoke(confirmPin)
                                                        onUnlocked()
                                                    } else {
                                                        errorMessage = "PINs don't match. Try again."
                                                        confirmPin = ""
                                                        isConfirmPhase = false
                                                        enteredPin = ""
                                                    }
                                                }
                                            } else {
                                                enteredPin += key
                                                if (enteredPin.length == 4) {
                                                    if (isSettingPin) {
                                                        // Move to confirm phase
                                                        isConfirmPhase = true
                                                    } else {
                                                        // Verify
                                                        if (onVerifyPin(enteredPin)) {
                                                            onUnlocked()
                                                        } else {
                                                            errorMessage = "Wrong PIN. Try again."
                                                            enteredPin = ""
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (key == "⌫") {
                                        Icon(
                                            Icons.Default.Backspace,
                                            contentDescription = "Delete",
                                            tint = DarkTextSecondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        Text(
                                            key,
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = DarkTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Biometric unlock (only while unlocking, and only if enabled + available)
            if (biometricEnabled && !isSettingPin) {
                Spacer(Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(PrimaryPurple.copy(alpha = 0.15f))
                        .clickable { onBiometricRequested?.invoke() }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = "Biometric", tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                    Text("Unlock with biometrics", color = DarkTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
    }
}
