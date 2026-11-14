package dev.nikhil.cmfsocialremote.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.nikhil.cmfsocialremote.model.ActionResult
import dev.nikhil.cmfsocialremote.model.AppConfig
import dev.nikhil.cmfsocialremote.model.ContentSurface
import dev.nikhil.cmfsocialremote.model.LikeStrategy
import dev.nikhil.cmfsocialremote.model.RemoteAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Performs scroll and like actions in the supported social apps.
 *
 * Privacy boundaries (also enforced by accessibility_service_config.xml and the manifest):
 *  - only the four supported apps' packages are delivered to this service;
 *  - nothing happens unless a command arrives from the watch / test dial;
 *  - password fields are skipped, no text is stored or logged, and the app has no INTERNET permission;
 *  - screen text is only hashed in memory to detect "the screen changed" after a swipe.
 */
class ControlAccessibilityService : AccessibilityService() {
    private val gestureLock = Mutex()
    private val scrollEvents = AtomicInteger(0)

    override fun onServiceConnected() {
        super.onServiceConnected()
        ServiceBridge.attach(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ServiceBridge.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ServiceBridge.detach(this)
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> scrollEvents.incrementAndGet()
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> ServiceBridge.onSupportedAppForeground?.invoke()
        }
    }

    suspend fun perform(action: RemoteAction, profile: AppProfile, config: AppConfig): ActionResult {
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString()
        if (root == null || pkg == null || pkg !in profile.app.packages) {
            return ActionResult.WrongApp("Open ${profile.app.title} first — it isn't the app on screen")
        }
        return when (action) {
            RemoteAction.NEXT -> scroll(forward = true, root = root, config = config)
            RemoteAction.PREVIOUS -> scroll(forward = false, root = root, config = config)
            RemoteAction.LIKE_TOGGLE -> like(root, profile, config)
        }
    }

    // ---------------------------------------------------------------- scrolling

    private suspend fun scroll(forward: Boolean, root: AccessibilityNodeInfo, config: AppConfig): ActionResult {
        val before = screenSignature(root)
        val eventsBefore = scrollEvents.get()
        val delivered = gestureLock.withLock { swipe(forward, config.surface) }
        if (!delivered) return ActionResult.Failed("Android rejected the swipe")

        val label = if (forward) "next" else "previous"
        repeat(8) {
            delay(150)
            val now = rootInActiveWindow
            if (scrollEvents.get() != eventsBefore || (now != null && screenSignature(now) != before)) {
                return ActionResult.Confirmed("Moved to $label")
            }
        }
        return ActionResult.Unconfirmed(
            "Swipe delivered, but the screen didn't change (end of list, still loading, or the app ignored it)",
        )
    }

