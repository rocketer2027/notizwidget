package com.example.notizwidget;

import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

/** Formatiert nur den markierten Bereich: fett, kursiv, Groesse, Farbe. */
final class TextFormat {
    /** Stufen der Schriftgroesse relativ zur Normalgroesse. */
    static final float[] SIZES = {0.7f, 0.85f, 1f, 1.25f, 1.5f, 2f, 2.5f};
    private static final int FLAG = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;

    private interface Filter {
        boolean accept(Object span);
    }

    /** Index der Normalgroesse (Faktor 1) in SIZES. */
    static final int DEFAULT_SIZE_IDX = 2;

    private TextFormat() {}

    /** Stufe, die einem Faktor am naechsten liegt. */
    static int nearestIndex(float factor) {
        int idx = 0;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < SIZES.length; i++) {
            float diff = Math.abs(SIZES[i] - factor);
            if (diff < best) {
                best = diff;
                idx = i;
            }
        }
        return idx;
    }

    /** Fett/kursiv fest setzen (on) oder entfernen, ohne umzuschalten. */
    static void setStyle(Editable e, int start, int end, final int style, boolean on) {
        if (start >= end) return;
        clear(e, StyleSpan.class, start, end, new Filter() {
            @Override
            public boolean accept(Object s) {
                return ((StyleSpan) s).getStyle() == style;
            }
        });
        if (on) put(e, new StyleSpan(style), start, end);
    }

    /** Groesse fest auf einen Faktor setzen (1 = Standard, entfernt die Groessenangabe). */
    static void setSize(Editable e, int start, int end, float factor) {
        if (start >= end) return;
        clear(e, RelativeSizeSpan.class, start, end, null);
        if (factor != 1f) put(e, new RelativeSizeSpan(factor), start, end);
    }

    private static boolean same(Object a, Object b) {
        if (a instanceof StyleSpan && b instanceof StyleSpan)
            return ((StyleSpan) a).getStyle() == ((StyleSpan) b).getStyle();
        if (a instanceof RelativeSizeSpan && b instanceof RelativeSizeSpan)
            return ((RelativeSizeSpan) a).getSizeChange() == ((RelativeSizeSpan) b).getSizeChange();
        if (a instanceof ForegroundColorSpan && b instanceof ForegroundColorSpan)
            return ((ForegroundColorSpan) a).getForegroundColor() == ((ForegroundColorSpan) b).getForegroundColor();
        return false;
    }

    /** Setzt einen Span und verlaengert einen gleichen, direkt davor endenden, statt viele Mini-Spans anzuhaeufen. */
    private static void put(Editable e, Object span, int start, int end) {
        int s = start;
        if (start > 0) {
            for (Object o : e.getSpans(start - 1, start, span.getClass())) {
                if (e.getSpanEnd(o) == start && same(o, span)) {
                    s = e.getSpanStart(o);
                    e.removeSpan(o);
                    break;
                }
            }
        }
        e.setSpan(span, s, end, FLAG);
    }

    /** Fett oder kursiv ein- bzw. ausschalten (wenn schon alles markierte so ist, wird es entfernt). */
    static void toggleStyle(Editable e, int start, int end, final int style) {
        boolean[] covered = new boolean[end - start];
        for (StyleSpan sp : e.getSpans(start, end, StyleSpan.class)) {
            if (sp.getStyle() != style) continue;
            int a = Math.max(start, e.getSpanStart(sp));
            int b = Math.min(end, e.getSpanEnd(sp));
            for (int i = a; i < b; i++) covered[i - start] = true;
        }
        boolean all = true;
        for (boolean c : covered) {
            if (!c) {
                all = false;
                break;
            }
        }
        clear(e, StyleSpan.class, start, end, new Filter() {
            @Override
            public boolean accept(Object s) {
                return ((StyleSpan) s).getStyle() == style;
            }
        });
        if (!all) put(e, new StyleSpan(style), start, end);
    }

    /** Groesse um eine Stufe aendern (delta = +1 oder -1). */
    static void changeSize(Editable e, int start, int end, int delta) {
        float current = 1f;
        RelativeSizeSpan[] found = e.getSpans(start, end, RelativeSizeSpan.class);
        if (found.length > 0) current = found[0].getSizeChange();
        int idx = 0;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < SIZES.length; i++) {
            float diff = Math.abs(SIZES[i] - current);
            if (diff < best) {
                best = diff;
                idx = i;
            }
        }
        idx = Math.max(0, Math.min(SIZES.length - 1, idx + delta));
        clear(e, RelativeSizeSpan.class, start, end, null);
        if (SIZES[idx] != 1f) e.setSpan(new RelativeSizeSpan(SIZES[idx]), start, end, FLAG);
    }

    /** Textfarbe setzen; Palette.NO_COLOR entfernt die Farbe wieder. */
    static void setColor(Editable e, int start, int end, int color) {
        clear(e, ForegroundColorSpan.class, start, end, null);
        if (color != Palette.NO_COLOR && start < end) put(e, new ForegroundColorSpan(color), start, end);
    }

    /** Entfernt jede Formatierung im Bereich (Standardformat); der Rest des Textes bleibt unveraendert. */
    static void stripAll(Editable e, int start, int end) {
        if (start >= end) return;
        clear(e, StyleSpan.class, start, end, null);
        clear(e, RelativeSizeSpan.class, start, end, null);
        clear(e, ForegroundColorSpan.class, start, end, null);
    }

    /** Entfernt Spans im Bereich; Teile ausserhalb des Bereichs bleiben erhalten. */
    private static void clear(Editable e, Class<?> cls, int start, int end, Filter filter) {
        Object[] spans = e.getSpans(start, end, cls);
        for (Object sp : spans) {
            if (filter != null && !filter.accept(sp)) continue;
            int s = e.getSpanStart(sp);
            int t = e.getSpanEnd(sp);
            e.removeSpan(sp);
            if (s < start) {
                Object left = copy(sp);
                if (left != null) e.setSpan(left, s, start, FLAG);
            }
            if (t > end) {
                Object right = copy(sp);
                if (right != null) e.setSpan(right, end, t, FLAG);
            }
        }
    }

    private static Object copy(Object s) {
        if (s instanceof StyleSpan) return new StyleSpan(((StyleSpan) s).getStyle());
        if (s instanceof ForegroundColorSpan)
            return new ForegroundColorSpan(((ForegroundColorSpan) s).getForegroundColor());
        if (s instanceof RelativeSizeSpan) return new RelativeSizeSpan(((RelativeSizeSpan) s).getSizeChange());
        return null;
    }
}
