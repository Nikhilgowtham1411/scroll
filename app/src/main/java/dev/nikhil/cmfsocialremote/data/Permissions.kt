package dev.nikhil.cmfsocialremote.data

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.nikhil.cmfsocialremote.control.ControlAccessibilityService

data class PermissionState(
    val bluetoothOn: Boolean = false,
    val blePermission: Boolean = false,
    val notifications: Boolean = false,
    val accessibility: Boolean = false,
)

object Permissions {
    fun blePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun hasBle(ctx: Context): Boolean = blePermissions().all { granted(ctx, it) }

    fun hasConnect(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || granted(ctx, Manifest.permission.BLUETOOTH_CONNECT)

    fun hasNotifications(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)

    /** True when the user has switched our accessibility service on in system settings. */
    fun isAccessibilityEnabled(ctx: Context): Boolean {
        val me = ComponentName(ctx, ControlAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
