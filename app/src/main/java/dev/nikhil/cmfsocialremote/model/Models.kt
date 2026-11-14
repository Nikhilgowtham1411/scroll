package dev.nikhil.cmfsocialremote.model

/** The four apps with a control profile. */
enum class SocialApp(
    val title: String,
    val subtitle: String,
    val packages: List<String>,
    val monogram: String,
) {
    INSTAGRAM("Instagram", "Reels and Feed", listOf("com.instagram.android"), "IG"),
    YOUTUBE("YouTube", "Shorts", listOf("com.google.android.youtube"), "YT"),
    FACEBOOK("Facebook", "Reels", listOf("com.facebook.katana", "com.facebook.lite"), "f"),
    TIKTOK("TikTok", "For You feed", listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), "TT");

    companion object {
        fun fromName(name: String?): SocialApp =
            entries.firstOrNull { it.name == name } ?: INSTAGRAM
    }
}

/** Reels = full-screen pager (one fast swipe per item). Feed = scrolling list (slower, shorter swipe). */
enum class ContentSurface(val label: String) { REELS("Reels"), FEED("Feed") }

enum class LikeStrategy(val label: String) {
    BUTTON_THEN_DOUBLE_TAP("Button, then double-tap"),
    BUTTON_ONLY("Like button only"),
    DOUBLE_TAP_ONLY("Double-tap only"),
}

/** Per-app gesture configuration. */
data class AppConfig(
    val invertScroll: Boolean = false,
    val likeStrategy: LikeStrategy = LikeStrategy.BUTTON_THEN_DOUBLE_TAP,
    val allowUnlike: Boolean = true,
    val surface: ContentSurface = ContentSurface.REELS,
)

/** What a command source (watch media keys, BLE channel, on-screen test dial) reports. */
sealed interface WatchInput {
    data object Up : WatchInput
    data object Down : WatchInput

    /** A single raw press (e.g. the watch's play/pause). The gesture engine decides single vs double. */
    data object Press : WatchInput

    /** A source that already detected a double tap. */
    data object DoubleTap : WatchInput
}

enum class RemoteGesture(val label: String) {
    SWIPE_UP("Swipe up"),
    SWIPE_DOWN("Swipe down"),
    DOUBLE_TAP("Double tap"),
    SINGLE_TAP("Single tap"),
}

enum class RemoteAction { NEXT, PREVIOUS, LIKE_TOGGLE }

/**
 * Result of a phone-side action. The UI only reports success for [Confirmed]; everything
 * else is shown as not confirmed, unsupported or failed.
 */
sealed interface ActionResult {
    val message: String

    /** The phone observed the expected change on screen. */
    data class Confirmed(override val message: String) : ActionResult

    /** The input was delivered but nothing on screen proved it worked. */
    data class Unconfirmed(override val message: String) : ActionResult

    /** This app / screen does not support the interaction. */
    data class Unsupported(override val message: String) : ActionResult

    data class WrongApp(override val message: String) : ActionResult
    data class NotEnabled(override val message: String = "Accessibility access is off") : ActionResult
    data class Failed(override val message: String) : ActionResult

    /** Deliberately nothing to do (single tap, rate limit). */
    data class Ignored(override val message: String) : ActionResult
}

/** [result] is null while the action is still running. */
data class GestureEvent(
    val id: Long,
    val gesture: RemoteGesture,
    val source: String,
    val result: ActionResult?,
)
