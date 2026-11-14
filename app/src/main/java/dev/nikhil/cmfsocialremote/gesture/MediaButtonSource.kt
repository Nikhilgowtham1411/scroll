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
    private val onSignal: (String) -> Unit = {},
) {
    private var keepAlive: android.media.AudioTrack? = null
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
                    onSignal("key " + KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_"))
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

            override fun onSkipToNext() { onSignal("skip next"); emit(WatchInput.Up) }
            override fun onSkipToPrevious() { onSignal("skip previous"); emit(WatchInput.Down) }
            override fun onPlay() { onSignal("play"); emit(WatchInput.Press) }
            override fun onPause() { onSignal("pause"); emit(WatchInput.Press) }
        })
        session = s
        setLabel(app, surface)
        publishState(playing = true)
        s.isActive = true
        startKeepAlive()
    }

    /**
     * Android prefers sessions that are really playing audio when it picks who gets media keys.
     * A looping silent track (no audio focus requested, so other apps are unaffected) keeps ours eligible.
     */
    private fun startKeepAlive() {
        if (keepAlive != null) return
        try {
            val frames = 4410
            val track = android.media.AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(44100)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(frames * 2)
                .build()
            track.write(ByteArray(frames * 2), 0, frames * 2)
            track.setLoopPoints(0, frames, -1)
            track.play()
            keepAlive = track
        } catch (_: Exception) {
            keepAlive = null
        }
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
        keepAlive?.let { runCatching { it.stop(); it.release() } }
        keepAlive = null
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
