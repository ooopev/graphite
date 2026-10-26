package com.graphite.app.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.graphite.app.R
import com.graphite.app.data.ThemeMode

/** omarchy.org's sans — Geist. */
val Geist =
    FontFamily(
        Font(R.font.geist_regular, FontWeight.Normal),
        Font(R.font.geist_medium, FontWeight.Medium),
        Font(R.font.geist_semibold, FontWeight.SemiBold),
    )

/** omarchy.org's mono — JetBrains Mono. Labels, numbers, status. */
val Mono =
    FontFamily(
        Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
        Font(R.font.jetbrainsmono_medium, FontWeight.Medium),
        Font(R.font.jetbrainsmono_bold, FontWeight.Bold),
    )

/** omarchy.org is square: every corner in the system is 0dp. */
val OmarchyShapes =
    Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(0.dp),
        medium = RoundedCornerShape(0.dp),
        large = RoundedCornerShape(0.dp),
        extraLarge = RoundedCornerShape(0.dp),
    )

/**
 * Graphite is a dark-only app by design: omarchy.org's design language in
 * black / gray / white. Dynamic color (Material You wallpaper) is opt-in.
 */
@Composable
fun GraphiteTheme(
    theme: ThemeMode = ThemeMode.BLACK,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when (theme) {
            ThemeMode.BLACK -> BlackColorScheme
            ThemeMode.GRAY -> GraphiteColorScheme
            ThemeMode.DYNAMIC -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dynamicDarkColorScheme(LocalContext.current)
                } else {
                    GraphiteColorScheme
                }
            }
        }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = GraphiteTypography,
        shapes = OmarchyShapes,
        content = content,
    )
}

val GraphiteTypography =
    Typography(
        displaySmall =
            TextStyle(
                fontFamily = Geist,
                fontWeight = FontWeight.Normal,
                fontSize = 34.sp,
                letterSpacing = (-0.5).sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                letterSpacing = (-0.3).sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                letterSpacing = (-0.3).sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = Geist,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = Geist,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = Geist,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = Geist,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = Mono,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = Mono,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = Mono,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
            ),
    )
