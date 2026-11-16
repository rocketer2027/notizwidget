package com.example.notizwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/** Farben und kleine Farbwahl-Helfer. */
final class Palette {

    /** Auswahl fuer den Hintergrund. */
    static final int[] BG = {
            0xFFFFF6C8, // Gelb
            0xFFFFFFFF, // Weiss
            0xFFDFF5D8, // Hellgruen
            0xFFD9ECFF, // Hellblau
            0xFFFFE0E6, // Rosa
            0xFFE6E6E6, // Hellgrau
            0xFF2B2B2B, // Dunkelgrau
            0xFF000000  // Schwarz
    };

    /** Auswahl fuer markierten Text. */
    static final int[] TEXT = {
            0xFF000000, 0xFFD32F2F, 0xFFF57C00, 0xFF2E7D32,
            0xFF1565C0, 0xFF6A1B9A, 0xFF757575, 0xFFFFFFFF
    };

    /** Bedeutet: Standardfarbe (Farbe der Markierung entfernen). */
    static final int NO_COLOR = 0;

    interface ColorCallback {
        void onColor(int color);
    }

    private Palette() {}

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static boolean isLight(int bg) {
        double lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255.0;
        return lum > 0.55;
    }

    /** Standard-Textfarbe passend zum Hintergrund: dunkel auf hell, hell auf dunkel. */
    static int textColorFor(int bg) {
        return isLight(bg) ? 0xFF2B2711 : 0xFFF5F5F5;
    }

    /** Runder Farbpunkt zum Antippen. */
    static View swatch(Context c, int color, boolean selected, int sizeDp, View.OnClickListener l) {
        View v = new View(c);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setStroke(dp(c, selected ? 3 : 1), selected ? 0xFF1E88E5 : 0x66000000);
        v.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp));
        lp.setMargins(dp(c, 6), dp(c, 6), dp(c, 6), dp(c, 6));
        v.setLayoutParams(lp);
        v.setOnClickListener(l);
        return v;
    }

    /** Kleiner Dialog mit Farbpunkten fuer die Textfarbe. */
    static void pickTextColor(Activity a, final ColorCallback cb) {
        HorizontalScrollView sv = new HorizontalScrollView(a);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));
        sv.addView(row);

        final AlertDialog[] dlg = new AlertDialog[1];
        for (final int col : TEXT) {
            row.addView(swatch(a, col, false, 40, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (dlg[0] != null) dlg[0].dismiss();
                    cb.onColor(col);
                }
            }));
        }
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(a.getString(R.string.color_title))
                .setView(sv)
                .setNeutralButton(a.getString(R.string.color_default), (d, w) -> cb.onColor(NO_COLOR))
                .setNegativeButton(a.getString(R.string.cancel), null);
        dlg[0] = b.create();
        dlg[0].show();
    }
}
