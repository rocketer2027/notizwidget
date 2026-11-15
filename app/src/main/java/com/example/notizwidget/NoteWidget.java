package com.example.notizwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RemoteViews;

/** Homescreen-Widget: zeigt die Notiz scrollbar an; Tippen oeffnet die App zum Schreiben. */
public class NoteWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) manager.updateAppWidget(id, build(context, id));
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list);
    }

    /** Groesse des Widgets geaendert: die freie Flaeche unter dem Text neu berechnen. */
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions);
        manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list);
    }

    static RemoteViews build(Context c, int appWidgetId) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_note);

        // Hintergrund: Farbe und Transparenz
        int bg = Settings.bgColor(c);
        rv.setInt(R.id.widget_bg, "setColorFilter", bg);
        rv.setInt(R.id.widget_bg, "setImageAlpha", Settings.bgAlpha255(c));

        // Scrollbare Liste der Zeilen
        Intent svc = new Intent(c, NoteWidgetService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        rv.setRemoteAdapter(R.id.widget_list, svc);
        rv.setEmptyView(R.id.widget_list, R.id.widget_empty);

        // Hinweis bei leerer Notiz
        int tc = Palette.textColorFor(bg);
        rv.setTextColor(R.id.widget_empty, (tc & 0x00FFFFFF) | 0xB0000000);
        rv.setTextViewTextSize(R.id.widget_empty, TypedValue.COMPLEX_UNIT_SP, Settings.widgetTextSize(c));

        // Tippen irgendwo auf eine Zeile oeffnet die App
        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int mutable = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
        PendingIntent template = PendingIntent.getActivity(
                c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | mutable);
        rv.setPendingIntentTemplate(R.id.widget_list, template);

        // Tippen auf den Hinweis oder den Hintergrund oeffnet die App ebenfalls
        PendingIntent direct = PendingIntent.getActivity(
                c, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.widget_empty, direct);
        rv.setOnClickPendingIntent(R.id.widget_bg, direct);
        return rv;
    }

    /** Alle Widgets neu zeichnen (nach Aenderung der Notiz oder der Einstellungen). */
    static void updateAll(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, NoteWidget.class));
            if (ids == null || ids.length == 0) return;
            for (int id : ids) m.updateAppWidget(id, build(c, id));
            m.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
        } catch (Exception ignored) {
            // Ein Fehler beim Widget darf das Speichern nie verhindern.
        }
    }
}
