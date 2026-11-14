package dev.nikhil.cmfsocialremote.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nikhil.cmfsocialremote.RemoteHub
import dev.nikhil.cmfsocialremote.data.Permissions
import dev.nikhil.cmfsocialremote.model.ContentSurface
import dev.nikhil.cmfsocialremote.model.LikeStrategy
import dev.nikhil.cmfsocialremote.model.SocialApp
import dev.nikhil.cmfsocialremote.ui.theme.Cmf

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(hub: RemoteHub, onOpenSetup: () -> Unit) {
    val ctx = LocalContext.current
    val s by hub.settings.collectAsStateWithLifecycle()
    val perms by hub.permissions.collectAsStateWithLifecycle()

    val askBle = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hub.refresh() }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hub.refresh() }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Wordmark()

        SectionTitle("Gesture sensitivity")
        GlowCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                Text("Relaxed", color = Cmf.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("Quick", color = Cmf.TextDim, fontSize = 12.sp)
            }
            Slider(
                value = s.sensitivity,
                onValueChange = { v -> hub.update { it.copy(sensitivity = v) } },
                colors = SliderDefaults.colors(
                    thumbColor = Cmf.Orange,
                    activeTrackColor = Cmf.Orange,
                    inactiveTrackColor = Cmf.Outline,
                ),
            )
            Text(
                "Double-tap window ${s.doublePressWindowMs} ms · minimum ${s.scrollCooldownMs} ms between scrolls",
                color = Cmf.TextDim,
                fontSize = 12.sp,
            )
        }

        SectionTitle("Feedback")
        GlowCard(Modifier.fillMaxWidth()) {
            SettingSwitch("Phone haptics", "Short tick when confirmed, double buzz when not", s.phoneHaptics) { v ->
                hub.update { it.copy(phoneHaptics = v) }
            }
            Text("Watch haptics", color = Cmf.Text, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Not available: the CMF Watch Pro gives third-party apps no way to vibrate the watch.",
                color = Cmf.TextDim,
                fontSize = 12.sp,
            )
        }

        SectionTitle("Permissions")
        GlowCard(Modifier.fillMaxWidth()) {
            PermissionRow("Accessibility service", perms.accessibility, "Open") {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PermissionRow("Bluetooth", perms.blePermission, "Allow") { askBle.launch(Permissions.blePermissions()) }
            PermissionRow("Notifications", perms.notifications, "Allow") {
                if (android.os.Build.VERSION.SDK_INT >= 33) askNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            PermissionRow("Restricted settings (sideloaded installs)", true, "App info") {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            CmfButton("Run setup again", onClick = onOpenSetup, filled = false, modifier = Modifier.padding(top = 12.dp).fillMaxWidth())
        }

        SectionTitle("Connection")
        GlowCard(Modifier.fillMaxWidth()) {
            SettingSwitch("Auto-reconnect", "Retry with back-off if a direct BLE session drops", s.autoReconnect) { v ->
                hub.update { it.copy(autoReconnect = v) }
            }
            SettingSwitch(
                "Open direct BLE session on start",
                "Reads battery and listens for the command channel. May compete with the CMF Watch app for the watch's single connection.",
                s.directBleSession,
            ) { v -> hub.update { it.copy(directBleSession = v) } }
            SettingSwitch("Open the selected app on start", null, s.autoOpenApp) { v ->
                hub.update { it.copy(autoOpenApp = v) }
            }
            CmfButton("Re-claim watch music controls", onClick = { hub.reclaimMediaButtons() }, filled = false, modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
            Text(
                "Use this if the watch's Music buttons start controlling another player instead of this app.",
                color = Cmf.TextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SectionTitle("App-specific gestures")
        SocialApp.entries.forEach { target ->
            val c = s.configFor(target)
            GlowCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppGlyph(target, 32)
                    Text("  ${target.title} · ${target.subtitle}", color = Cmf.Text, fontSize = 15.sp)
                }
                SettingSwitch("Invert scroll direction", "Swipe up goes to previous", c.invertScroll) { v ->
                    hub.updateApp(target) { it.copy(invertScroll = v) }
                }
                SettingSwitch("Allow unlike", "Double tap on a liked item removes the like", c.allowUnlike) { v ->
                    hub.updateApp(target) { it.copy(allowUnlike = v) }
                }
                Text("How to like", color = Cmf.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LikeStrategy.entries.forEach { st ->
                        Chip(st.label, selected = c.likeStrategy == st, onClick = {
                            hub.updateApp(target) { it.copy(likeStrategy = st) }
                        })
                    }
                }
                if (target == SocialApp.INSTAGRAM) {
                    Text("Surface", color = Cmf.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ContentSurface.entries.forEach { sf ->
                            Chip(sf.label, selected = c.surface == sf, onClick = {
                                hub.updateApp(target) { it.copy(surface = sf) }
                            })
                        }
                    }
                }
            }
        }

        SectionTitle("Privacy")
        Text(
            "This app has no internet permission and never asks for, stores or reads any social account password. It does not log in for you. The accessibility service only sees the four supported apps, only acts when your watch sends a command, and skips password fields.",
            color = Cmf.TextDim,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PermissionRow(name: String, ok: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Cmf.Text, fontSize = 15.sp)
            Text(if (ok) "Ready" else "Needed", color = if (ok) Cmf.Green else Cmf.Amber, fontSize = 12.sp)
        }
        Chip(action, selected = false, onClick = onClick)
    }
}
