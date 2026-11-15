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
