package dev.nikhil.cmfsocialremote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import dev.nikhil.cmfsocialremote.control.AppLauncher
import dev.nikhil.cmfsocialremote.model.SocialApp
import dev.nikhil.cmfsocialremote.ui.theme.Cmf

@Composable
fun AppsScreen(hub: RemoteHub) {
    val ctx = LocalContext.current
    val settings by hub.settings.collectAsStateWithLifecycle()

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Wordmark()
        Text("Choose the app the watch controls.", color = Cmf.TextDim, fontSize = 14.sp)

        SocialApp.entries.forEach { target ->
            val selected = target == settings.activeApp
            val installed = AppLauncher.installedPackage(ctx, target) != null
            GlowCard(
                Modifier.fillMaxWidth().clickable { hub.selectApp(target) },
                highlight = selected,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppGlyph(target, 52)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(target.title, color = Cmf.Text, fontSize = 18.sp)
                        Text(target.subtitle, color = Cmf.TextDim, fontSize = 13.sp)
                        Pill(
                            if (installed) "Installed" else "Not installed",
                            if (installed) Cmf.Green else Cmf.Amber,
                            Modifier.padding(top = 8.dp),
                        )
                    }
                    if (selected) Text("Active", color = Cmf.Orange, fontSize = 13.sp)
                }
                if (installed) {
                    Spacer(Modifier.height(10.dp))
                    CmfButton(
                        "Open ${target.title}",
                        filled = false,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            hub.selectApp(target)
                            hub.openActiveApp()
                        },
                    )
                }
            }
        }

        Text(
            "Each app has its own control profile. The apps don't publish a stable interface, so Like-button detection is best effort and can change after an app update. Whatever the phone can't verify is reported as \"not confirmed\".",
            color = Cmf.TextDim,
            fontSize = 12.sp,
        )
    }
}
