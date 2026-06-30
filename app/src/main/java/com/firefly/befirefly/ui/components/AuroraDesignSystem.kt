package com.firefly.befirefly.ui.components

// ============================================================================
// BE FIREFLY — AURORA DESIGN SYSTEM V2
// Shared components: colors, backgrounds, particles, glass modifiers
// ============================================================================

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.composed
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*

// ============================================================================
// AURORA COLOR PALETTE
// ============================================================================

object AuroraColors {
    val DeepPurple   = Color(0xFF0F0518)
    val MidnightBlue = Color(0xFF0A0E27)
    val TrueBlack    = Color(0xFF000000)
    val Teal         = Color(0xFF00E5FF)
    val SoftPink     = Color(0xFFFF79C6)
    val WarmGolden   = Color(0xFFFFD700)
    val Lavender     = Color(0xFFB388FF)
    val Ember        = Color(0xFF8B4513)
    val Cyan         = Color(0xFF00E5FF)
    val Teal2        = Color(0xFF00BFA5)
}

// ============================================================================
// AURORA EASINGS (from CSS prototype)
// ============================================================================

object AuroraEasings {
    val Bounce = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val Smooth = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
}

// ============================================================================
// NETWORK DATA MODELS
// ============================================================================

enum class NetworkMode(val label: String, val color: Color) {
    MESH_ACTIVE("Mesh Active", Color(0xFF00E5FF)),
    CLOUD_CONNECTED("Cloud Connected", Color(0xFF76FF03)),
    OFFLINE("Offline", Color(0xFFFF5252))
}

enum class ConnectionType(val color: Color) {
    ONLINE(Color(0xFFFFD700)),
    OFFLINE(Color(0xFF8B4513)),
    BLE_MESH(Color(0xFF00E5FF))
}

// ============================================================================
// V2 AURORA BACKGROUND — 3 layers + particles + vignette
// ============================================================================

@Composable
fun BeFireflyAuroraBackground(
    isAnimated: Boolean = true,
    networkMode: NetworkMode = NetworkMode.MESH_ACTIVE,
    content: @Composable BoxScope.() -> Unit
) {
    var targetTouch by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var currentTouch by remember { mutableStateOf(Offset(0.5f, 0.5f)) }

    // 60fps lerp — the "water" feel
    LaunchedEffect(Unit) {
        while (isActive) {
            currentTouch = Offset(
                x = currentTouch.x + (targetTouch.x - currentTouch.x) * 0.05f,
                y = currentTouch.y + (targetTouch.y - currentTouch.y) * 0.05f
            )
            delay(16L)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "aurora")

    val drift1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "layer1"
    )

    val drift2 by infiniteTransition.animateFloat(
        initialValue = 2f * PI.toFloat(),
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "layer2"
    )

    val drift3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(35000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "layer3"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    targetTouch = Offset(
                        x = change.position.x / size.width.toFloat(),
                        y = change.position.y / size.height.toFloat()
                    )
                }
            }
    ) {
        // True black base
        Box(modifier = Modifier.fillMaxSize().background(AuroraColors.TrueBlack))

        if (isAnimated) {
            Canvas(modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { /* GPU-accelerated rendering */ }
            ) {
                val w = size.width
                val h = size.height
                val touch = currentTouch

                // Dynamic mouse/touch interaction offsets
                val ox = (touch.x - 0.5f) * w * 0.08f
                val oy = (touch.y - 0.5f) * h * 0.08f

                // Network-reactive ambiance: dim & calm when offline, vivid when connected.
                val intensity = when (networkMode) {
                    NetworkMode.OFFLINE -> 0.45f
                    NetworkMode.CLOUD_CONNECTED -> 0.9f
                    NetworkMode.MESH_ACTIVE -> 1.0f
                }

                // ── NEURAL GRADIENT FIELD ──
                // Large, soft, luminous color blobs that drift and blend (Screen) like a living
                // mesh gradient — the Google "neural expressive" look. Cheaper than stacked blurs.
                val d1x = sin(drift1) * w * 0.12f + ox
                val d1y = cos(drift1 * 0.8f) * h * 0.10f + oy
                val d2x = cos(drift2) * w * 0.10f - ox * 0.6f
                val d2y = sin(drift2 * 0.9f) * h * 0.09f - oy * 0.6f
                val d3x = sin(drift3 * 1.1f) * w * 0.11f + ox * 0.4f
                val d3y = cos(drift3) * h * 0.08f + oy * 0.4f

                // Teal — top-left → center
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.22f * intensity), Color.Transparent),
                        center = Offset(w * 0.30f + d1x, h * 0.28f + d1y),
                        radius = maxOf(w, h) * 0.85f
                    ),
                    radius = maxOf(w, h) * 0.85f,
                    center = Offset(w * 0.30f + d1x, h * 0.28f + d1y),
                    blendMode = BlendMode.Screen
                )
                // Pink — bottom-right
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFF79C6).copy(alpha = 0.20f * intensity), Color.Transparent),
                        center = Offset(w * 0.78f + d2x, h * 0.72f + d2y),
                        radius = maxOf(w, h) * 0.8f
                    ),
                    radius = maxOf(w, h) * 0.8f,
                    center = Offset(w * 0.78f + d2x, h * 0.72f + d2y),
                    blendMode = BlendMode.Screen
                )
                // Lavender — right, mid
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFB388FF).copy(alpha = 0.16f * intensity), Color.Transparent),
                        center = Offset(w * 0.85f + d3x, h * 0.30f + d3y),
                        radius = maxOf(w, h) * 0.7f
                    ),
                    radius = maxOf(w, h) * 0.7f,
                    center = Offset(w * 0.85f + d3x, h * 0.30f + d3y),
                    blendMode = BlendMode.Screen
                )
                // Gold accent — bottom-left warmth
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFD700).copy(alpha = 0.12f * intensity), Color.Transparent),
                        center = Offset(w * 0.18f + d2x * 0.7f, h * 0.82f + d1y * 0.5f),
                        radius = maxOf(w, h) * 0.6f
                    ),
                    radius = maxOf(w, h) * 0.6f,
                    center = Offset(w * 0.18f + d2x * 0.7f, h * 0.82f + d1y * 0.5f),
                    blendMode = BlendMode.Screen
                )

                // ── VIGNETTE — soft edge darkening to frame the gradient field ──
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f)),
                        center = Offset(w / 2f, h / 2f),
                        radius = maxOf(w, h) * 0.65f
                    )
                )
            }

            // Preserving precise floating particles animation layer
        } else {
            // Dark Mode (Starry Night) - pure black background
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }
        
        // Stars/Fireflies are ALWAYS visible, even in dark mode
        FloatingParticles(isAnimated = isAnimated, networkMode = networkMode)

        content()
    }
}

