package dev.nikhil.cmfsocialremote

import android.app.Application
import dev.nikhil.cmfsocialremote.data.SettingsStore

class SocialRemoteApp : Application() {
    val hub: RemoteHub by lazy { RemoteHub(this, SettingsStore(this)) }
}
