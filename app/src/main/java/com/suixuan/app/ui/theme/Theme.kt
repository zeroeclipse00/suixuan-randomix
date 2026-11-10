package com.suixuan.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF275C45), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8D9), onPrimaryContainer = Color(0xFF173B2C),
    secondary = Color(0xFF9A512A), secondaryContainer = Color(0xFFFFDCC3),
    background = Color(0xFFF7F5F0), surface = Color(0xFFF7F5F0),
    surfaceContainer = Color(0xFFECEDE4), surfaceVariant = Color(0xFFE5E8DD),
    onSurface = Color(0xFF202C25), onSurfaceVariant = Color(0xFF59665D),
    outline = Color(0xFF788779), outlineVariant = Color(0xFFCDD4C8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9D5B7), primaryContainer = Color(0xFF275C45),
    secondary = Color(0xFFF3B38A), background = Color(0xFF171D19),
    surface = Color(0xFF171D19), surfaceContainer = Color(0xFF263029),
    surfaceVariant = Color(0xFF354037), onSurface = Color(0xFFE1E8DE),
)

@Composable
fun SuixuanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
