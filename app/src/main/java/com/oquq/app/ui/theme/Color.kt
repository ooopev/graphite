package com.oquq.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// ---- core neutral ramp: black → graphite → smoke → bone ----
val Black = Color(0xFF000000)
val Surface01 = Color(0xFF0A0A0B)
val Surface02 = Color(0xFF101012)
val Surface03 = Color(0xFF17171A)
val Surface04 = Color(0xFF1E1E22)
val Smoke = Color(0xFF8B8B92)
val Bone = Color(0xFFE8E8EA)
val PureWhite = Color(0xFFFFFFFF)
val OutlineDark = Color(0xFF232327)
val ErrorMuted = Color(0xFFB5696C)
val SuccessMuted = Color(0xFF7E9B7F)

/** Pure black AMOLED scheme — the default face of oQuQ. */
val BlackColorScheme =
    darkColorScheme(
        primary = Bone,
        onPrimary = Black,
        primaryContainer = Surface03,
        onPrimaryContainer = Bone,
        secondary = Smoke,
        onSecondary = Black,
        secondaryContainer = Surface03,
        onSecondaryContainer = Bone,
        tertiary = Smoke,
        onTertiary = Black,
        background = Black,
        onBackground = Bone,
        surface = Black,
        onSurface = Bone,
        surfaceVariant = Surface02,
        onSurfaceVariant = Smoke,
        surfaceContainer = Surface01,
        surfaceContainerHigh = Surface02,
        surfaceContainerHighest = Surface03,
        outline = OutlineDark,
        outlineVariant = Surface03,
        error = ErrorMuted,
        onError = Black,
        scrim = Black,
    )

/** Graphite scheme — same DNA with a whisper more elevation. */
val GraphiteColorScheme =
    BlackColorScheme.copy(
        background = Surface01,
        surface = Surface01,
        surfaceContainer = Surface02,
        surfaceContainerHigh = Surface03,
        surfaceContainerHighest = Surface04,
    )