// ============================================================================
// FLOATING PARTICLES
// ============================================================================

@Composable
fun FloatingParticles(isAnimated: Boolean = true, networkMode: NetworkMode = NetworkMode.MESH_ACTIVE) {
    val colors = listOf(
        Color(0xFF00E5FF), Color(0xFFFF79C6), Color(0xFFFFD700),
        Color(0xFFFF6B35), Color(0xFF00BFA5), Color(0xFFB388FF)
    )

    // Fireflies signal more brightly when the network is alive; they go quiet when offline.
    val netFactor = when (networkMode) {
        NetworkMode.OFFLINE -> 0.4f
        NetworkMode.CLOUD_CONNECTED -> 0.95f
        NetworkMode.MESH_ACTIVE -> 1.15f
    }

    // Sparse particle distribution matching prototype
    val particles = remember {
        (0..22).map { i ->
            Triple(
                (i * 43 % 100) / 100f,
                (i * 67 % 100) / 100f,
                colors[i % colors.size]
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    // A normalized 0→1 phase. Each particle moves a WHOLE number of cycles over this phase,
    // so the value at phase=1 is identical to phase=0 → the loop is seamless (no visible "restart").
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(45000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "global_phase"
    )

    Canvas(modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { /* GPU-accelerated rendering */ }
    ) {
        val w = size.width
        val h = size.height
        val twoPi = 2f * PI.toFloat()

        particles.forEachIndexed { index, (x, y, color) ->
            // Whole-cycle counts per particle (1–3). Because they're integers, sin/cos return to
            // their exact starting value when phase wraps 0→1, so there's no snap.
            val cyclesX = 1 + (index % 3)
            val cyclesY = 1 + (index % 2)
            val cyclesA = 1 + (index % 2)
            // Constant per-particle phase offset (does NOT scale with phase, so it can't break the loop).
            val seed = index * 0.7f

            val offsetX = sin(twoPi * cyclesX * phase + seed) * w * 0.025f
            val offsetY = cos(twoPi * cyclesY * phase + seed) * h * 0.035f

            // Gentle pulsing — also a whole number of cycles.
            val alphaPulse = (sin(twoPi * cyclesA * phase + seed) + 1f) / 2f

            val px = x * w + offsetX
            val py = y * h + offsetY

            if (isAnimated) {
                // Aurora mode: small dots with subtle glow
                val radius = if (index % 5 == 0) 2.5f else if (index % 3 == 0) 1.8f else 1.2f
                val alpha = ((0.25f + alphaPulse * 0.35f) * netFactor).coerceAtMost(1f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha * 0.6f), Color.Transparent),
                        center = Offset(px, py),
                        radius = radius * 3f
                    ),
                    radius = radius * 3f,
                    center = Offset(px, py)
                )
                drawCircle(color = color.copy(alpha = alpha), radius = radius, center = Offset(px, py))
            } else {
                // Dark mode: tiny crisp pinpoint dots, NO glow — matches screenshot exactly
                val radius = if (index % 5 == 0) 2.2f else if (index % 3 == 0) 1.5f else 1.0f
                val alpha = ((0.6f + alphaPulse * 0.4f) * netFactor).coerceAtMost(1f)  // bright, crisp dots
                
                drawCircle(color = color.copy(alpha = alpha), radius = radius, center = Offset(px, py))
            }
        }
    }
}

