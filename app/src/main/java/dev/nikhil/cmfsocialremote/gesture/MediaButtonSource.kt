package dev.nikhil.cmfsocialremote.gesture

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.view.KeyEvent
import dev.nikhil.cmfsocialremote.model.ContentSurface
import dev.nikhil.cmfsocialremote.model.SocialApp
import dev.nikhil.cmfsocialremote.model.WatchInput

/**
 * The compatibility path for the original CMF Watch Pro.
 *
 * The watch's Music screen (next / previous / play-pause) is relayed to the phone by the CMF Watch
 * app as ordinary Android media commands. We hold an active [MediaSession], so Android routes those
 * commands to us instead of a music player:
 *
 *   next -> swipe up     previous -> swipe down     play/pause pressed twice -> double tap
 *
 * We never ask for audio focus and play no audio, so Reels / Shorts audio is unaffected.
 *
 * Caveat (documented in docs/COMPATIBILITY.md): Android sends media keys to the session that most
 * recently became "playing". [claim] re-asserts ours; the app calls it whenever a supported app
 * comes to the foreground.
 */
class MediaButtonSource(
    private val context: Context,
    private val onInput: (WatchInput, String) -> Unit,
) {
    private var session: MediaSession? = null
    private var title = ""
    private var subtitle = ""

    fun start(app: SocialApp, surface: ContentSurface) {
        if (session != null) return
        val s = MediaSession(context, "CmfSocialRemote")
        s.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                val event = mediaButtonIntent.keyEvent() ?: return true
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT -> emit(WatchInput.Up)
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> emit(WatchInput.Down)
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_MEDIA_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK -> emit(WatchInput.Press)
                    }
                }
                return true
            }

            override fun onSkipToNext() = emit(WatchInput.Up)
            override fun onSkipToPrevious() = emit(WatchInput.Down)
            override fun onPlay() = emit(WatchInput.Press)
            override fun onPause() = emit(WatchInput.Press)
        })
        session = s
        setLabel(app, surface)
        publishState(playing = true)
        s.isActive = true
    }

    /** Text the watch's Music screen may show as the "track". */
    fun setLabel(app: SocialApp, surface: ContentSurface) {
        title = "${app.title} ${if (app == SocialApp.INSTAGRAM) surface.label else app.subtitle}"
        subtitle = "▲ next   ▼ previous   ⏯⏯ like"
        val s = session ?: return
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle)
                .build(),
        )
    }

    /** Re-take priority for media buttons by cycling the session through paused -> playing. */
    fun claim() {
        val s = session ?: return
        publishState(playing = false)
        publishState(playing = true)
        s.isActive = true
    }

    fun release() {
        session?.run {
            isActive = false
            release()
        }
        session = null
    }

    private fun emit(input: WatchInput) {
        onInput(input, SOURCE)
        // Keep reporting "playing" so the watch UI doesn't flip to a paused state we never honour.
        publishState(playing = true)
    }

    private fun publishState(playing: Boolean) {
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1f,
                )
                .build(),
        )
    }

    private fun Intent.keyEvent(): KeyEvent? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }

    companion object {
        const val SOURCE = "Watch music controls"
    }
}
