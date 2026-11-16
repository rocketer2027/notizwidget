package com.example.notizwidget;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.Base64;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Sicherungen als HTML-Datei (mit Formatierung) oder als reine Textdatei. Die HTML-Datei zeigt die
 * Notiz formatiert im Browser an und enthaelt unsichtbar die genauen Daten, damit sie sich spaeter
 * verlustfrei wieder einspielen laesst.
 */
final class Backup {
    private static final String MARKER = "<!--NOTIZ-DATA:";
    private static final String FOLDER = "Catatan-Sicherungen";
    private static final String ACTION = "com.example.notizwidget.BACKUP";
    private static final String CHANNEL = "backup";
    static final String EXTRA_FORCE = "force";
    private static final long SLACK_MS = 6L * 60 * 60 * 1000; // Toleranz, falls der Wecker etwas frueher/spaeter feuert

    private Backup() {}

    // ---------- Dateiinhalt ----------

    static String toHtml(CharSequence text) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        String json = NoteStore.serialize(sb);
        String data = Base64.encodeToString(json.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"><title>Catatan</title></head><body>\n"
                + Html.toHtml(sb, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
                + "\n" + MARKER + data + "-->\n</body></html>\n";
    }

    /** Liest eine Sicherung (HTML mit Daten) oder jede andere Textdatei. */
    static SpannableStringBuilder fromBackup(String content) {
        int idx = content.indexOf(MARKER);
        if (idx >= 0) {
            int end = content.indexOf("-->", idx);
            if (end > idx) {
                try {
                    String b64 = content.substring(idx + MARKER.length(), end).trim();
                    String json = new String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8);
                    return NoteStore.deserialize(json);
                } catch (Exception ignored) {
                    // faellt auf die einfachen Faelle unten zurueck
                }
            }
        }
        String lower = content.toLowerCase(Locale.ROOT);
        if (lower.contains("<html") || lower.contains("<body")) {
            return new SpannableStringBuilder(Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY));
        }
        return new SpannableStringBuilder(content);
    }

    // ---------- Lesen und Schreiben ----------

    static boolean writeToUri(Context c, Uri uri, String content) {
        try (OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) return false;
            out.write(content.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String readFromUri(Context c, Uri uri) {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            return NoteStore.readAll(in);
        } catch (Exception e) {
            return null;
        }
    }

    /** Speichert unter Dokumente/Catatan-Sicherungen (ohne zusaetzliche Berechtigung). Gibt den Dateinamen zurueck oder null. */
    static String saveToDocuments(Context c, String content, boolean asText) {
        try {
            String ext = asText ? ".txt" : ".html";
            String name = "Catatan-" + new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.GERMANY).format(new Date()) + ext;
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, asText ? "text/plain" : "text/html");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER);
            Uri uri = c.getContentResolver().insert(MediaStore.Files.getContentUri("external"), v);
            if (uri == null) return null;
            return writeToUri(c, uri, content) ? name : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sichert die aktuelle Notiz sofort im eingestellten Format. Gibt den Dateinamen zurueck,
     * null bei leerer Notiz oder Fehler. Nur automatische Sicherungen verschieben den Rhythmus.
     */
    static String backupNow(Context c, boolean countForSchedule) {
        SpannableStringBuilder note = NoteStore.load(c);
        if (note.toString().trim().isEmpty()) return null;
        boolean asText = "txt".equals(Settings.backupFormat(c));
        String content = asText ? note.toString() : toHtml(note);
        String name = saveToDocuments(c, content, asText);
        if (name != null && countForSchedule) {
            Settings.setLastBackup(c, System.currentTimeMillis(), "");
        }
        return name;
    }

    // ---------- Automatik ----------

    private static PendingIntent alarmIntent(Context c, boolean force) {
        Intent i = new Intent(c, BackupReceiver.class).setAction(ACTION);
        if (force) i.putExtra(EXTRA_FORCE, true);
        return PendingIntent.getBroadcast(c, force ? 78 : 77, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Setzt den Wecker fuer die naechste eingestellte Uhrzeit. Der Wecker darf auch im Ruhezustand
     * (Doze) ausloesen. Nach jedem Ausloesen setzt der Empfaenger den naechsten Wecker neu.
     */
    static void schedule(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, Settings.backupHour(c));
            cal.set(Calendar.MINUTE, Settings.backupMinute(c));
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis() + 5000) cal.add(Calendar.DAY_OF_YEAR, 1);
            PendingIntent pi = alarmIntent(c, false);
            am.cancel(pi);
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } catch (Exception ignored) {
            // Sicherung ist ein Zusatz und darf die App nie stoeren.
        }
    }

    /** Testlauf: in ca. 1 Minute genau derselbe Weg wie bei der echten Automatik (ohne Rhythmus-Pruefung). */
    static void scheduleTest(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000L,
                    alarmIntent(c, true));
        } catch (Exception ignored) {
            // egal
        }
    }

    static void cancel(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(alarmIntent(c, false));
        } catch (Exception ignored) {
            // egal
        }
    }

    /** Wird vom Wecker aufgerufen. force = Testlauf. */
    static void runAuto(Context c, boolean force) {
        if (!force && !Settings.autoBackup(c)) return;
        if (NoteStore.deserialize(NoteStore.loadRaw(c)).toString().trim().isEmpty()) {
            if (force) notifyResult(c, "Nichts gesichert: Die Notiz ist leer.");
            return;
        }
        long now = System.currentTimeMillis();
        long gap = Settings.backupDays(c) * 24L * 60 * 60 * 1000 - SLACK_MS;
        if (!force && now - Settings.lastBackupTime(c) < gap) return; // noch nicht wieder dran
        String name = backupNow(c, !force);
        notifyResult(c, name != null
                ? "Automatische Sicherung gespeichert: " + name
                : "Automatische Sicherung fehlgeschlagen.");
    }

    // ---------- Benachrichtigung ----------

    /** Fragt (ab Android 13) einmalig nach der Erlaubnis, Benachrichtigungen zu zeigen. */
    static void requestNotificationPermission(Activity a, boolean onlyOnce) {
        if (Build.VERSION.SDK_INT < 33) return;
        if (a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        if (onlyOnce) {
            android.content.SharedPreferences p = a.getSharedPreferences("app", Context.MODE_PRIVATE);
            if (p.getBoolean("askedNotifications", false)) return;
            p.edit().putBoolean("askedNotifications", true).apply();
        }
        a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5);
    }

    static void notifyResult(Context c, String text) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm == null) return;
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Sicherung", NotificationManager.IMPORTANCE_DEFAULT));
            Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(c, 2, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Catatan")
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify(1001, n);
        } catch (Exception ignored) {
            // Fehlende Erlaubnis o. ae.: Sicherung selbst ist trotzdem erfolgt.
        }
    }
}
