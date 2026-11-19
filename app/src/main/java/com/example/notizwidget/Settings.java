package com.example.notizwidget;

import android.content.Context;
import android.content.SharedPreferences;

/** Alle Einstellungen der App an einem Ort. */
final class Settings {
    static final int DEFAULT_BG = 0xFFFFF6C8;

    private Settings() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** Die vom Nutzer gewaehlte Farbe (unabhaengig vom Dunkelmodus). */
    static int rawBgColor(Context c) {
        return p(c).getInt("bg", DEFAULT_BG) | 0xFF000000;
    }

    /** Tatsaechlich verwendeter Hintergrund: im Dunkelmodus die gewaehlte Farbe stark abgedunkelt. */
    static int bgColor(Context c) {
        int raw = rawBgColor(c);
        if (!darkMode(c)) return raw;
        int r = (int) (((raw >> 16) & 0xFF) * 0.2f);
        int g = (int) (((raw >> 8) & 0xFF) * 0.2f);
        int b = (int) ((raw & 0xFF) * 0.2f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static boolean darkMode(Context c) {
        return p(c).getBoolean("dark", false);
    }

    static void setDarkMode(Context c, boolean on) {
        p(c).edit().putBoolean("dark", on).apply();
    }

    static int themeRes(Context c) {
        return darkMode(c) ? R.style.AppThemeDark : R.style.AppTheme;
    }

    /** Gewaehltes Logo: 0 = Gold (Standard), 1 = Blau. */
    static int logo(Context c) {
        return Math.max(0, Math.min(1, p(c).getInt("logo", 0)));
    }

    static void setLogo(Context c, int index) {
        p(c).edit().putInt("logo", index).apply();
    }

    /**
     * Android 13+: Das Startbild (System-Splash) uebernimmt gewaehltes Logo und Hintergrundfarbe ab dem naechsten Start.
     * Auf aelteren Versionen laesst sich das Startbild nicht umstellen (dort bleibt es Gold).
     */
    private static final int[][] SPLASH_THEMES = {
            {R.style.SplashG0, R.style.SplashG1, R.style.SplashG2, R.style.SplashG3, R.style.SplashG4, R.style.SplashG5, R.style.SplashG6, R.style.SplashG7, R.style.SplashG8, R.style.SplashG9, R.style.SplashG10, R.style.SplashG11, R.style.SplashG12, R.style.SplashG13, R.style.SplashG14, R.style.SplashG15},
            {R.style.SplashB0, R.style.SplashB1, R.style.SplashB2, R.style.SplashB3, R.style.SplashB4, R.style.SplashB5, R.style.SplashB6, R.style.SplashB7, R.style.SplashB8, R.style.SplashB9, R.style.SplashB10, R.style.SplashB11, R.style.SplashB12, R.style.SplashB13, R.style.SplashB14, R.style.SplashB15}
    };

    static void applySplashTheme(android.app.Activity a) {
        if (android.os.Build.VERSION.SDK_INT < 33) return;
        try {
            int raw = rawBgColor(a);
            boolean dark = darkMode(a);
            int logo = logo(a);
            int theme = 0;
            // 1) Farben aus der Palette: exakt passendes Startbild
            for (int i = 0; i < Palette.BG.length; i++) {
                if (Palette.BG[i] == raw) {
                    theme = SPLASH_THEMES[logo][dark ? i + 8 : i];
                    break;
                }
            }
            // 2) Eigene Farbe: naechstgelegene Stufe im feinen Raster (System-Splash kann keine freie Farbe)
            if (theme == 0) {
                int shown = bgColor(a);
                int levels = dark ? 3 : 5;
                int max = dark ? 51 : 255;
                int r = Math.round(android.graphics.Color.red(shown) * levels / (float) max);
                int g = Math.round(android.graphics.Color.green(shown) * levels / (float) max);
                int b = Math.round(android.graphics.Color.blue(shown) * levels / (float) max);
                String name = "Sp" + (logo == 1 ? "B" : "G") + (dark ? "D" : "L") + r + g + b;
                theme = a.getResources().getIdentifier(name, "style", a.getPackageName());
            }
            if (theme == 0) theme = logo == 1 ? R.style.StartThemeBlue : R.style.StartTheme;
            a.getSplashScreen().setSplashScreenTheme(theme);
        } catch (Exception ignored) {
            // Startbild ist nur Zierde: Fehler hier duerfen nichts blockieren.
        }
    }

    static int logoRes(Context c) {
        return logo(c) == 1 ? R.drawable.logo_blue : R.drawable.logo;
    }

    /** "de", "en", "id" oder "jv". */
    static String language(Context c) {
        return p(c).getString("lang", "de");
    }

    static void setLanguage(Context c, String code) {
        p(c).edit().putString("lang", code).apply();
    }

    /** Wie viele Sicherungen maximal behalten werden (1 bis 50). */
    static int maxBackups(Context c) {
        return Math.max(1, Math.min(50, p(c).getInt("maxBackups", 15)));
    }

    static void setMaxBackups(Context c, int n) {
        p(c).edit().putInt("maxBackups", Math.max(1, Math.min(50, n))).apply();
    }

    static long lastEdit(Context c) {
        return p(c).getLong("lastEdit", 0L);
    }

    static void setLastEdit(Context c, long time) {
        p(c).edit().putLong("lastEdit", time).apply();
    }

    static void setBgColor(Context c, int color) {
        p(c).edit().putInt("bg", color | 0xFF000000).apply();
    }

    /** Transparenz in Prozent: 0 = deckend, 90 = fast durchsichtig. */
    static int transparency(Context c) {
        return p(c).getInt("transparency", 0);
    }

    static void setTransparency(Context c, int percent) {
        p(c).edit().putInt("transparency", percent).apply();
    }

    /** Deckkraft des Widget-Hintergrunds von 0 bis 255. */
    static int bgAlpha255(Context c) {
        return Math.round(255f * (100 - transparency(c)) / 100f);
    }

    /** Schriftgroesse (sp) der Notiz im Widget auf dem Homescreen. */
    static int widgetTextSize(Context c) {
        return p(c).getInt("textSize", 16);
    }

    static void setWidgetTextSize(Context c, int sp) {
        p(c).edit().putInt("textSize", sp).apply();
    }

    static String email(Context c) {
        return p(c).getString("email", "");
    }

    static void setEmail(Context c, String email) {
        p(c).edit().putString("email", email == null ? "" : email.trim()).apply();
    }

    static boolean autoBackup(Context c) {
        return p(c).getBoolean("autoBackup", true);
    }

    static void setAutoBackup(Context c, boolean on) {
        p(c).edit().putBoolean("autoBackup", on).apply();
    }

    /** Rhythmus der automatischen Sicherung in Tagen (1 bis 14). */
    static int backupDays(Context c) {
        return p(c).getInt("backupDays", 2);
    }

    static void setBackupDays(Context c, int days) {
        p(c).edit().putInt("backupDays", Math.max(1, Math.min(14, days))).apply();
    }

    static int backupHour(Context c) {
        return p(c).getInt("backupHour", 23);
    }

    static int backupMinute(Context c) {
        return p(c).getInt("backupMinute", 0);
    }

    static void setBackupTime(Context c, int hour, int minute) {
        p(c).edit().putInt("backupHour", hour).putInt("backupMinute", minute).apply();
    }

    /** "html" (mit Formatierung) oder "txt" (nur Text). */
    static String backupFormat(Context c) {
        return p(c).getString("backupFormat", "html");
    }

    static void setBackupFormat(Context c, String format) {
        p(c).edit().putString("backupFormat", format).apply();
    }

    static long lastBackupTime(Context c) {
        return p(c).getLong("lastBackupTime", 0L);
    }

    static String lastBackupHash(Context c) {
        return p(c).getString("lastBackupHash", "");
    }

    static void setLastBackup(Context c, long time, String hash) {
        p(c).edit().putLong("lastBackupTime", time).putString("lastBackupHash", hash).apply();
    }
}
