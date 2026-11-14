package dev.nikhil.cmfsocialremote.gesture

import android.os.SystemClock
import dev.nikhil.cmfsocialremote.data.Settings
import dev.nikhil.cmfsocialremote.model.RemoteGesture
import dev.nikhil.cmfsocialremote.model.WatchInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Turns raw inputs into the gestures the product defines:
 * swipe up, swipe down, double tap (like / unlike) and single tap (no action).
 *
 * A raw [WatchInput.Press] is ambiguous until the double-press window expires, so a single tap is
 * reported late — which is fine because a single tap does nothing.
 *
 * [scope] must run on the main thread; every call is hopped onto it, so [handle] is thread-safe.
 */
class GestureEngine(
    private val scope: CoroutineScope,
    private val settings: () -> Settings,
    private val onGesture: (RemoteGesture, String) -> Unit,
    private val onDropped: (RemoteGesture, String) -> Unit,
) {
    private var pendingPress: Job? = null
    private var lastScrollAt = 0L

    fun handle(input: WatchInput, source: String) {
        scope.launch {
            when (input) {
                WatchInput.Up -> scroll(RemoteGesture.SWIPE_UP, source)
                WatchInput.Down -> scroll(RemoteGesture.SWIPE_DOWN, source)
                WatchInput.DoubleTap -> {
                    pendingPress?.cancel()
                    pendingPress = null
                    onGesture(RemoteGesture.DOUBLE_TAP, source)
                }
                WatchInput.Press -> press(source)
            }
        }
    }

    private fun scroll(gesture: RemoteGesture, source: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastScrollAt < settings().scrollCooldownMs) {
            onDropped(gesture, source)
            return
        }
        lastScrollAt = now
        onGesture(gesture, source)
    }

    private fun press(source: String) {
        val pending = pendingPress
        if (pending != null && pending.isActive) {
            pending.cancel()
            pendingPress = null
            onGesture(RemoteGesture.DOUBLE_TAP, source)
            return
        }
        pendingPress = scope.launch {
            delay(settings().doublePressWindowMs)
            pendingPress = null
            onGesture(RemoteGesture.SINGLE_TAP, source)
        }
    }
}
