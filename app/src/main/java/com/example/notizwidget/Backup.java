package com.example.notizwidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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
    private static final long SLACK_MS = 6L * 60 * 60 * 1000; // Toleranz, weil der Wecker nicht auf die Minute feuert

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

    /** Speichert unter Dokumente/Catatan-Sicherungen (ohne zusaetzliche Berechtigung). */
    static boolean saveToDocuments(Context c, String content, boolean asText) {
        try {
            String ext = asText ? ".txt" : ".html";
            String name = "Catatan-" + new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.GERMANY).format(new Date()) + ext;
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, asText ? "text/plain" : "text/html");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER);
            Uri uri = c.getContentResolver().insert(MediaStore.Files.getContentUri("external"), v);
            if (uri == null) return false;
            return writeToUri(c, uri, content);
        } catch (Exception e) {
            return false;
        }
    }

    /** Sichert die aktuelle Notiz sofort im eingestellten Format. false, wenn leer oder fehlgeschlagen. */
    static boolean backupNow(Context c) {
        SpannableStringBuilder note = NoteStore.load(c);
        if (note.toString().trim().isEmpty()) return false;
        boolean asText = "txt".equals(Settings.backupFormat(c));
        String content = asText ? note.toString() : toHtml(note);
        boolean ok = saveToDocuments(c, content, asText);
        if (ok) {
            Settings.setLastBackup(c, System.currentTimeMillis(), String.valueOf(NoteStore.loadRaw(c).hashCode()));
        }
        return ok;
    }

    // ---------- Automatik ----------

    private static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, BackupReceiver.class).setAction("com.example.notizwidget.BACKUP");
        return PendingIntent.getBroadcast(c, 77, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Taeglicher Wecker zur eingestellten Uhrzeit. Gesichert wird nur, wenn seit der letzten
     * Sicherung der eingestellte Rhythmus (Tage) vergangen ist.
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
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, alarmIntent(c));
        } catch (Exception ignored) {
            // Sicherung ist ein Zusatz und darf die App nie stoeren.
        }
    }

    static void cancel(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(alarmIntent(c));
        } catch (Exception ignored) {
            // egal
        }
    }

    /** Wird vom Wecker aufgerufen. */
    static void runIfDue(Context c) {
        if (!Settings.autoBackup(c)) return;
        String raw = NoteStore.loadRaw(c);
        if (NoteStore.deserialize(raw).toString().trim().isEmpty()) return;
        long now = System.currentTimeMillis();
        long gap = Settings.backupDays(c) * 24L * 60 * 60 * 1000 - SLACK_MS;
        if (now - Settings.lastBackupTime(c) < gap) return;
        if (String.valueOf(raw.hashCode()).equals(Settings.lastBackupHash(c))) return; // nichts geaendert
        backupNow(c);
    }
}
