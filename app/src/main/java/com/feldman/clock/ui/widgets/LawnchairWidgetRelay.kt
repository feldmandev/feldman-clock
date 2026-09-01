package com.feldman.clock.ui.widgets

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.widget.RemoteViews

/** Delivers a large Clock RemoteViews directly to the active signed Lawnchair fork. */
object LawnchairWidgetRelay {
    private const val LAWNCHAIR_PACKAGE = "app.lawnchair"
    private const val LAWNCHAIR_SERVICE = "app.lawnchair.ClockWidgetRelayService"
    private const val MESSAGE_UPDATE = 1
    private const val EXTRA_APP_WIDGET_ID = "appWidgetId"
    private const val EXTRA_REMOTE_VIEWS = "remoteViews"

    fun send(context: Context, appWidgetId: Int, remoteViews: RemoteViews) {
        val appContext = context.applicationContext
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                try {
                    Messenger(service).send(Message.obtain(null, MESSAGE_UPDATE).apply {
                        data = Bundle().apply {
                            putInt(EXTRA_APP_WIDGET_ID, appWidgetId)
                            putParcelable(EXTRA_REMOTE_VIEWS, remoteViews)
                        }
                    })
                } catch (exception: Exception) {
                    Log.w(TAG, "Could not relay widget $appWidgetId", exception)
                } finally {
                    appContext.unbindService(this)
                }
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        runCatching {
            appContext.bindService(
                Intent().setComponent(ComponentName(LAWNCHAIR_PACKAGE, LAWNCHAIR_SERVICE)),
                connection,
                Context.BIND_AUTO_CREATE
            )
        }.onFailure { Log.w(TAG, "Could not bind Lawnchair relay", it) }
    }

    private const val TAG = "ClockWidgetRelay"
}
