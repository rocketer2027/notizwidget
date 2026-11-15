package com.example.notizwidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Speichert die Notiz samt Formatierung (fett, kursiv, Groesse, Farbe) als kleinen JSON-Text
 * und fuehrt einen Verlauf frueherer Fassungen, damit versehentlich Geloeschtes zurueckkommt.
 */
final class NoteStore {
    private static final String PREFS = "note";
    private static final String KEY = "data";
    private static final String OLD_KEY = "text"; // Version 1 der App: reiner Text
    private static final int MAX_HISTORY = 60;

    private NoteStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------- Laden und Speichern ----------

    static String loadRaw(Context c) {
        SharedPreferences p = prefs(c);
        String raw = p.getString(KEY, null);
        if (raw == null) {
            String old = p.getString(OLD_KEY, "");
            raw = serialize(new SpannableStringBuilder(old == null ? "" : old));
        }
        return raw;
    }

    static SpannableStringBuilder load(Context c) {
        return deserialize(loadRaw(c));
    }

    static void save(Context c, CharSequence text) {
        saveRaw(c, serialize(text));
    }

    static void saveRaw(Context c, String raw) {
        SharedPreferences p = prefs(c);
        boolean changed = !raw.equals(loadRaw(c)) || !p.contains(KEY);
        if (!changed) return;
        p.edit().putString(KEY, raw).apply();
        addHistory(c, raw);
        NoteWidget.updateAll(c);
    }

    /** Haengt Text unten an die Notiz an (fuer "Teilen"). */
    static void append(Context c, String add) {
        SpannableStringBuilder sb = load(c);
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
        sb.append(add);
        save(c, sb);
    }

    // ---------- Umwandlung Text <-> JSON ----------

    static String serialize(CharSequence cs) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", cs.toString());
            JSONArray arr = new JSONArray();
            if (cs instanceof Spanned) {
                Spanned sp = (Spanned) cs;
                Object[] spans = sp.getSpans(0, cs.length(), Object.class);
                for (Object s : spans) {
                    int a = sp.getSpanStart(s);
                    int b = sp.getSpanEnd(s);
                    if (a < 0 || b <= a) continue;
                    if (s instanceof StyleSpan) {
                        int st = ((StyleSpan) s).getStyle();
                        if (st == Typeface.BOLD || st == Typeface.BOLD_ITALIC) arr.put(entry(a, b, "b", 0));
                        if (st == Typeface.ITALIC || st == Typeface.BOLD_ITALIC) arr.put(entry(a, b, "i", 0));
                    } else if (s instanceof RelativeSizeSpan) {
                        arr.put(entry(a, b, "z", ((RelativeSizeSpan) s).getSizeChange()));
                    } else if (s instanceof ForegroundColorSpan) {
                        arr.put(entry(a, b, "c", ((ForegroundColorSpan) s).getForegroundColor()));
                    }
                }
            }
            o.put("s", arr);
            return o.toString();
        } catch (Exception e) {
            return "{\"t\":\"\",\"s\":[]}";
        }
    }

    private static JSONObject entry(int a, int b, String kind, double value) throws Exception {
        JSONObject j = new JSONObject();
        j.put("a", a);
        j.put("b", b);
        j.put("k", kind);
        if (!kind.equals("b") && !kind.equals("i")) j.put("v", value);
        return j;
    }

    static SpannableStringBuilder deserialize(String raw) {
        if (raw == null) return new SpannableStringBuilder();
        if (!raw.startsWith("{")) return new SpannableStringBuilder(raw); // reiner Text
        try {
            JSONObject o = new JSONObject(raw);
            String t = o.optString("t", "");
            SpannableStringBuilder sb = new SpannableStringBuilder(t);
            JSONArray arr = o.optJSONArray("s");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject j = arr.optJSONObject(i);
                    if (j == null) continue;
                    int a = Math.max(0, j.optInt("a"));
                    int b = Math.min(t.length(), j.optInt("b"));
                    if (b <= a) continue;
                    Object span = null;
                    String k = j.optString("k");
                    if (k.equals("b")) span = new StyleSpan(Typeface.BOLD);
                    else if (k.equals("i")) span = new StyleSpan(Typeface.ITALIC);
                    else if (k.equals("z")) span = new RelativeSizeSpan((float) j.optDouble("v", 1.0));
                    else if (k.equals("c")) span = new ForegroundColorSpan(j.optInt("v"));
                    if (span != null) sb.setSpan(span, a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            return sb;
        } catch (Exception e) {
            return new SpannableStringBuilder(raw);
        }
    }

    // ---------- Verlauf (zum Zurueckholen) ----------

    /** Eine fruehere Fassung der Notiz. */
    static final class Entry {
        final String raw;
        final long time;
        final String label;

        Entry(String raw, long time, String label) {
            this.raw = raw;
            this.time = time;
            this.label = label;
        }
    }

    private static File historyDir(Context c) {
        File d = new File(c.getFilesDir(), "history");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static void addHistory(Context c, String raw) {
        try {
            if (deserialize(raw).toString().trim().isEmpty()) return; // leere Fassung nicht merken
            File d = historyDir(c);
            File f = new File(d, System.currentTimeMillis() + ".json");
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(raw.getBytes(StandardCharsets.UTF_8));
            }
            File[] all = d.listFiles();
            if (all != null && all.length > MAX_HISTORY) {
                Arrays.sort(all);
                for (int i = 0; i < all.length - MAX_HISTORY; i++) all[i].delete();
            }
        } catch (Exception ignored) {
            // Verlauf ist ein Zusatz: Fehler hier duerfen das Speichern nie verhindern.
        }
    }

    /** Fruehere Fassungen, neueste zuerst. Die aktuelle Fassung ist nicht dabei. */
    static List<Entry> history(Context c) {
        List<Entry> list = new ArrayList<>();
        File[] all = historyDir(c).listFiles();
        if (all == null) return list;
        Arrays.sort(all);
        String current = loadRaw(c);
        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY);
        for (int i = all.length - 1; i >= 0; i--) {
            try {
                String raw = readFile(all[i]);
                if (raw == null || raw.equals(current)) continue;
                long time = Long.parseLong(all[i].getName().replace(".json", ""));
                String text = deserialize(raw).toString().replace('\n', ' ').trim();
                if (text.length() > 40) text = text.substring(0, 40) + "…";
                list.add(new Entry(raw, time, fmt.format(new Date(time)) + "   " + text));
            } catch (Exception ignored) {
                // kaputte Datei ueberspringen
            }
        }
        return list;
    }

    // ---------- Hilfen ----------

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String readFile(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            return readAll(in);
        } catch (IOException e) {
            return null;
        }
    }
}
