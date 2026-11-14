package dev.nikhil.cmfsocialremote.control

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide handle to the accessibility service, which Android instantiates itself. */
object ServiceBridge {
    @Volatile
    var service: ControlAccessibilityService? = null
        private set

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Invoked when one of the supported apps shows a new window, so media buttons can be re-claimed. */
    @Volatile
    var onSupportedAppForeground: (() -> Unit)? = null

    fun attach(s: ControlAccessibilityService) {
        service = s
        _connected.value = true
    }

    fun detach(s: ControlAccessibilityService) {
        if (service === s) {
            service = null
            _connected.value = false
        }
    }
}
