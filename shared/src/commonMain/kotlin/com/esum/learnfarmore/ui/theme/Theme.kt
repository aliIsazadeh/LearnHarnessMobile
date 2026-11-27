package com.esum.learnfarmore.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B5E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4EFDD),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4B635C),
    secondaryContainer = Color(0xFFCDE8DF),
    onSecondaryContainer = Color(0xFF07201A),
    tertiary = Color(0xFF7D5800),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD3C1),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF0F5144),
    onPrimaryContainer = Color(0xFFB4EFDD),
    secondary = Color(0xFFB2CCC3),
    secondaryContainer = Color(0xFF344C45),
    onSecondaryContainer = Color(0xFFCDE8DF),
    tertiary = Color(0xFFFFBA3C),
)

@Composable
fun LearnHarnessTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
