package dev.nikhil.cmfsocialremote.control

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dev.nikhil.cmfsocialremote.model.SocialApp

/**
 * How to find the Like control in one app. These are heuristics: the apps do not publish their
 * accessibility trees and change them between releases, so each profile is a best-effort set of
 * hints, not a guarantee. When no Like button is found the app falls back to a double-tap (if the
 * app supports it) and reports the result as "not confirmed".
 */
data class AppProfile(
    val app: SocialApp,
    /** Resource-id names (the part after ":id/") that identify the Like button. */
    val likeIdHints: List<String>,
    /** Lower-case fragments of the Like button's content description. */
    val likeDescHints: List<String>,
    /** Fragments that mean the item is already liked. Checked before [likeDescHints]. */
    val likedStateHints: List<String>,
    /** Fragments that disqualify a node (comment likes, "liked by ..." text). */
    val excludeHints: List<String>,
    /** Whether double-tapping the video likes it in this app. */
    val doubleTapLikes: Boolean,
    /** Best-effort deep links that open the right surface inside the app. */
    val deepLinks: List<String>,
)

object AppProfiles {
    private val common = listOf("comment", "liked by", "reply", "dislike")

    private val all = mapOf(
        SocialApp.INSTAGRAM to AppProfile(
            app = SocialApp.INSTAGRAM,
            likeIdHints = listOf("clips_like_button", "row_feed_button_like", "like_button"),
            likeDescHints = listOf("like"),
            likedStateHints = listOf("unlike", "liked"),
            excludeHints = common,
            doubleTapLikes = true,
            deepLinks = listOf("https://www.instagram.com/reels/"),
        ),
        SocialApp.YOUTUBE to AppProfile(
            app = SocialApp.YOUTUBE,
            likeIdHints = listOf("reel_like_button", "like_button"),
            likeDescHints = listOf("like this video", "like"),
            likedStateHints = listOf("unlike", "liked", "remove like", "you like this"),
            excludeHints = common,
            doubleTapLikes = true,
            deepLinks = listOf("https://www.youtube.com/shorts"),
        ),
        SocialApp.FACEBOOK to AppProfile(
            app = SocialApp.FACEBOOK,
            likeIdHints = listOf("like_button"),
            likeDescHints = listOf("like"),
            likedStateHints = listOf("unlike", "liked", "remove like"),
            excludeHints = common,
            doubleTapLikes = true,
            deepLinks = emptyList(),
        ),
        SocialApp.TIKTOK to AppProfile(
            app = SocialApp.TIKTOK,
            likeIdHints = emptyList(),
            likeDescHints = listOf("like video", "like"),
            likedStateHints = listOf("unlike", "liked", "remove like"),
            excludeHints = common,
            doubleTapLikes = true,
            deepLinks = emptyList(),
        ),
    )

    fun of(app: SocialApp): AppProfile = all.getValue(app)
}

sealed interface LaunchResult {
    data class Launched(val how: String) : LaunchResult
    data object NotInstalled : LaunchResult
    data object Failed : LaunchResult
}

/** Opens a supported app using its deep link when available, otherwise its launcher intent. */
object AppLauncher {
    @Suppress("DEPRECATION")
    fun installedPackage(ctx: Context, app: SocialApp): String? =
        app.packages.firstOrNull { pkg ->
            try {
                ctx.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

    fun launch(ctx: Context, app: SocialApp): LaunchResult {
        val pkg = installedPackage(ctx, app) ?: return LaunchResult.NotInstalled
        val profile = AppProfiles.of(app)
        for (link in profile.deepLinks) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(ctx.packageManager) != null) {
                try {
                    ctx.startActivity(intent)
                    return LaunchResult.Launched("deep link")
                } catch (_: ActivityNotFoundException) {
                }
            }
        }
        val launcher = ctx.packageManager.getLaunchIntentForPackage(pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return LaunchResult.Failed
        return try {
            ctx.startActivity(launcher)
            LaunchResult.Launched("app launcher")
        } catch (_: ActivityNotFoundException) {
            LaunchResult.Failed
        }
    }
}
