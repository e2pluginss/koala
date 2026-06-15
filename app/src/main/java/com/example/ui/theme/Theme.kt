package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB3261E),      // Elegant Dark warm red accent
    secondary = Color(0xFFFF5252),    // glowing secondary warm red / slices
    tertiary = Color(0xFF49454F),     // medium dark control plates/sliders
    background = Color(0xFF121212),   // deep elegant dark body
    surface = Color(0xFF1C1B1F),      // dark surface panel / header/nav
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFE6E1E5), // main warm text
    onSurface = Color(0xFFCAC4D0)     // unselected or lighter text
)

private val LightColorScheme = DarkColorScheme // Samplers are exclusively professional Dark hardware!

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force Dark theme for instrument workspace aesthetic
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve physical hardware visuals
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
