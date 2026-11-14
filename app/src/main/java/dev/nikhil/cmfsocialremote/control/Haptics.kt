package dev.nikhil.cmfsocialremote.control

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Phone-side haptics only. The CMF Watch Pro offers no way for a third-party app to vibrate the watch. */
@Suppress("DEPRECATION")
class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** Short tick: the phone confirmed the action. */
    fun confirmed() {
        vibrator?.takeIf { it.hasVibrator() }
            ?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Double buzz: not confirmed, unsupported or failed. */
    fun problem() {
        vibrator?.takeIf { it.hasVibrator() }
            ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 70, 30), -1))
    }
}
