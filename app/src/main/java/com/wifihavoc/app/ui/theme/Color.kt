package com.wifihavoc.app.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// ===== WifiHavoc — Material 3 palette (seed: electric indigo/violet) =====
// Правильные M3 тональности (neutral/primary/secondary/tertiary), не «свои» цвета.

// Primary — electric violet (tone 40)
val Primary40 = Color(0xFF6750A4)
val Primary80 = Color(0xFFD0BCFF)
val PrimaryContainer40 = Color(0xFFEADDFF)
val OnPrimaryContainer40 = Color(0xFF21005D)

// Secondary — soft teal-violet
val Secondary40 = Color(0xFF625B71)
val Secondary80 = Color(0xFFCCC2DC)
val SecondaryContainer40 = Color(0xFFE8DEF8)

// Tertiary — cyan accent
val Tertiary40 = Color(0xFF007B8F)
val Tertiary80 = Color(0xFF4DD0E1)

// Surface / neutral — cool grey
val SurfaceNeutral98 = Color(0xFFFFFBFE)
val SurfaceNeutral10 = Color(0xFF1C1B1F)
val SurfaceVariant80 = Color(0xFFE7E0EC)
val OutlineColor = Color(0xFF79747E)

// Semantic
val Error40 = Color(0xFFB3261E)
val Error80 = Color(0xFFFFB4AB)
val OkGreen = Color(0xFF2E7D32)
val WarnAmber = Color(0xFFF5A623)

// Frosted blur — полупрозрачные поверхности для эффекта стекла
val GlassDark = Color(0xCC1C1B1F)    // 80% тёмная стеклянная поверхность
val GlassLight = Color(0xE6FFFFFF)   // 90% светлое стекло
val ScrimOverlay = Color(0x66000000) // тень-подложка под стекло

fun havocLightColors() = lightColorScheme(
    primary = Primary40,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainer40,
    onPrimaryContainer = OnPrimaryContainer40,
    inversePrimary = Primary80,
    secondary = Secondary40,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainer40,
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Tertiary40,
    onTertiary = Color.White,
    background = SurfaceNeutral98,
    onBackground = SurfaceNeutral10,
    surface = SurfaceNeutral98,
    onSurface = SurfaceNeutral10,
    surfaceVariant = SurfaceVariant80,
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainerLowest = Color.White,
    outline = OutlineColor,
    outlineVariant = Color(0xFFCAC4D0),
    error = Error40,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

fun havocDarkColors() = darkColorScheme(
    primary = Primary80,
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = PrimaryContainer40,
    inversePrimary = Primary40,
    secondary = Secondary80,
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = SecondaryContainer40,
    tertiary = Tertiary80,
    onTertiary = Color(0xFF00363F),
    background = SurfaceNeutral10,
    onBackground = Color(0xFFE6E1E5),
    surface = SurfaceNeutral10,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerHighest = Color(0xFF36343B),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainerLowest = Color(0xFF121316),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    error = Error80,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)
