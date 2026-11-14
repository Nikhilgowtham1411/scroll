package dev.nikhil.cmfsocialremote

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.nikhil.cmfsocialremote.ble.BleScanner
import dev.nikhil.cmfsocialremote.ble.WatchConnection
import dev.nikhil.cmfsocialremote.ble.WatchDevice
import dev.nikhil.cmfsocialremote.ble.WatchLinkMonitor
import dev.nikhil.cmfsocialremote.control.AppLauncher
import dev.nikhil.cmfsocialremote.control.AppProfiles
import dev.nikhil.cmfsocialremote.control.Haptics
import dev.nikhil.cmfsocialremote.control.LaunchResult
import dev.nikhil.cmfsocialremote.control.ServiceBridge
import dev.nikhil.cmfsocialremote.data.PermissionState
import dev.nikhil.cmfsocialremote.data.Permissions
import dev.nikhil.cmfsocialremote.data.Settings
import dev.nikhil.cmfsocialremote.data.SettingsStore
import dev.nikhil.cmfsocialremote.gesture.GestureEngine
import dev.nikhil.cmfsocialremote.gesture.MediaButtonSource
import dev.nikhil.cmfsocialremote.gesture.RemoteProtocol
import dev.nikhil.cmfsocialremote.model.ActionResult
import dev.nikhil.cmfsocialremote.model.GestureEvent
import dev.nikhil.cmfsocialremote.model.RemoteAction
import dev.nikhil.cmfsocialremote.model.RemoteGesture
import dev.nikhil.cmfsocialremote.model.SocialApp
import dev.nikhil.cmfsocialremote.model.WatchInput
import dev.nikhil.cmfsocialremote.service.RemoteService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Wires command sources -> gesture engine -> accessibility actions, and exposes state to the UI. */
class RemoteHub(private val app: Application, private val store: SettingsStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings: StateFlow<Settings> = store.settings
    val scanner = BleScanner(app)
    val linkMonitor = WatchLinkMonitor(app)
    val connection = WatchConnection(app, scope) { bytes ->
        RemoteProtocol.decode(bytes)?.let { onInput(it, SOURCE_BLE) }
    }
    private val haptics = Haptics(app)
    private val engine = GestureEngine(scope, { settings.value }, ::onGesture, ::onDropped)

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _lastEvent = MutableStateFlow<GestureEvent?>(null)
    val lastEvent: StateFlow<GestureEvent?> = _lastEvent.asStateFlow()

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _bonded = MutableStateFlow<List<WatchDevice>>(emptyList())
    val bonded: StateFlow<List<WatchDevice>> = _bonded.asStateFlow()

    private val _scanResults = MutableStateFlow<List<WatchDevice>>(emptyList())
    val scanResults: StateFlow<List<WatchDevice>> = _scanResults.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _signal = MutableStateFlow<String?>(null)

    /** Raw description of the last thing the watch (via the phone's media session) sent, for diagnosis. */
    val signal: StateFlow<String?> = _signal.asStateFlow()

    fun onSignal(text: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _signal.value = "$text · $t"
    }

    private var scanJob: Job? = null
    private var eventId = 0L
    private var media: MediaButtonSource? = null

    init {
        // Keep the label the watch's Music screen shows in step with the selected app.
        scope.launch {
            settings
                .map { it.activeApp to it.configFor(it.activeApp).surface }
                .distinctUntilChanged()
                .collect { (a, s) -> media?.setLabel(a, s) }
        }
        ServiceBridge.onSupportedAppForeground = { media?.claim() }
        refresh()
    }

    // ------------------------------------------------------------ status

    fun refresh() {
        _permissions.value = PermissionState(
            bluetoothOn = scanner.isBluetoothOn(),
            blePermission = Permissions.hasBle(app),
            notifications = Permissions.hasNotifications(app),
            accessibility = Permissions.isAccessibilityEnabled(app) && ServiceBridge.connected.value,
        )
        if (Permissions.hasConnect(app)) linkMonitor.start()
        _bonded.value = scanner.bonded()
    }

    fun dismissNotice() {
        _notice.value = null
    }

    // ------------------------------------------------------------ remote lifecycle

    fun startRemote() {
        _notice.value = null
        ContextCompat.startForegroundService(app, Intent(app, RemoteService::class.java))
        val s = settings.value
        if (s.directBleSession) {
            val address = s.preferredWatchAddress
            if (address != null && connection.session.value.state is dev.nikhil.cmfsocialremote.ble.ConnectionState.Idle) {
                connection.connect(WatchDevice(address, s.preferredWatchName ?: "Watch", null, true), s.autoReconnect)
            }
        }
        if (s.autoOpenApp) openActiveApp()
    }

    fun stopRemote() {
        app.stopService(Intent(app, RemoteService::class.java))
    }

    fun openActiveApp() {
        val target = settings.value.activeApp
        when (AppLauncher.launch(app, target)) {
            is LaunchResult.Launched -> Unit
            LaunchResult.NotInstalled -> _notice.value = "${target.title} isn't installed on this phone"
            LaunchResult.Failed -> _notice.value = "Couldn't open ${target.title}"
        }
    }

    /** Called by [RemoteService] when it owns the media session. */
    fun attachMedia(source: MediaButtonSource?) {
        media = source
    }

    fun reclaimMediaButtons() {
        media?.claim()
    }

    fun setRunning(value: Boolean) {
        _running.value = value
    }

    // ------------------------------------------------------------ input pipeline

    fun onInput(input: WatchInput, source: String) {
        if (!_running.value && source != SOURCE_TEST) return
        engine.handle(input, source)
    }

    private fun onDropped(gesture: RemoteGesture, source: String) {
        publish(nextId(), gesture, source, ActionResult.Ignored("Too fast — skipped"))
    }

    private fun onGesture(gesture: RemoteGesture, source: String) {
        val s = settings.value
        val target = s.activeApp
        val config = s.configFor(target)
        val id = nextId()

        val action: RemoteAction? = when (gesture) {
            RemoteGesture.SWIPE_UP -> if (config.invertScroll) RemoteAction.PREVIOUS else RemoteAction.NEXT
            RemoteGesture.SWIPE_DOWN -> if (config.invertScroll) RemoteAction.NEXT else RemoteAction.PREVIOUS
            RemoteGesture.DOUBLE_TAP -> RemoteAction.LIKE_TOGGLE
            RemoteGesture.SINGLE_TAP -> null
        }
        if (action == null) {
            publish(id, gesture, source, ActionResult.Ignored("Single tap — no action"))
            return
        }

        publish(id, gesture, source, null)
        scope.launch {
            val service = ServiceBridge.service
            val result: ActionResult = if (service == null) {
                ActionResult.NotEnabled()
            } else {
                try {
                    service.perform(action, AppProfiles.of(target), config)
                } catch (e: Exception) {
                    ActionResult.Failed("Unexpected error (${e.javaClass.simpleName})")
                }
            }
            publish(id, gesture, source, result)
            if (s.phoneHaptics) {
                if (result is ActionResult.Confirmed) haptics.confirmed() else haptics.problem()
            }
        }
    }

    private fun publish(id: Long, gesture: RemoteGesture, source: String, result: ActionResult?) {
        _lastEvent.value = GestureEvent(id, gesture, source, result)
    }

    private fun nextId() = ++eventId

    // ------------------------------------------------------------ devices

    fun startScan() {
        if (_scanning.value) return
        _scanError.value = null
        _scanResults.value = emptyList()
        _scanning.value = true
        scanJob = scope.launch {
            scanner.scan()
                .catch { _scanError.value = it.message ?: "Scan failed" }
                .collect { d ->
                    _scanResults.value = (_scanResults.value.filterNot { it.address == d.address } + d)
                        .sortedWith(compareByDescending<WatchDevice> { it.looksLikeCmf }.thenByDescending { it.rssi ?: -127 })
                }
            _scanning.value = false
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _scanning.value = false
    }

    fun connect(device: WatchDevice) {
        store.update { it.copy(preferredWatchAddress = device.address, preferredWatchName = device.name) }
        connection.connect(device, settings.value.autoReconnect)
    }

    fun disconnect() = connection.disconnect()

    fun forgetWatch() {
        connection.disconnect()
        store.update { it.copy(preferredWatchAddress = null, preferredWatchName = null) }
    }

    // ------------------------------------------------------------ settings shortcuts

    fun selectApp(target: SocialApp) = store.update { it.copy(activeApp = target) }
    fun update(block: (Settings) -> Settings) = store.update(block)
    fun updateApp(target: SocialApp, block: (dev.nikhil.cmfsocialremote.model.AppConfig) -> dev.nikhil.cmfsocialremote.model.AppConfig) =
        store.updateApp(target, block)

    companion object {
        const val SOURCE_TEST = "Phone test"
        const val SOURCE_BLE = "BLE command channel"
    }
}
