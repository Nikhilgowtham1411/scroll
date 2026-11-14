package dev.nikhil.cmfsocialremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nikhil.cmfsocialremote.RemoteHub
import dev.nikhil.cmfsocialremote.ble.ConnectionState
import dev.nikhil.cmfsocialremote.model.ContentSurface
import dev.nikhil.cmfsocialremote.model.SocialApp
import dev.nikhil.cmfsocialremote.ui.theme.Cmf

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(hub: RemoteHub, onNeedSetup: () -> Unit, onPickApp: () -> Unit) {
    val settings by hub.settings.collectAsStateWithLifecycle()
    val running by hub.running.collectAsStateWithLifecycle()
    val event by hub.lastEvent.collectAsStateWithLifecycle()
    val perms by hub.permissions.collectAsStateWithLifecycle()
    val session by hub.connection.session.collectAsStateWithLifecycle()
    val linked by hub.linkMonitor.connected.collectAsStateWithLifecycle()
    val notice by hub.notice.collectAsStateWithLifecycle()
    val signal by hub.signal.collectAsStateWithLifecycle()

    val app = settings.activeApp
    val watchName = settings.preferredWatchName
    val watchLinked = settings.preferredWatchAddress?.let { it in linked } == true ||
        session.state is ConnectionState.Connected

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Wordmark()

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (perms.bluetoothOn) "Bluetooth on" else "Bluetooth off", if (perms.bluetoothOn) Cmf.Green else Cmf.Red)
            Pill(
                when {
                    watchName == null -> "No watch chosen"
                    watchLinked -> "Watch connected"
                    else -> "Watch not connected"
                },
                if (watchLinked) Cmf.Green else Cmf.Amber,
            )
            Pill(if (perms.accessibility) "Accessibility on" else "Accessibility off", if (perms.accessibility) Cmf.Green else Cmf.Red)
        }

        GlowCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(watchName ?: "No watch selected", color = Cmf.Text, fontSize = 17.sp)
                    Text(
                        if (watchName == null) "Pick your CMF Watch Pro on the Watch tab" else if (watchLinked) "Linked to this phone" else "Waiting for the watch",
                        color = Cmf.TextDim,
                        fontSize = 12.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(session.batteryPercent?.let { "$it%" } ?: "—", color = Cmf.Text, fontSize = 17.sp)
                    Text("Battery", color = Cmf.TextDim, fontSize = 12.sp)
                }
            }
            if (watchLinked && session.batteryPercent == null) {
                Text(
                    "The watch doesn't share its battery with other apps unless a direct BLE session is open.",
                    color = Cmf.TextDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        GestureDial(
            event = event,
            onTest = { hub.onInput(it, RemoteHub.SOURCE_TEST) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )

        if (running) {
            Text(
                signal?.let { "Last signal from watch: $it" }
                    ?: "No signal from the watch yet. Open Music on the watch and press next.",
                color = if (signal != null) Cmf.Green else Cmf.Amber,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val e = event
        if (e != null) {
            val r = e.result
            GlowCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(r?.headline() ?: "Working…", r?.color() ?: Cmf.Violet)
                    Spacer(Modifier.width(10.dp))
                    Text("${e.gesture.label} · ${e.source}", color = Cmf.TextDim, fontSize = 12.sp)
                }
                if (r != null) {
                    Text(r.message, color = Cmf.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else {
            Text(
                "Swipe or double-tap the dial to test without the watch.",
                color = Cmf.TextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        GlowCard(Modifier.fillMaxWidth(), highlight = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppGlyph(app)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.title, color = Cmf.Text, fontSize = 17.sp)
                    Text(app.subtitle, color = Cmf.TextDim, fontSize = 12.sp)
                }
                Chip("Change", selected = false, onClick = onPickApp)
            }
            if (app == SocialApp.INSTAGRAM) {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContentSurface.entries.forEach { s ->
                        Chip(s.label, selected = settings.configFor(app).surface == s, onClick = {
                            hub.updateApp(app) { it.copy(surface = s) }
                        })
                    }
                }
            }
        }

        notice?.let {
            Text(it, color = Cmf.Amber, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }

        CmfButton(
            text = if (running) "Stop Remote" else "Start Remote",
            filled = !running,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                when {
                    running -> hub.stopRemote()
                    !perms.accessibility -> onNeedSetup()
                    else -> hub.startRemote()
                }
            },
        )
        if (running) {
            Text(
                "On the watch: open Music, then ▶▶ next, ◀◀ previous, double-press ⏯ to like.",
                color = Cmf.TextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
