// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Gourav Mahunta

package com.duarf.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duarf.engine.model.AlertLevel

/**
 * "Calm Guardian": indigo on a cool off-white, with teal for protected, amber for caution and
 * red for danger. Caution and danger also differ in lightness and always come with an icon and
 * a word, so level is never told by colour alone (§13.3). The system font is kept on purpose:
 * it covers all 12 UI scripts consistently.
 */
object DuarfColors {
    val Indigo = Color(0xFF2F3A8F)
    val IndigoDark = Color(0xFF1F276B)
    val IndigoSoft = Color(0xFFE6E9F8)
    val Ground = Color(0xFFF3F5FA)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF151A30)
    val InkMuted = Color(0xFF4A5170)
    val Line = Color(0xFFDDE2EE)
    val FieldBorder = Color(0xFF7A819C)

    val Safe = Color(0xFF0B6E62)
    val SafeStrong = Color(0xFF0A4F47)
    val SafeSoft = Color(0xFFDDF2EE)

    val Caution = Color(0xFF8A4B00)
    val CautionStrong = Color(0xFF5C3200)
    val CautionSoft = Color(0xFFFFE3B8)

    val Danger = Color(0xFFB42318)
    val DangerStrong = Color(0xFF8C1D13)
    val DangerSoft = Color(0xFFFDE7E4)
    val DangerMark = Color(0xFFFDE2DE)
}

/** Colours and icon for one alert level. */
data class LevelStyle(
    val strong: Color,
    val onStrong: Color,
    val soft: Color,
    val onSoft: Color,
    val icon: ImageVector
)

fun AlertLevel.style(): LevelStyle = when (this) {
    AlertLevel.DANGER -> LevelStyle(DuarfColors.Danger, Color.White, DuarfColors.DangerSoft, DuarfColors.DangerStrong, Icons.Default.Warning)
    AlertLevel.CAUTION -> LevelStyle(DuarfColors.Caution, Color.White, DuarfColors.CautionSoft, DuarfColors.CautionStrong, Icons.Default.ErrorOutline)
    AlertLevel.NONE -> LevelStyle(DuarfColors.Safe, Color.White, DuarfColors.SafeSoft, DuarfColors.SafeStrong, Icons.Default.CheckCircle)
}

private val LightScheme = lightColorScheme(
    primary = DuarfColors.Indigo,
    onPrimary = Color.White,
    primaryContainer = DuarfColors.IndigoSoft,
    onPrimaryContainer = DuarfColors.IndigoDark,
    secondary = DuarfColors.Safe,
    onSecondary = Color.White,
    secondaryContainer = DuarfColors.SafeSoft,
    onSecondaryContainer = DuarfColors.SafeStrong,
    tertiary = DuarfColors.Caution,
    onTertiary = Color.White,
    tertiaryContainer = DuarfColors.CautionSoft,
    onTertiaryContainer = DuarfColors.CautionStrong,
    error = DuarfColors.Danger,
    onError = Color.White,
    errorContainer = DuarfColors.DangerSoft,
    onErrorContainer = DuarfColors.DangerStrong,
    background = DuarfColors.Ground,
    onBackground = DuarfColors.Ink,
    // Screens and app bars sit on the ground colour; cards, dialogs and fields are white.
    surface = DuarfColors.Ground,
    onSurface = DuarfColors.Ink,
    surfaceVariant = Color(0xFFEDF0F7),
    onSurfaceVariant = DuarfColors.InkMuted,
    surfaceContainerLowest = DuarfColors.Card,
    surfaceContainerLow = DuarfColors.Card,
    surfaceContainer = DuarfColors.Card,
    surfaceContainerHigh = DuarfColors.Card,
    surfaceContainerHighest = DuarfColors.Card,
    outline = DuarfColors.FieldBorder,
    outlineVariant = DuarfColors.Line
)

/** One step larger than Material's defaults: the main users are older or less confident readers. */
private val DuarfTypography = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
)

private val DuarfShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DuarfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        typography = DuarfTypography,
        shapes = DuarfShapes,
        content = content
    )
}
