package dev.nikhil.cmfsocialremote.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nikhil.cmfsocialremote.RemoteHub
import dev.nikhil.cmfsocialremote.ble.CmfCompatibility
import dev.nikhil.cmfsocialremote.ble.ConnectionState
import dev.nikhil.cmfsocialremote.ble.WatchDevice
import dev.nikhil.cmfsocialremote.data.Permissions
import dev.nikhil.cmfsocialremote.ui.theme.Cmf

@Composable
fun DeviceScreen(hub: RemoteHub) {
    val ctx = LocalContext.current
    val settings by hub.settings.collectAsStateWithLifecycle()
    val perms by hub.permissions.collectAsStateWithLifecycle()
    val session by hub.connection.session.collectAsStateWithLifecycle()
    val bonded by hub.bonded.collectAsStateWithLifecycle()
    val found by hub.scanResults.collectAsStateWithLifecycle()
    val scanning by hub.scanning.collectAsStateWithLifecycle()
    val scanError by hub.scanError.collectAsStateWithLifecycle()
    val linked by hub.linkMonitor.connected.collectAsStateWithLifecycle()

    val askBle = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hub.refresh()
        if (Permissions.hasBle(ctx)) hub.startScan()
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Wordmark()

        WatchSetupCard()

        // Connection status
        GlowCard(Modifier.fillMaxWidth(), highlight = session.state is ConnectionState.Connected) {
            val (label, color) = when (val st = session.state) {
                ConnectionState.Idle -> "No direct BLE session" to Cmf.TextDim
                is ConnectionState.Connecting -> "Connecting (attempt ${st.attempt})…" to Cmf.Violet
                ConnectionState.Connected -> "Direct BLE session open" to Cmf.Green
                is ConnectionState.Reconnecting -> "Reconnecting in ${st.inMs / 1000}s (attempt ${st.attempt})" to Cmf.Amber
                is ConnectionState.Failed -> "Couldn't connect" to Cmf.Red
            }
            Pill(label, color)
            (session.state as? ConnectionState.Failed)?.let {
                Text(it.reason, color = Cmf.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            session.device?.let { Text(it.name, color = Cmf.Text, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp)) }
            if (session.state is ConnectionState.Connected) {
                val info = listOfNotNull(
                    session.batteryPercent?.let { "Battery $it%" },
                    session.manufacturer,
                    session.model,
                ).joinToString(" · ")
                if (info.isNotEmpty()) Text(info, color = Cmf.TextDim, fontSize = 12.sp)
                Text(
                    if (session.hasCommandChannel) "Command channel found" else "No command channel on this device",
                    color = if (session.hasCommandChannel) Cmf.Green else Cmf.TextDim,
                    fontSize = 12.sp,
                )
            }
            if (session.state !is ConnectionState.Idle) {
                CmfButton("Disconnect", onClick = { hub.disconnect() }, filled = false, modifier = Modifier.padding(top = 12.dp).fillMaxWidth())
            }
        }

        if (!perms.bluetoothOn) {
            GlowCard(Modifier.fillMaxWidth()) {
                Text("Bluetooth is off", color = Cmf.Text, fontSize = 15.sp)
                CmfButton(
                    "Open Bluetooth settings",
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                )
            }
        }

        SectionTitle("Paired with this phone")
        if (!Permissions.hasConnect(ctx)) {
            Text("Allow Bluetooth access to list paired devices.", color = Cmf.TextDim, fontSize = 13.sp)
        } else if (bonded.isEmpty()) {
            Text("Nothing paired yet. Pair the watch in the CMF Watch app first.", color = Cmf.TextDim, fontSize = 13.sp)
        }
        bonded.sortedByDescending { it.looksLikeCmf }.forEach { d ->
            DeviceRow(d, linked = d.address in linked, selected = d.address == settings.preferredWatchAddress, onConnect = { hub.connect(d) })
        }

        SectionTitle("Nearby (BLE scan)")
        CmfButton(
            text = if (scanning) "Scanning…" else "Scan for devices",
            enabled = !scanning,
            filled = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (Permissions.hasBle(ctx)) hub.startScan() else askBle.launch(Permissions.blePermissions())
            },
        )
        scanError?.let { Text(it, color = Cmf.Red, fontSize = 12.sp) }
        if (!scanning && found.isEmpty() && scanError == null) {
            Text(
                "A watch that is already connected to the CMF Watch app usually stops advertising, so it may not appear here. Use the paired list above.",
                color = Cmf.TextDim,
                fontSize = 12.sp,
            )
        }
        found.forEach { d ->
            DeviceRow(d, linked = d.address in linked, selected = d.address == settings.preferredWatchAddress, onConnect = { hub.connect(d) })
        }

        if (settings.preferredWatchAddress != null) {
            CmfButton("Forget selected watch", onClick = { hub.forgetWatch() }, filled = false, modifier = Modifier.fillMaxWidth())
        }

        SectionTitle("What the original CMF Watch Pro supports")
        GlowCard(Modifier.fillMaxWidth()) {
            CmfCompatibility.capabilities.forEachIndexed { i, c ->
                if (i > 0) Spacer(Modifier.padding(vertical = 6.dp))
                val color = when (c.support) {
                    CmfCompatibility.Support.WORKS -> Cmf.Green
                    CmfCompatibility.Support.VERIFY -> Cmf.Amber
                    CmfCompatibility.Support.NEEDS_WATCH -> Cmf.Violet
                    CmfCompatibility.Support.UNAVAILABLE -> Cmf.Red
                }
                Text(c.name, color = Cmf.Text, fontSize = 14.sp)
                Pill(c.support.label, color, Modifier.padding(vertical = 4.dp))
                Text(c.detail, color = Cmf.TextDim, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DeviceRow(d: WatchDevice, linked: Boolean, selected: Boolean, onConnect: () -> Unit) {
    GlowCard(Modifier.fillMaxWidth(), highlight = selected) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name, color = Cmf.Text, fontSize = 15.sp)
                val bits = listOfNotNull(
                    if (d.looksLikeCmf) "CMF watch" else null,
                    if (d.bonded) "paired" else null,
                    if (linked) "connected to phone" else null,
                    d.rssi?.let { "$it dBm" },
                )
                Text(bits.joinToString(" · ").ifEmpty { d.address }, color = Cmf.TextDim, fontSize = 12.sp)
            }
            Spacer(Modifier.width(10.dp))
            CmfButton(if (selected) "Reconnect" else "Select", onClick = onConnect, filled = false)
        }
    }
}
