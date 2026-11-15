package com.example.notizwidget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

/**
 * Liefert dem Widget die Notiz als EINEN zusammenhaengenden Textblock (dadurch scrollt es
 * fluessig statt zeilenweise) plus eine leere Flaeche darunter, die beim Antippen die App oeffnet.
 */
public class NoteWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        return new Factory(getApplicationContext(), id);
    }

    static class Factory implements RemoteViewsFactory {
        /** Hoehen (dp) der verfuegbaren Fuellflaechen, passend zu den Layouts widget_filler_*. */
        private static final int[] FILLER_DP = {30, 60, 90, 120, 160, 200, 250, 300, 360, 430, 520, 640};
        private static final int[] FILLER_LAYOUT = {
                R.layout.widget_filler_30, R.layout.widget_filler_60, R.layout.widget_filler_90,
                R.layout.widget_filler_120, R.layout.widget_filler_160, R.layout.widget_filler_200,
                R.layout.widget_filler_250, R.layout.widget_filler_300, R.layout.widget_filler_360,
                R.layout.widget_filler_430, R.layout.widget_filler_520, R.layout.widget_filler_640
        };

        private final Context ctx;
        private final int widgetId;
        private SpannableStringBuilder text = new SpannableStringBuilder();
        private int fillerIndex = 0;

        Factory(Context ctx, int widgetId) {
            this.ctx = ctx;
            this.widgetId = widgetId;
        }

        @Override
        public void onCreate() {
            load();
        }

        @Override
        public void onDataSetChanged() {
            load();
        }

        @Override
        public void onDestroy() {
            text = new SpannableStringBuilder();
        }

        private void load() {
            text = NoteStore.load(ctx);
            fillerIndex = chooseFiller();
        }

        /**
         * Schaetzt, wie viel Platz unter dem Text im Widget noch frei ist, und waehlt eine passende
         * Fuellflaeche. So oeffnet ein Tipp irgendwo im Widget die App, auch bei kurzen Notizen.
         */
        private int chooseFiller() {
            float widgetH = 0f;
            float widgetW = 0f;
            try {
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    Bundle o = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(widgetId);
                    if (o != null) {
                        widgetH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
                        widgetW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
                    }
                }
            } catch (Exception ignored) {
                // Standardwerte unten
            }
            if (widgetH <= 0f) widgetH = 320f;
            if (widgetW <= 0f) widgetW = 300f;

            float size = Settings.widgetTextSize(ctx) * ctx.getResources().getConfiguration().fontScale;
            float charsPerLine = Math.max(6f, (widgetW - 28f) / (size * 0.52f));
            float lines = 0f;
            for (String para : text.toString().split("\n", -1)) {
                lines += Math.max(1f, (float) Math.ceil(para.length() / charsPerLine));
            }
            float textHeight = 18f + lines * size * 1.25f;
            float need = widgetH - textHeight;
            for (int i = 0; i < FILLER_DP.length; i++) {
                if (FILLER_DP[i] >= need) return i;
            }
            return FILLER_DP.length - 1;
        }

        /** Ein Textblock plus eine Fuellflaeche. Bei leerer Notiz: nichts (dann zeigt das Widget den Hinweis). */
        @Override
        public int getCount() {
            return text.toString().trim().isEmpty() ? 0 : 2;
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position == 0) {
                RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_item);
                rv.setTextViewText(R.id.item_text, text);
                rv.setTextViewTextSize(R.id.item_text, TypedValue.COMPLEX_UNIT_SP, Settings.widgetTextSize(ctx));
                rv.setTextColor(R.id.item_text, Palette.textColorFor(Settings.bgColor(ctx)));
                rv.setOnClickFillInIntent(R.id.item_text, new Intent());
                return rv;
            }
            RemoteViews filler = new RemoteViews(ctx.getPackageName(), FILLER_LAYOUT[fillerIndex]);
            filler.setOnClickFillInIntent(R.id.filler, new Intent());
            return filler;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1 + FILLER_LAYOUT.length;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