    private suspend fun swipe(forward: Boolean, surface: ContentSurface): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val (low, high, duration) = when (surface) {
            ContentSurface.REELS -> Triple(h * 0.74f, h * 0.26f, 170L)
            ContentSurface.FEED -> Triple(h * 0.70f, h * 0.35f, 380L)
        }
        // "Next" = finger moves up the screen.
        val path = Path().apply {
            moveTo(w * 0.5f, if (forward) low else high)
            lineTo(w * 0.5f, if (forward) high else low)
        }
        return dispatch(GestureDescription.StrokeDescription(path, 0, duration))
    }

    // ---------------------------------------------------------------- liking

    private suspend fun like(root: AccessibilityNodeInfo, profile: AppProfile, config: AppConfig): ActionResult {
        val title = profile.app.title
        val node = LikeNodeFinder.find(root, profile)

        if (node != null) {
            if (node.liked && !config.allowUnlike) {
                return ActionResult.Unsupported("Already liked — unliking from the watch is off for $title")
            }
            if (config.likeStrategy != LikeStrategy.DOUBLE_TAP_ONLY) {
                if (node.clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return verifyLikeChanged(profile, wasLiked = node.liked)
                }
                if (config.likeStrategy == LikeStrategy.BUTTON_ONLY) {
                    return ActionResult.Failed("The Like button didn't respond")
                }
            }
        } else if (config.likeStrategy == LikeStrategy.BUTTON_ONLY) {
            return ActionResult.Unsupported("$title didn't expose a Like button on this screen")
        }

        if (!profile.doubleTapLikes) return ActionResult.Unsupported("$title can't be liked by double-tap")
        if (node?.liked == true) {
            return ActionResult.Unsupported("Already liked — a double-tap can't unlike in $title")
        }

        val delivered = gestureLock.withLock { dispatch(*doubleTapStrokes()) }
        if (!delivered) return ActionResult.Failed("Android rejected the double-tap")

        delay(500)
        val after = rootInActiveWindow?.let { LikeNodeFinder.find(it, profile) }
        return if (after?.liked == true) {
            ActionResult.Confirmed("Liked")
        } else {
            ActionResult.Unconfirmed("Double-tap delivered, but $title doesn't show the Like state, so it can't be confirmed")
        }
    }

    private suspend fun verifyLikeChanged(profile: AppProfile, wasLiked: Boolean): ActionResult {
        repeat(4) {
            delay(200)
            val after = rootInActiveWindow?.let { LikeNodeFinder.find(it, profile) }
            if (after != null && after.liked != wasLiked) {
                return ActionResult.Confirmed(if (after.liked) "Liked" else "Unliked")
            }
        }
        return ActionResult.Unconfirmed("Tapped the Like button, but its state didn't visibly change")
    }

    private fun doubleTapStrokes(): Array<GestureDescription.StrokeDescription> {
        val dm = resources.displayMetrics
        val path = Path().apply { moveTo(dm.widthPixels * 0.5f, dm.heightPixels * 0.42f) }
        return arrayOf(
            GestureDescription.StrokeDescription(path, 0, 40),
            GestureDescription.StrokeDescription(path, 130, 40),
        )
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun dispatch(vararg strokes: GestureDescription.StrokeDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val builder = GestureDescription.Builder()
            strokes.forEach { builder.addStroke(it) }
            val accepted = dispatchGesture(
                builder.build(),
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null,
            )
            if (!accepted && cont.isActive) cont.resume(false)
        }

    /** Hash of visible, non-password text. Used only to notice that the screen changed. */
    private fun screenSignature(root: AccessibilityNodeInfo): Int {
        var hash = 17
        var visited = 0
        fun visit(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 30 || visited > 400) return
            visited++
            if (n.isVisibleToUser && !n.isPassword) {
                val text = n.text?.toString()
                // Skip clock-like text (video timers) so playback alone doesn't look like a scroll.
                if (text == null || !CLOCK.containsMatchIn(text)) hash = 31 * hash + (text?.hashCode() ?: 0)
                hash = 31 * hash + (n.contentDescription?.toString()?.hashCode() ?: 0)
            }
            for (i in 0 until n.childCount) visit(n.getChild(i), depth + 1)
        }
        visit(root, 0)
        return hash
    }

    private companion object {
        val CLOCK = Regex("""\d{1,2}:\d{2}""")
    }
}

/** A Like control found on screen. */
class LikeNode(val clickTarget: AccessibilityNodeInfo, val liked: Boolean)

object LikeNodeFinder {
    fun find(root: AccessibilityNodeInfo, p: AppProfile): LikeNode? {
        var best: LikeNode? = null
        var bestScore = -1
        var bestLeft = -1
        var visited = 0

        fun visit(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40 || visited > 1500) return
            visited++
            if (!n.isVisibleToUser || n.isPassword) return

            val id = n.viewIdResourceName?.substringAfterLast('/').orEmpty()
            val label = (n.contentDescription?.toString() ?: n.text?.toString()).orEmpty().lowercase()
            val idHit = id.isNotEmpty() && p.likeIdHints.any { id == it }
            val descHit = label.isNotEmpty() &&
                p.likeDescHints.any { label.contains(it) } &&
                p.excludeHints.none { label.contains(it) }

            if (idHit || descHit) {
                val target = clickableSelfOrAncestor(n)
                if (target != null) {
                    val score = (if (idHit) 10 else 0) + (if (descHit) 5 else 0) + (if (n.isClickable) 1 else 0)
                    val bounds = Rect().also { n.getBoundsInScreen(it) }
                    if (score > bestScore || (score == bestScore && bounds.left > bestLeft)) {
                        val liked = p.likedStateHints.any { label.contains(it) } ||
                            n.isSelected || (n.isCheckable && n.isChecked)
                        best = LikeNode(target, liked)
                        bestScore = score
                        bestLeft = bounds.left
                    }
                }
            }
            for (i in 0 until n.childCount) visit(n.getChild(i), depth + 1)
        }

        visit(root, 0)
        return best
    }

    private fun clickableSelfOrAncestor(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = n
        var hops = 0
        while (cur != null && hops <= 3) {
            if (cur.isClickable) return cur
            cur = cur.parent
            hops++
        }
        return null
    }
}
