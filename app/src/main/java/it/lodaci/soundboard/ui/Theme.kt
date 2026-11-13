package it.lodaci.soundboard.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.PaddingValues

private val Colors = darkColorScheme(
    primary = Color(0xFFE0A84E),
    onPrimary = Color(0xFF2A1A00),
    primaryContainer = Color(0xFF5A3F12),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFFB79CF0),
    onSecondary = Color(0xFF261548),
    secondaryContainer = Color(0xFF3D2C63),
    onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = Color(0xFF7FD3B0),
    background = Color(0xFF17131C),
    onBackground = Color(0xFFEAE1EC),
    surface = Color(0xFF17131C),
    onSurface = Color(0xFFEAE1EC),
    surfaceVariant = Color(0xFF2B2433),
    onSurfaceVariant = Color(0xFFCDC2D3),
    surfaceContainer = Color(0xFF221C29),
    surfaceContainerHigh = Color(0xFF2B2433),
    surfaceContainerHighest = Color(0xFF352D3E),
    error = Color(0xFFFF8A80),
)

@Composable
fun SoundboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography(), content = content)
}

/** Scaffold per le singole schermate: gli inset di sistema li gestisce già lo Scaffold esterno. */
@Composable
fun ScreenScaffold(
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        contentWindowInsets = WindowInsets(0),
        content = content,
    )
}

fun contentColorFor(bg: Color): Color = if (bg.luminance() > 0.45f) Color(0xFF1A1A1A) else Color.White

fun formatMs(ms: Long): String {
    if (ms <= 0) return "--:--"
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