// ============================================================================
// AURORA GRADIENT TEXT
// ============================================================================

@Composable
fun AuroraGradientText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 34.sp,
    fontWeight: FontWeight = FontWeight.ExtraBold
) {
    val infiniteTransition = rememberInfiniteTransition(label = "title_gradient")

    // One full gradient period. We animate the start by exactly this much and tile with
    // Repeated, so the loop is perfectly seamless (no visible jump on restart).
    val period = 1000f
    val shift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = period,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradient_shift"
    )

    // First and last colors match (Teal) so the repeating tile is continuous.
    val gradientColors = listOf(
        AuroraColors.Teal, AuroraColors.SoftPink, AuroraColors.WarmGolden,
        AuroraColors.Cyan, AuroraColors.Lavender, AuroraColors.Teal
    )

    val brush = Brush.linearGradient(
        colors = gradientColors,
        start = Offset(shift, 0f),
        end = Offset(shift + period, 0f),
        tileMode = TileMode.Repeated
    )

    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            brush = brush,
            fontSize = fontSize,
            fontWeight = fontWeight,
            letterSpacing = (-0.8).sp,
            // Soft bioluminescent halo — the "living light" glow from the web prototype.
            shadow = Shadow(
                color = AuroraColors.Teal.copy(alpha = 0.35f),
                offset = Offset.Zero,
                blurRadius = 24f
            )
        )
    )
}

// ============================================================================
// NETWORK DOT — expanding ring style
// ============================================================================

@Composable
fun NetworkDot(networkMode: NetworkMode) {
    val infiniteTransition = rememberInfiniteTransition(label = "net_dot")

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot_pulse"
    )
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "ring_scale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "ring_alpha"
    )

    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.size(10.dp)
                .graphicsLayer { scaleX = ringScale; scaleY = ringScale; alpha = ringAlpha }
                .background(Color.Transparent, CircleShape)
                .border(1.5.dp, networkMode.color, CircleShape)
        )
        Box(
            modifier = Modifier.size(10.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .background(networkMode.color, CircleShape)
        )
    }

    Spacer(modifier = Modifier.width(8.dp))

    Text(
        text = networkMode.label,
        color = networkMode.color.copy(alpha = 0.9f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.5.sp
    )
}

// ============================================================================
// PULSING STATUS INDICATOR — dual ring
// ============================================================================

@Composable
fun PulsingStatus(
    connectionType: ConnectionType,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status")

    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "r1"
    )
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "a1"
    )
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(2500, delayMillis = 800, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "r2"
    )
    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2500, delayMillis = 800, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "a2"
    )

    Box(modifier = modifier.size(size * 2.2f), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(size).graphicsLayer { scaleX = ring1Scale; scaleY = ring1Scale; alpha = ring1Alpha }.border(2.dp, connectionType.color, CircleShape))
        Box(modifier = Modifier.size(size).graphicsLayer { scaleX = ring2Scale; scaleY = ring2Scale; alpha = ring2Alpha }.border(1.5.dp, connectionType.color, CircleShape))
        Box(modifier = Modifier.size(size).background(connectionType.color, CircleShape).border(2.5.dp, Color(0xFF0A0518), CircleShape))
    }
}

// ============================================================================
// GLASS MODIFIER
// ============================================================================

