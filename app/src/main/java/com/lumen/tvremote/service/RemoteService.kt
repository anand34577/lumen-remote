package com.lumen.tvremote.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lumen.tvremote.MainActivity
import com.lumen.tvremote.R
import com.lumen.tvremote.app
import com.lumen.tvremote.connection.ConnectionStatus
import com.lumen.tvremote.connection.TvState
import com.lumen.tvremote.protocol.TvKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Foreground service that shows a full remote in the notification shade and lock screen. */
class RemoteService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        val initial = buildNotification(app.controller.state.value)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, initial, type)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot enter foreground", e)
            stopSelf()
            return
        }
        scope.launch {
            app.controller.state
                .map { Triple(it.device?.name, it.connection, it.volume?.muted) }
                .distinctUntilChanged()
                .collect {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(app.controller.state.value))
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            scope.launch { app.store.updateSettings { it.copy(persistentNotification = false) } }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(state: TvState): Notification {
        val title = state.device?.name ?: getString(R.string.app_name)
        val status = when (val c = state.connection) {
            ConnectionStatus.Connected -> getString(R.string.status_connected)
            ConnectionStatus.Connecting -> getString(R.string.status_connecting)
            is ConnectionStatus.Reconnecting -> getString(R.string.status_reconnecting)
            ConnectionStatus.NeedsPairing -> getString(R.string.status_needs_pairing)
            is ConnectionStatus.Offline -> getString(R.string.status_offline)
            ConnectionStatus.Idle -> getString(R.string.status_tap_to_connect)
        }

        val small = RemoteViews(packageName, R.layout.notification_remote_small).apply {
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.status, status)
            bindKeys(this, SMALL_KEYS)
        }
        val big = RemoteViews(packageName, R.layout.notification_remote_big).apply {
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.status, status)
            bindKeys(this, BIG_KEYS)
        }

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val hide = PendingIntent.getService(
            this, 1,
            Intent(this, RemoteService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_remote)
            .setContentTitle(title)
            .setContentText(status)
            .setContentIntent(open)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.action_hide), hide)
            .build()
    }

    private fun bindKeys(views: RemoteViews, keys: Map<Int, TvKey>) {
        keys.forEach { (id, key) -> views.setOnClickPendingIntent(id, RemoteActionReceiver.pendingIntent(this, key)) }
    }

    companion object {
        private const val TAG = "RemoteService"
        private const val CHANNEL_ID = "remote"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_HIDE = "com.lumen.tvremote.action.HIDE_NOTIFICATION"

        private val SMALL_KEYS = mapOf(
            R.id.btn_vol_down to TvKey.VOLUME_DOWN,
            R.id.btn_play to TvKey.PLAY_PAUSE,
            R.id.btn_vol_up to TvKey.VOLUME_UP,
        )

        private val BIG_KEYS = mapOf(
            R.id.btn_up to TvKey.UP,
            R.id.btn_down to TvKey.DOWN,
            R.id.btn_left to TvKey.LEFT,
            R.id.btn_right to TvKey.RIGHT,
            R.id.btn_ok to TvKey.CENTER,
            R.id.btn_back to TvKey.BACK,
            R.id.btn_home to TvKey.HOME,
            R.id.btn_power to TvKey.POWER,
            R.id.btn_mute to TvKey.MUTE,
            R.id.btn_vol_down to TvKey.VOLUME_DOWN,
            R.id.btn_play to TvKey.PLAY_PAUSE,
            R.id.btn_vol_up to TvKey.VOLUME_UP,
        )

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_remote), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_remote_description)
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, RemoteService::class.java))
            } catch (e: Exception) {
                // Background start restrictions; the service starts the next time the app is opened.
                Log.w(TAG, "Cannot start notification remote now", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RemoteService::class.java))
        }
    }
}
