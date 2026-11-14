package dev.nikhil.cmfsocialremote.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nikhil.cmfsocialremote.RemoteHub
import dev.nikhil.cmfsocialremote.data.Permissions
import dev.nikhil.cmfsocialremote.ui.theme.Cmf

private const val STEPS = 4

@Composable
fun OnboardingScreen(hub: RemoteHub, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val perms by hub.permissions.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(0) }

    val askBle = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hub.refresh() }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hub.refresh() }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Wordmark()
            Text("Step ${step + 1} of $STEPS", color = Cmf.TextDim, fontSize = 12.sp)

            when (step) {
                0 -> {
                    Title("Your watch as a remote")
                    Body("CMF Social Remote lets your CMF Watch Pro scroll and like in Instagram, YouTube Shorts, Facebook Reels and TikTok.")
                    GlowCard(Modifier.fillMaxWidth(), highlight = true) {
                        Text("What to expect", color = Cmf.Text, fontSize = 15.sp)
                        Body(
                            "The original CMF Watch Pro runs a closed system. No app can be installed on it, and its screen can't be given custom gestures. " +
                                "So this app uses what the watch already sends the phone: the Music screen. " +
                                "Next ▶▶ scrolls forward, previous ◀◀ scrolls back, and pressing play/pause twice likes or unlikes. A single press does nothing.",
                        )
                    }
                }
                1 -> {
                    Title("Bluetooth")
                    Body("Bluetooth access lets the app find your paired watch, show whether it is connected and, if you choose, read its battery. It is not needed for the Music-screen controls.")
                    StatusLine(perms.blePermission, "Bluetooth permission")
                    StatusLine(perms.notifications, "Notification permission (shows the \"remote is active\" notice)")
                    CmfButton("Allow Bluetooth", onClick = { askBle.launch(Permissions.blePermissions()) }, enabled = !perms.blePermission, modifier = Modifier.fillMaxWidth())
                    CmfButton(
                        "Allow notifications",
                        onClick = { if (android.os.Build.VERSION.SDK_INT >= 33) askNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                        enabled = !perms.notifications,
                        filled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                2 -> {
                    Title("Accessibility access")
                    Body(
                        "Android only lets an app scroll or tap inside another app through an accessibility service, and only after you switch it on yourself.",
                    )
                    GlowCard(Modifier.fillMaxWidth()) {
                        Text("What it can and can't do", color = Cmf.Text, fontSize = 15.sp)
                        Body(
                            "• Works only in Instagram, YouTube, Facebook and TikTok.\n" +
                                "• Acts only when your watch sends a command.\n" +
                                "• Never reads or stores passwords or messages, never logs in for you.\n" +
                                "• The app has no internet permission, so nothing leaves your phone.",
                        )
                    }
                    StatusLine(perms.accessibility, "Accessibility service")
                    CmfButton(
                        "Open accessibility settings",
                        onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Body("Then choose CMF Social Remote and turn it on. If Android says the setting is restricted (common for apps installed from a file), open App info, tap ⋮ and choose \"Allow restricted settings\", then try again.")
                    CmfButton(
                        "Open App info",
                        onClick = {
                            ctx.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        filled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    Title("Using the watch")
                    Body("1. Pair the watch in the CMF Watch app as usual and keep it connected.\n2. Here, pick your app and tap Start Remote.\n3. On the watch, open Music.\n4. ▶▶ next · ◀◀ previous · double-press ⏯ like/unlike.")
                    GlowCard(Modifier.fillMaxWidth()) {
                        Text("Worth knowing", color = Cmf.Text, fontSize = 15.sp)
                        Body(
                            "Whether the watch's Music keys reach this app depends on your phone and the CMF Watch app, so try it once. " +
                                "If they control another player instead, use \"Re-claim watch music controls\" in Settings. " +
                                "You can always test the same gestures on the on-screen dial. " +
                                "The watch can't show success or failure; the phone's feedback tells you what happened.",
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) {
                CmfButton("Back", onClick = { step-- }, filled = false, modifier = Modifier.weight(1f))
            } else {
                CmfButton("Skip", onClick = onDone, filled = false, modifier = Modifier.weight(1f))
            }
            CmfButton(
                if (step == STEPS - 1) "Finish" else "Next",
                onClick = { if (step == STEPS - 1) onDone() else step++ },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, color = Cmf.Text, fontSize = 26.sp)

@Composable
private fun Body(text: String) = Text(text, color = Cmf.TextDim, fontSize = 14.sp, lineHeight = 20.sp)

@Composable
private fun StatusLine(ok: Boolean, label: String) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Pill(if (ok) "Ready" else "Needed", if (ok) Cmf.Green else Cmf.Amber)
        Spacer(Modifier.height(0.dp))
        Text("  $label", color = Cmf.Text, fontSize = 13.sp)
    }
}
