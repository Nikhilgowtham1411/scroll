package dev.nikhil.cmfsocialremote.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import dev.nikhil.cmfsocialremote.data.Permissions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class ScanFailure(message: String) : Exception(message)

/** Finds nearby BLE devices and lists devices already paired with the phone. */
class BleScanner(private val context: Context) {
    private val adapter
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun isBluetoothOn(): Boolean = try {
        adapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }

    @SuppressLint("MissingPermission")
    fun bonded(): List<WatchDevice> {
        if (!Permissions.hasConnect(context)) return emptyList()
        return try {
            adapter?.bondedDevices.orEmpty().map {
                WatchDevice(it.address, it.name ?: "Unknown device", null, true)
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun scan(timeoutMs: Long = 12_000): Flow<WatchDevice> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || !isBluetoothOn()) {
            close(ScanFailure("Bluetooth is off"))
            return@callbackFlow
        }
        if (!Permissions.hasBle(context)) {
            close(ScanFailure("Bluetooth permission not granted"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toWatchDevice())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { trySend(it.toWatchDevice()) }
            }

            override fun onScanFailed(errorCode: Int) {
                close(ScanFailure("Scan failed (code $errorCode)"))
            }
        }

        try {
            scanner.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                callback,
            )
        } catch (e: SecurityException) {
            close(ScanFailure("Bluetooth permission not granted"))
            return@callbackFlow
        }

        val timer = launch {
            delay(timeoutMs)
            close()
        }
        awaitClose {
            timer.cancel()
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun ScanResult.toWatchDevice(): WatchDevice {
        val name = scanRecord?.deviceName
            ?: try {
                device.name
            } catch (_: SecurityException) {
                null
            }
        return WatchDevice(
            address = device.address,
            name = name ?: "Unnamed device",
            rssi = rssi,
            bonded = device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED,
        )
    }
}
