package dev.nikhil.cmfsocialremote.ble

data class WatchDevice(
    val address: String,
    val name: String,
    val rssi: Int?,
    val bonded: Boolean,
) {
    val looksLikeCmf: Boolean get() = CmfCompatibility.looksLikeCmf(name)
}

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Connecting(val attempt: Int) : ConnectionState
    data object Connected : ConnectionState
    data class Reconnecting(val attempt: Int, val inMs: Long) : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}

/** Snapshot of the direct BLE (GATT) session. Separate from the phone-level "watch is linked" status. */
data class WatchSession(
    val state: ConnectionState = ConnectionState.Idle,
    val device: WatchDevice? = null,
    val batteryPercent: Int? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val services: List<String> = emptyList(),
    val hasCommandChannel: Boolean = false,
)

/** What the original CMF Watch Pro can and cannot do for this app. Mirrors docs/COMPATIBILITY.md. */
object CmfCompatibility {
    fun looksLikeCmf(name: String?): Boolean {
        val n = name?.lowercase() ?: return false
        return "cmf" in n || "watch pro" in n
    }

    enum class Support(val label: String) {
        WORKS("Works today"),
        VERIFY("Works — verify on your phone"),
        NEEDS_WATCH("Needs watch-side support"),
        UNAVAILABLE("Not possible"),
    }

    data class Capability(val name: String, val support: Support, val detail: String)

    val capabilities: List<Capability> = listOf(
        Capability(
            "Watch Music ▲ next / ▼ previous → scroll",
            Support.VERIFY,
            "The watch's Music screen sends track skip to the phone through the CMF Watch app. " +
                "This app owns the phone's media session so those keys arrive here.",
        ),
        Capability(
            "Watch Music ⏯ double press → like / unlike",
            Support.VERIFY,
            "Two quick presses of play/pause are read as a double tap. A single press does nothing.",
        ),
        Capability(
            "Link status (watch connected to phone)",
            Support.WORKS,
            "Read from Android's Bluetooth connection events for the paired watch.",
        ),
        Capability(
            "Battery level over standard BLE",
            Support.VERIFY,
            "Only if the watch exposes the standard Battery Service and accepts a second connection.",
        ),
        Capability(
            "Direct BLE command channel",
            Support.NEEDS_WATCH,
            "Implemented and tested against the reference protocol, but the original watch cannot send it.",
        ),
        Capability(
            "Custom swipe / double-tap gestures on the watch",
            Support.UNAVAILABLE,
            "The watch OS is closed: no app store, no SDK, no way to install a watch app or change firmware.",
        ),
        Capability(
            "Custom watch face with a gesture pad",
            Support.UNAVAILABLE,
            "Watch faces are static or cloud dials from the CMF Watch app. They cannot run logic or send touch events.",
        ),
        Capability(
            "Watch-side haptic feedback",
            Support.UNAVAILABLE,
            "No documented way for a third-party app to vibrate the watch. Feedback is on the phone only.",
        ),
    )
}
