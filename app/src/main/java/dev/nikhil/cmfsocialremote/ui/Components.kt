package dev.nikhil.cmfsocialremote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nikhil.cmfsocialremote.model.ActionResult
import dev.nikhil.cmfsocialremote.model.SocialApp
import dev.nikhil.cmfsocialremote.ui.theme.Cmf

@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val border = if (highlight) {
        Brush.linearGradient(listOf(Cmf.Orange, Cmf.Violet))
    } else {
        Brush.linearGradient(listOf(Cmf.Outline, Cmf.Outline))
    }
    Column(
        modifier
            .clip(shape)
            .background(Cmf.Graphite)
            .border(BorderStroke(1.dp, border), shape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun CmfButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
    val fill = if (filled) {
        Brush.horizontalGradient(listOf(Cmf.Orange, Color(0xFFFF7A3D)))
    } else {
        Brush.horizontalGradient(listOf(Cmf.Graphite2, Cmf.Graphite2))
    }
    Box(
        modifier
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(fill)
            .border(BorderStroke(1.dp, if (filled) Color.Transparent else Cmf.Outline), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (filled) Color.Black else Cmf.Text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Cmf.Graphite2)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Cmf.Text, fontSize = 13.sp)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) Cmf.Orange.copy(alpha = 0.18f) else Cmf.Graphite2)
            .border(BorderStroke(1.dp, if (selected) Cmf.Orange else Cmf.Outline), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, color = if (selected) Cmf.Orange else Cmf.Text, fontSize = 13.sp)
    }
}

@Composable
fun AppGlyph(app: SocialApp, size: Int = 44) {
    val colors = when (app) {
        SocialApp.INSTAGRAM -> listOf(Cmf.Orange, Cmf.Pink, Cmf.Violet)
        SocialApp.YOUTUBE -> listOf(Cmf.Red, Cmf.Orange)
        SocialApp.FACEBOOK -> listOf(Color(0xFF3B82F6), Cmf.Violet)
        SocialApp.TIKTOK -> listOf(Color(0xFF22D3EE), Cmf.Pink)
    }
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(app.monogram, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * 0.36f).sp)
    }
}

@Composable
fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Cmf.Text, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = Cmf.TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Cmf.Orange,
                uncheckedThumbColor = Cmf.TextDim,
                uncheckedTrackColor = Cmf.Graphite2,
                uncheckedBorderColor = Cmf.Outline,
            ),
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = Cmf.TextDim,
        fontSize = 12.sp,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 4.dp),
    )
}

@Composable
fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "CMF",
            color = Cmf.Text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(7.dp).clip(CircleShape).background(Cmf.Orange))
        Spacer(Modifier.width(10.dp))
        Text("Social Remote", color = Cmf.TextDim, fontSize = 16.sp)
    }
}

fun ActionResult.color(): Color = when (this) {
    is ActionResult.Confirmed -> Cmf.Green
    is ActionResult.Unconfirmed -> Cmf.Amber
    is ActionResult.Ignored -> Cmf.TextDim
    else -> Cmf.Red
}

fun ActionResult.headline(): String = when (this) {
    is ActionResult.Confirmed -> "Confirmed"
    is ActionResult.Unconfirmed -> "Sent, not confirmed"
    is ActionResult.Unsupported -> "Not supported"
    is ActionResult.WrongApp -> "Wrong app on screen"
    is ActionResult.NotEnabled -> "Accessibility is off"
    is ActionResult.Failed -> "Failed"
    is ActionResult.Ignored -> "Skipped"
}

@Composable
fun VSpace(dp: Int) = Spacer(Modifier.height(dp.dp))

@Composable
fun SpacedColumn(modifier: Modifier = Modifier, gap: Int = 12, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap.dp), content = content)
}
