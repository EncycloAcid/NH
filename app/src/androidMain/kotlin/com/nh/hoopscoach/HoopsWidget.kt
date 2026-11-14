package com.nh.hoopscoach

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * The home-screen widget: your team and record, the next game, and the latest headline. The app writes
 * the three lines whenever the league changes (see updateHomeWidget); tapping it opens the game.
 */
class HoopsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, views(context))
    }

    companion object {
        fun views(context: Context): RemoteViews {
            val prefs = context.getSharedPreferences("hoops_widget", Context.MODE_PRIVATE)
            val v = RemoteViews(context.packageName, R.layout.widget_hoops)
            v.setTextViewText(R.id.widget_title, prefs.getString("title", null) ?: "Hoops Dynasty")
            v.setTextViewText(R.id.widget_line1, prefs.getString("line1", null) ?: "Open the game to start your season.")
            v.setTextViewText(R.id.widget_line2, prefs.getString("line2", null) ?: "")
            val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return v
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HoopsWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
        }
    }
}
