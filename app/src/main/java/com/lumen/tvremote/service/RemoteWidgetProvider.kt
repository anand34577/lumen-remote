package com.lumen.tvremote.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lumen.tvremote.MainActivity
import com.lumen.tvremote.R
import com.lumen.tvremote.protocol.TvKey

/** Home-screen widget with a D-pad, volume and playback keys. */
class RemoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_remote).apply {
            KEYS.forEach { (id, key) -> setOnClickPendingIntent(id, RemoteActionReceiver.pendingIntent(context, key)) }
            setOnClickPendingIntent(
                R.id.btn_open_app,
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    private companion object {
        val KEYS = mapOf(
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
    }
}
