package dev.nikhil.cmfsocialremote.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dev.nikhil.cmfsocialremote.R
import dev.nikhil.cmfsocialremote.SocialRemoteApp
import dev.nikhil.cmfsocialremote.gesture.MediaButtonSource
import dev.nikhil.cmfsocialremote.ui.MainActivity

/**
 * Foreground service that keeps the remote alive while the screen is off or another app is open.
 * It owns the media session that receives the watch's Music-screen commands.
 */
class RemoteService : Service() {
    private var source: MediaButtonSource? = null

    private val hub get() = (application as SocialRemoteApp).hub

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        val settings = hub.settings.value
        source = MediaButtonSource(this) { input, src -> hub.onInput(input, src) }.also {
            it.start(settings.activeApp, settings.configFor(settings.activeApp).surface)
            hub.attachMedia(it)
        }
        hub.setRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        hub.attachMedia(null)
        source?.release()
        source = null
        hub.setRunning(false)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RemoteService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.notif_stop), stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "remote"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "dev.nikhil.cmfsocialremote.STOP"
    }
}
