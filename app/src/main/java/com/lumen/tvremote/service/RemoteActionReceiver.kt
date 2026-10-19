package com.lumen.tvremote.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lumen.tvremote.app
import com.lumen.tvremote.protocol.TvKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Executes remote keys from the notification remote and the home-screen widget. */
class RemoteActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_KEY) return
        val key = intent.getStringExtra(EXTRA_KEY)?.let(TvKey::fromName) ?: return
        val app = context.app
        val pending = goAsync()
        scope.launch {
            try {
                app.controller.sendKeyWhenReady(key)
                app.scheduleBackgroundDisconnect()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_KEY = "com.lumen.tvremote.action.KEY"
        const val EXTRA_KEY = "key"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun pendingIntent(context: Context, key: TvKey): PendingIntent {
            val intent = Intent(context, RemoteActionReceiver::class.java)
                .setAction(ACTION_KEY)
                .putExtra(EXTRA_KEY, key.name)
            return PendingIntent.getBroadcast(
                context,
                key.ordinal,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
