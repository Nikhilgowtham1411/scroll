package dev.nikhil.cmfsocialremote.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nikhil.cmfsocialremote.ui.theme.Cmf

private val GADGETBRIDGE_PACKAGES = listOf(
    "nodomain.freeyourgadget.gadgetbridge",
    "nodomain.freeyourgadget.gadgetbridge.nightly",
    "nodomain.freeyourgadget.gadgetbridge.nightly_nopebble",
)

/**
 * The CMF Watch Pro only talks to a phone through a signed-in vendor session, so this app cannot open
 * that link itself. Gadgetbridge (open source) already implements it for the original CMF Watch Pro
 * and relays the watch's music buttons to the phone, where this app picks them up.
 */
@Composable
fun WatchSetupCard() {
    val ctx = LocalContext.current
    val installed = GADGETBRIDGE_PACKAGES.firstOrNull { pkg ->
        try {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    GlowCard(Modifier.fillMaxWidth(), highlight = true) {
        Text("Connect the watch", color = Cmf.Text, fontSize = 17.sp)
        Pill(
            if (installed != null) "Gadgetbridge installed" else "Gadgetbridge not installed",
            if (installed != null) Cmf.Green else Cmf.Amber,
            Modifier.padding(top = 8.dp),
        )
        Text(
            "This app can't log in to the watch by itself: the CMF Watch Pro only accepts a connection that carries its pairing key. " +
                "Gadgetbridge (free, open source) supports the original CMF Watch Pro and passes the watch's Music buttons to the phone, which this app turns into scroll and like.",
            color = Cmf.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            "1. Pair the watch in the CMF Watch app once (account needed). Don't unpair it afterwards.\n" +
                "2. Get the watch's pairing key (K1): connect the phone to a computer with adb, run  adb logcat  while pairing, and look for lines containing \"k1\" or \"sign secret\". Rooted phones can read it from the app's database instead.\n" +
                "3. Install Gadgetbridge, add the CMF Watch Pro, long-press it and choose \"Auth key\", paste the K1 key (no spaces or newline).\n" +
                "4. Close the CMF Watch app (remove its permissions) so only one app holds the watch.\n" +
                "5. Back here: pick the social app and tap Start Remote. On the watch open Music and press next / previous.",
            color = Cmf.Text, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp),
        )
        Column(Modifier.padding(top = 12.dp)) {
            if (installed != null) {
                CmfButton("Open Gadgetbridge", modifier = Modifier.fillMaxWidth(), onClick = {
                    ctx.packageManager.getLaunchIntentForPackage(installed)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.let { ctx.startActivity(it) }
                })
            } else {
                CmfButton("Get Gadgetbridge (F-Droid)", modifier = Modifier.fillMaxWidth(), onClick = {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/nodomain.freeyourgadget.gadgetbridge/"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                })
            }
            Text(
                "Watch faces: uploading a custom face is only experimental in Gadgetbridge, so the theme is set through the CMF Watch app's photo dial instead.",
                color = Cmf.TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
