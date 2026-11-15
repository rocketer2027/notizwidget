package com.example.notizwidget;

import android.content.Context;
import android.content.Intent;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

/** Liefert dem Widget die Zeilen der Notiz (jede Zeile ein Listeneintrag, mit Formatierung). */
public class NoteWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    static class Factory implements RemoteViewsFactory {
        private final Context ctx;
        private final List<CharSequence> lines = new ArrayList<>();

        Factory(Context ctx) {
            this.ctx = ctx;
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
            lines.clear();
        }

        private void load() {
            lines.clear();
            SpannableStringBuilder s = NoteStore.load(ctx);
            if (s.toString().trim().isEmpty()) return;
            int start = 0;
            int n = s.length();
            for (int i = 0; i <= n; i++) {
                if (i == n || s.charAt(i) == '\n') {
                    lines.add(s.subSequence(start, i));
                    start = i + 1;
                }
            }
        }

        /** Alle Zeilen plus eine leere Flaeche am Ende, die beim Antippen die App oeffnet. */
        @Override
        public int getCount() {
            return lines.isEmpty() ? 0 : lines.size() + 1;
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position >= lines.size()) {
                RemoteViews filler = new RemoteViews(ctx.getPackageName(), R.layout.widget_filler);
                filler.setOnClickFillInIntent(R.id.filler, new Intent());
                return filler;
            }
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_item);
            CharSequence line = lines.get(position);
            if (line.length() == 0) line = " ";
            rv.setTextViewText(R.id.item_text, line);
            rv.setTextViewTextSize(R.id.item_text, TypedValue.COMPLEX_UNIT_SP, Settings.widgetTextSize(ctx));
            rv.setTextColor(R.id.item_text, Palette.textColorFor(Settings.bgColor(ctx)));
            rv.setOnClickFillInIntent(R.id.item_text, new Intent());
            return rv;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
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
