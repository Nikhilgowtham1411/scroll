package dev.nikhil.cmfsocialremote.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nikhil.cmfsocialremote.RemoteHub
import dev.nikhil.cmfsocialremote.SocialRemoteApp
import dev.nikhil.cmfsocialremote.ui.theme.Cmf
import dev.nikhil.cmfsocialremote.ui.theme.CmfTheme

class MainActivity : ComponentActivity() {
    private val hub: RemoteHub get() = (application as SocialRemoteApp).hub

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CmfTheme { Root(hub) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions, Bluetooth state and accessibility status can change while we're in Settings.
        hub.refresh()
    }
}

private enum class Tab(val label: String) { REMOTE("Remote"), WATCH("Watch"), APPS("Apps"), SETTINGS("Settings") }

@Composable
private fun Root(hub: RemoteHub) {
    val settings by hub.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.REMOTE) }
    var showSetup by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Cmf.Black).statusBarsPadding().navigationBarsPadding()) {
        if (!settings.onboardingDone || showSetup) {
            OnboardingScreen(hub) {
                showSetup = false
                hub.update { it.copy(onboardingDone = true) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.REMOTE -> HomeScreen(hub, onNeedSetup = { showSetup = true }, onPickApp = { tab = Tab.APPS })
                        Tab.WATCH -> DeviceScreen(hub)
                        Tab.APPS -> AppsScreen(hub)
                        Tab.SETTINGS -> SettingsScreen(hub, onOpenSetup = { showSetup = true })
                    }
                }
                BottomBar(tab) { tab = it }
            }
        }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Cmf.Graphite).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val selected = t == current
            Column(
                Modifier.weight(1f).clickable { onSelect(t) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(5.dp).clip(CircleShape).background(if (selected) Cmf.Orange else Cmf.Graphite),
                )
                Text(
                    t.label,
                    color = if (selected) Cmf.Text else Cmf.TextDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