fun Modifier.glassCard(
    cornerRadius: Dp = 20.dp,
    alpha: Float = 0.04f
): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)
    this
        .background(
            // Faint cool→warm tint across the diagonal so the card looks like it's catching
            // the aurora light, instead of sitting on flat gray. This fakes the backdrop-blur
            // "bleed-through" from the web prototype at near-zero GPU cost. Kept very low-alpha.
            brush = Brush.linearGradient(
                colors = listOf(
                    AuroraColors.Teal.copy(alpha = alpha * 0.85f),     // cool, top-left
                    Color.White.copy(alpha = alpha),                   // neutral, center
                    AuroraColors.SoftPink.copy(alpha = alpha * 0.6f)   // warm, bottom-right
                ),
                start = Offset.Zero,
                end = Offset.Infinite
            ),
            shape = shape
        )
        .border(
            // Edge highlight: brightest at the top-left (where light hits), fading to a
            // teal whisper at the bottom-right — like a real glass bevel.
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.16f),
                    Color.White.copy(alpha = 0.04f),
                    AuroraColors.Teal.copy(alpha = 0.06f)
                ),
                start = Offset.Zero,
                end = Offset.Infinite
            ),
            shape = shape
        )
}

// ============================================================================
// GLASS ACTION BUTTONS — animated border style
// ============================================================================

@Composable
fun BeFireflyActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btn"
    )

    Box(
        modifier = modifier
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glassCard(cornerRadius = 20.dp, alpha = 0.05f)
            .border(1.2.dp, Brush.sweepGradient(colors = gradientColors + gradientColors.first()), RoundedCornerShape(20.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
            Text(text = label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
        }
    }
}

// ============================================================================
// ANIMATED FIREFLY LOGO (migrated from AnimatedAuroraBackground.kt)
// ============================================================================

@Composable
fun AnimatedFireflyLogo(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "firefly")

    val flap by transition.animateFloat(
        initialValue = -0.5f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(150, easing = LinearEasing), RepeatMode.Reverse),
        label = "flap"
    )
    val floatY by transition.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "float"
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.2f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )

    val bodyColor = Color.White
    val glowColor = Color(0xFF00C9FF)
    val wingColor = Color(0xFF647DEE).copy(alpha = 0.5f)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(120.dp).offset(y = floatY.dp)) {
            val center = Offset(size.width / 2, size.height / 2)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = glowAlpha), Color.Transparent),
                    center = center + Offset(0f, 20f),
                    radius = 50f
                ),
                radius = 50f,
                center = center + Offset(0f, 20f)
            )

            drawRoundRect(
                color = bodyColor,
                topLeft = center + Offset(-8f, -20f),
                size = androidx.compose.ui.geometry.Size(16f, 40f),
                cornerRadius = CornerRadius(8f)
            )

            drawCircle(color = bodyColor, radius = 12f, center = center + Offset(0f, -30f))

            withTransform({
                translate(left = center.x, top = center.y - 10f)
                rotate(flap * 45f)
            }) {
                drawOval(color = wingColor, topLeft = Offset(2f, 0f), size = androidx.compose.ui.geometry.Size(40f, 15f))
            }

            withTransform({
                translate(left = center.x, top = center.y - 10f)
                rotate(-flap * 45f)
            }) {
                drawOval(color = wingColor, topLeft = Offset(-42f, 0f), size = androidx.compose.ui.geometry.Size(40f, 15f))
            }
        }
    }
}

// ============================================================================
// THEME TOGGLE BUTTON
// ============================================================================

@Composable
fun ThemeToggleButton(
    isStaticMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFFFFD700).copy(alpha = 0.12f))
            .border(1.5.dp, Color(0xFFFFD700).copy(alpha = 0.25f), CircleShape)
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isStaticMode,
            transitionSpec = {
                fadeIn(tween(300)) + scaleIn(tween(300)) togetherWith
                        fadeOut(tween(300)) + scaleOut(tween(300))
            },
            label = "ThemeIcon"
        ) { targetStatic ->
            if (targetStatic) {
                // LIGHT/STATIC MODE: Solid White Full Moon
                Icon(
                    imageVector = Icons.Default.Brightness1, // Solid circle
                    contentDescription = "Switch to Dark Mode",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                // DARK/ANIMATED MODE: Hollow Yellow Sun
                Icon(
                    imageVector = Icons.Default.LightMode, // Sun with rays
                    contentDescription = "Switch to Light Mode",
                    tint = AuroraColors.WarmGolden,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}