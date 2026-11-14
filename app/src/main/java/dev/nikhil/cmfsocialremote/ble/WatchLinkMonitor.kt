package dev.nikhil.cmfsocialremote.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import dev.nikhil.cmfsocialremote.data.Permissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks which Bluetooth devices are currently linked to the phone (any app), using Android's
 * ACL connection broadcasts. This works for the CMF Watch Pro even though the CMF Watch app owns
 * the actual data connection.
 */
class WatchLinkMonitor(private val context: Context) {
    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    val connected: StateFlow<Set<String>> = _connected.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val address = intent.device()?.address ?: return
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> _connected.value = _connected.value + address
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> _connected.value = _connected.value - address
            }
        }
    }

    /** Safe to call repeatedly; does nothing until the Bluetooth permission is granted. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (!Permissions.hasConnect(context)) return
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            registered = true
        }
        // Seed with devices that were already connected before we started listening.
        try {
            val manager = context.getSystemService(BluetoothManager::class.java)
            val now = manager?.getConnectedDevices(BluetoothProfile.GATT).orEmpty().map { it.address }
            if (now.isNotEmpty()) _connected.value = _connected.value + now
        } catch (_: SecurityException) {
        }
    }

    private fun Intent.device(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
}
