package com.graphite.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// ---- omarchy.org token scale, mapped to pure black → gray → white ----
// bg-deep / bg / surface / surface-2 / border-subtle / border-strong / text
val Black = Color(0xFF0A0A0A) // bg-deep
val Surface01 = Color(0xFF111111) // bg
val Surface02 = Color(0xFF171717) // surface
val Surface03 = Color(0xFF1E1E1E) // surface-2
val Surface04 = Color(0xFF262626) // border-subtle zone
val Smoke = Color(0xFF737373) // text-muted
val TextSecondary = Color(0xFFA3A3A3) // text-secondary
val Bone = Color(0xFFF5F5F5) // text (white)
val PureWhite = Color(0xFFFFFFFF)
val OutlineDark = Color(0xFF262626) // border-subtle
val BorderStrong = Color(0xFF404040) // border-strong
val ErrorMuted = Color(0xFFB5696C)
val SuccessMuted = Color(0xFF7E9B7F)

/** Pure black scheme — omarchy.org's white theme, inverted to mono dark. */
val BlackColorScheme =
    darkColorScheme(
        primary = Bone,
        onPrimary = Black,
        primaryContainer = Surface03,
        onPrimaryContainer = Bone,
        secondary = TextSecondary,
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
        onSurfaceVariant = TextSecondary,
        surfaceContainer = Surface01,
        surfaceContainerHigh = Surface02,
        surfaceContainerHighest = Surface03,
        outline = BorderStrong,
        outlineVariant = OutlineDark,
        error = ErrorMuted,
        onError = Black,
        scrim = Black,
    )

/** Graphite scheme — one step of elevation up, same mono DNA. */
val GraphiteColorScheme =
    BlackColorScheme.copy(
        background = Surface01,
        surface = Surface01,
        surfaceContainer = Surface02,
        surfaceContainerHigh = Surface03,
        surfaceContainerHighest = Surface04,
    )
