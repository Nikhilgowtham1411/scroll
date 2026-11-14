package dev.nikhil.cmfsocialremote.gesture

import dev.nikhil.cmfsocialremote.model.WatchInput
import java.util.UUID

/**
 * Reference BLE command channel defined by this project.
 *
 * A watch or BLE remote that can host a custom GATT service exposes [SERVICE_UUID] with one
 * notify characteristic, [COMMAND_UUID]. Each notification carries one byte:
 *
 *   0x01 = swipe up (next)   0x02 = swipe down (previous)   0x03 = double tap (like / unlike)
 *
 * The original CMF Watch Pro cannot expose this service (closed OS). The channel exists so the app
 * works unchanged with any device that can, e.g. an ESP32 remote or a future watch-side app.
 */
object RemoteProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("c3f1a000-5a1d-4b6e-9c0e-0c1f5c0e5a11")
    val COMMAND_UUID: UUID = UUID.fromString("c3f1a001-5a1d-4b6e-9c0e-0c1f5c0e5a11")

    fun decode(bytes: ByteArray): WatchInput? = when (bytes.firstOrNull()?.toInt()?.and(0xFF)) {
        0x01 -> WatchInput.Up
        0x02 -> WatchInput.Down
        0x03 -> WatchInput.DoubleTap
        else -> null
    }
}
