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

    static int bgColor(Context c) {
        return p(c).getInt("bg", DEFAULT_BG) | 0xFF000000;
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
