package com.firefly.befirefly.ui.components

// ============================================================================
// COMPATIBILITY WRAPPER
// Old references to AnimatedAuroraBackground now redirect to the new
// BeFireflyAuroraBackground from AuroraDesignSystem.kt
// AnimatedFireflyLogo is also re-exported from AuroraDesignSystem.kt
// ============================================================================

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * @deprecated Use [BeFireflyAuroraBackground] instead.
 * This wrapper exists for backward compatibility.
 */
@Composable
fun AnimatedAuroraBackground(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true
) {
    BeFireflyAuroraBackground(isAnimated = true) {}
}

// AnimatedFireflyLogo is now in AuroraDesignSystem.kt
// and is re-exported from the same package, so no wrapper needed.
