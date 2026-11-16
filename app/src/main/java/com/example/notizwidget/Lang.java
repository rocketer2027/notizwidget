package com.example.notizwidget;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** App-Sprache unabhaengig von der Systemsprache: Deutsch, Englisch, Indonesisch, Javanisch. */
final class Lang {
    static final String[] CODES = {"de", "en", "id", "jv"};
    static final int[] NAMES = {R.string.lang_de, R.string.lang_en, R.string.lang_id, R.string.lang_jv};

    private Lang() {}

    static Locale locale(Context c) {
        String code = Settings.language(c);
        return "id".equals(code) ? new Locale("in", "ID") : new Locale(code);
    }

    /** Liefert einen Context, der Texte in der gewaehlten App-Sprache liest. */
    static Context wrap(Context base) {
        try {
            Configuration cfg = new Configuration(base.getResources().getConfiguration());
            cfg.setLocale(locale(base));
            return base.createConfigurationContext(cfg);
        } catch (Exception e) {
            return base;
        }
    }

    static int indexOf(Context c) {
        String code = Settings.language(c);
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return i;
        return 0;
    }
}
