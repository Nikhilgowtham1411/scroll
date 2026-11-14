package dev.nikhil.cmfsocialremote.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import dev.nikhil.cmfsocialremote.gesture.RemoteProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

private fun uuid16(v: Int): UUID = UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", v))

private val SVC_BATTERY = uuid16(0x180F)
private val SVC_DEVICE_INFO = uuid16(0x180A)
private val CHR_BATTERY_LEVEL = uuid16(0x2A19)
private val CHR_MANUFACTURER = uuid16(0x2A29)
private val CHR_MODEL = uuid16(0x2A24)
private val CCC = uuid16(0x2902)

private const val CONNECT_TIMEOUT_MS = 15_000L
private const val MAX_ATTEMPTS = 8

/**
 * Direct BLE (GATT) session with a chosen device: battery, device info and the optional command
 * channel. Reconnects with exponential backoff after an unexpected drop.
 *
 * This is independent of the phone-level link the CMF Watch app owns. If the watch accepts only one
 * connection the attempt times out and the state becomes [ConnectionState.Failed] with an
 * explanation; nothing else is disturbed.
 */
class WatchConnection(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onCommandBytes: (ByteArray) -> Unit,
) {
    private val _session = MutableStateFlow(WatchSession())
    val session: StateFlow<WatchSession> = _session.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var target: WatchDevice? = null
    private var wanted = false
    private var autoReconnect = true
    private var attempt = 0
    private var reconnectJob: Job? = null
    private var timeoutJob: Job? = null

    private val queue = ArrayDeque<() -> Boolean>()
    private var busy = false
    private val lock = Any()

    fun connect(device: WatchDevice, autoReconnect: Boolean) {
        disconnect()
        this.autoReconnect = autoReconnect
        wanted = true
        target = device
        attempt = 0
        _session.value = WatchSession(state = ConnectionState.Connecting(1), device = device)
        open()
    }

    fun disconnect() {
        wanted = false
        reconnectJob?.cancel()
        timeoutJob?.cancel()
        closeGatt()
        synchronized(lock) { queue.clear(); busy = false }
        _session.value = WatchSession(state = ConnectionState.Idle, device = target)
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        val g = gatt ?: return
        gatt = null
        try {
            g.disconnect()
            g.close()
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun open() {
        val device = target ?: return
        closeGatt()
        _session.value = _session.value.copy(state = ConnectionState.Connecting(attempt + 1))
        try {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: return fail("Bluetooth is not available")
            val remote = adapter.getRemoteDevice(device.address)
            gatt = remote.connectGatt(context, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            return fail("Bluetooth permission not granted")
        } catch (e: IllegalArgumentException) {
            return fail("Invalid device address")
        }
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            onDrop("The watch did not answer. It may be holding its only connection for the CMF Watch app.")
        }
    }

    private fun fail(reason: String) {
        wanted = false
        _session.value = _session.value.copy(state = ConnectionState.Failed(reason))
    }

    /** Called when a connection attempt fails or an established link drops. */
    private fun onDrop(reason: String) {
        timeoutJob?.cancel()
        closeGatt()
        synchronized(lock) { queue.clear(); busy = false }
        if (!wanted) {
            _session.value = _session.value.copy(state = ConnectionState.Idle)
            return
        }
        if (!autoReconnect || attempt >= MAX_ATTEMPTS) {
            fail(if (autoReconnect) "Gave up after $MAX_ATTEMPTS attempts. $reason" else reason)
            return
        }
        attempt++
        val wait = minOf(30_000L, 1_000L shl (attempt - 1))
        _session.value = _session.value.copy(state = ConnectionState.Reconnecting(attempt, wait))
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(wait)
            if (wanted) open()
        }
    }

    // --- operation queue: GATT allows one outstanding operation at a time ---

    private fun enqueue(op: () -> Boolean) {
        synchronized(lock) { queue.addLast(op) }
        pump()
    }

    private fun pump() {
        synchronized(lock) {
            if (busy) return
            while (true) {
                val op = queue.removeFirstOrNull() ?: return
                busy = true
                if (runCatching { op() }.getOrDefault(false)) return
                busy = false
            }
        }
    }

    private fun opDone() {
        synchronized(lock) { busy = false }
        pump()
    }

    @SuppressLint("MissingPermission")
    private fun readChar(g: BluetoothGatt, service: UUID, chr: UUID) {
        val c = g.getService(service)?.getCharacteristic(chr) ?: return
        enqueue { g.readCharacteristic(c) }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun subscribe(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
        enqueue {
            g.setCharacteristicNotification(c, true)
            val d = c.getDescriptor(CCC) ?: return@enqueue false
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(d)
            }
        }
    }

    private fun handleValue(uuid: UUID, value: ByteArray?) {
        if (value == null || value.isEmpty()) return
        when (uuid) {
            CHR_BATTERY_LEVEL ->
                _session.value = _session.value.copy(batteryPercent = (value[0].toInt() and 0xFF).coerceIn(0, 100))
            CHR_MANUFACTURER ->
                _session.value = _session.value.copy(manufacturer = String(value, Charsets.UTF_8).trim())
            CHR_MODEL ->
                _session.value = _session.value.copy(model = String(value, Charsets.UTF_8).trim())
            RemoteProtocol.COMMAND_UUID -> onCommandBytes(value)
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g !== gatt) return
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                timeoutJob?.cancel()
                attempt = 0
                _session.value = _session.value.copy(state = ConnectionState.Connected)
                try {
                    g.discoverServices()
                } catch (_: SecurityException) {
                    onDrop("Bluetooth permission not granted")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onDrop(if (status == 0) "The watch disconnected." else "Connection lost (status $status).")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g !== gatt || status != BluetoothGatt.GATT_SUCCESS) return
            val names = g.services.map { it.uuid.shortName() }
            val command = g.getService(RemoteProtocol.SERVICE_UUID)?.getCharacteristic(RemoteProtocol.COMMAND_UUID)
            _session.value = _session.value.copy(services = names, hasCommandChannel = command != null)

            readChar(g, SVC_BATTERY, CHR_BATTERY_LEVEL)
            readChar(g, SVC_DEVICE_INFO, CHR_MANUFACTURER)
            readChar(g, SVC_DEVICE_INFO, CHR_MODEL)
            g.getService(SVC_BATTERY)?.getCharacteristic(CHR_BATTERY_LEVEL)?.let { subscribe(g, it) }
            command?.let { subscribe(g, it) }
        }

        // API 33+
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) handleValue(c.uuid, value)
            opDone()
        }

        // API 26-32
        @Deprecated("Deprecated in API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) handleValue(c.uuid, c.value)
            opDone()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            handleValue(c.uuid, value)
        }

        @Deprecated("Deprecated in API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            handleValue(c.uuid, c.value)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            opDone()
        }
    }

    private fun UUID.shortName(): String {
        val s = toString()
        val short = if (s.endsWith("-0000-1000-8000-00805f9b34fb")) s.substring(4, 8).uppercase() else null
        return when (short) {
            "1800" -> "Generic Access"
            "1801" -> "Generic Attribute"
            "180A" -> "Device Information"
            "180F" -> "Battery"
            "1812" -> "HID"
            "180D" -> "Heart Rate"
            null -> if (this == RemoteProtocol.SERVICE_UUID) "Social Remote command channel" else "Custom service"
            else -> "0x$short"
        }
    }
}
