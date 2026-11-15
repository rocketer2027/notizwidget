package com.example.notizwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class NoteWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, buildViews(context)) }
    }

    companion object {
        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_note)
            val text = NoteStore.load(context)

            if (text.isBlank()) {
                views.setTextViewText(R.id.widget_text, context.getString(R.string.widget_empty))
                views.setTextColor(R.id.widget_text, Color.parseColor("#8A7F4A"))
            } else {
                views.setTextViewText(R.id.widget_text, text)
                views.setTextColor(R.id.widget_text, Color.parseColor("#2B2711"))
            }

            // Tippen auf das Widget öffnet die App zum Schreiben.
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NoteWidget::class.java))
            ids.forEach { manager.updateAppWidget(it, buildViews(context)) }
        }
    }
}
