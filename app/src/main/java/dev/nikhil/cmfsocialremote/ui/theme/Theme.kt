package dev.nikhil.cmfsocialremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Cmf {
    val Black = Color(0xFF000000)
    val Graphite = Color(0xFF0E0E11)
    val Graphite2 = Color(0xFF17171C)
    val Outline = Color(0xFF2A2A31)
    val Text = Color(0xFFF2F2F5)
    val TextDim = Color(0xFF8B8B96)
    val Orange = Color(0xFFFF5A1F)
    val Violet = Color(0xFF8B5CF6)
    val Pink = Color(0xFFFF3D81)
    val Green = Color(0xFF2EE6A6)
    val Amber = Color(0xFFFFB020)
    val Red = Color(0xFFFF5252)
}

private val scheme = darkColorScheme(
    primary = Cmf.Orange,
    secondary = Cmf.Violet,
    background = Cmf.Black,
    surface = Cmf.Graphite,
    onPrimary = Color.Black,
    onBackground = Cmf.Text,
    onSurface = Cmf.Text,
    outline = Cmf.Outline,
)

@Composable
fun CmfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
