package com.example.notizwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Der Editor: ein Textfeld, Formatierung fuer markierten Text, ein kleines Menue. */
public class MainActivity extends Activity {

    private static final int REQ_SAVE = 1;
    private static final int REQ_OPEN = 2;

    private View root;
    private EditText editor;
    private TextView menuButton;
    private TextView[] toolButtons;
    private boolean firstLoad = true;
    private boolean suppressWatch = false; // true, solange die App selbst den Text setzt
    private int changeStart;
    private int changeCount;

    // Format fuer allen neu getippten Text; bleibt, bis man es aendert (Standard = alles aus).
    private boolean typeBold;
    private boolean typeItalic;
    private int typeSizeIdx = TextFormat.DEFAULT_SIZE_IDX;
    private Integer typeColor; // null = Standardfarbe

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        editor = findViewById(R.id.editor);
        menuButton = findViewById(R.id.btn_menu);
        TextView bold = findViewById(R.id.tb_bold);
        TextView italic = findViewById(R.id.tb_italic);
        TextView smaller = findViewById(R.id.tb_smaller);
        TextView bigger = findViewById(R.id.tb_bigger);
        TextView color = findViewById(R.id.tb_color);
        toolButtons = new TextView[]{bold, italic, smaller, bigger, color};

        bold.setOnClickListener(v -> formatSelection(0));
        italic.setOnClickListener(v -> formatSelection(1));
        smaller.setOnClickListener(v -> formatSelection(2));
        bigger.setOnClickListener(v -> formatSelection(3));
        color.setOnClickListener(v -> formatSelection(4));
        menuButton.setOnClickListener(this::showMenu);

        // Neuer Text am Anfang einer Zeile bekommt immer das Standardformat. Formatierung bleibt nur
        // bei dem Text, den man markiert und formatiert hat.
        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                changeStart = start;
                changeCount = count;
            }

            @Override
            public void afterTextChanged(Editable e) {
                if (suppressWatch || changeCount <= 0) return;
                int start = changeStart;
                int end = Math.min(changeStart + changeCount, e.length());
                if (start < 0 || start >= end) return;
                int nl = -1; // erster eingefuegter Zeilenumbruch
                for (int i = start; i < end; i++) {
                    if (e.charAt(i) == '\n') {
                        nl = i;
                        break;
                    }
                }
                boolean lineStart = start == 0 || e.charAt(start - 1) == '\n';
                // Neue Zeilen starten ohne uebernommene Formatierung; danach gilt das eingestellte Tippformat.
                if (nl >= 0) TextFormat.stripAll(e, lineStart ? start : nl + 1, end);
                else if (lineStart) TextFormat.stripAll(e, start, end);
                applyTypingFormat(e, start, end);
            }
        });

        if (Settings.autoBackup(this)) {
            Backup.schedule(this);
            Backup.requestNotificationPermission(this, true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
        // Notiz laden bzw. neu laden, falls sie inzwischen von aussen geaendert wurde (z. B. durch "Teilen").
        String stored = NoteStore.loadRaw(this);
        if (!NoteStore.serialize(editor.getText()).equals(stored)) {
            int oldSel = editor.getSelectionStart();
            setNote(NoteStore.deserialize(stored));
            int len = editor.getText().length();
            editor.setSelection(firstLoad || oldSel < 0 ? len : Math.min(oldSel, len));
        }
        firstLoad = false;
    }

    // Beim Verlassen der App (Home, Zurueck, App-Wechsel) wird automatisch gespeichert.
    @Override
    protected void onPause() {
        super.onPause();
        NoteStore.save(this, editor.getText());
    }

    // ---------- Farben ----------

    private void applyColors() {
        int bg = Settings.bgColor(this);
        int fg = Palette.textColorFor(bg);
        root.setBackgroundColor(bg);
        editor.setTextColor(fg);
        editor.setHintTextColor((fg & 0x00FFFFFF) | 0x80000000);
        menuButton.setTextColor(fg);
        for (TextView t : toolButtons) t.setTextColor(fg);
        applySystemBars(bg);
        updateToolState();
    }

    private void applySystemBars(int bg) {
        Window w = getWindow();
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        boolean light = Palette.isLight(bg);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(light ? mask : 0, mask);
            }
        } else {
            int mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            int flags = w.getDecorView().getSystemUiVisibility();
            w.getDecorView().setSystemUiVisibility(light ? (flags | mask) : (flags & ~mask));
        }
    }

    // ---------- Formatierung ----------

    private void formatSelection(int kind) {
        int a = editor.getSelectionStart();
        int b = editor.getSelectionEnd();
        if (a < 0 || b < 0 || a == b) {
            changeTypingFormat(kind); // nichts markiert: Format fuer den weiteren Text festlegen
            return;
        }
        final int start = Math.min(a, b);
        final int end = Math.max(a, b);
        final Editable e = editor.getText();
        switch (kind) {
            case 0:
                TextFormat.toggleStyle(e, start, end, Typeface.BOLD);
                break;
            case 1:
                TextFormat.toggleStyle(e, start, end, Typeface.ITALIC);
                break;
            case 2:
                TextFormat.changeSize(e, start, end, -1);
                break;
            case 3:
                TextFormat.changeSize(e, start, end, +1);
                break;
            default:
                Palette.pickTextColor(this, color -> TextFormat.setColor(e, start, end, color));
                break;
        }
    }

    private void changeTypingFormat(int kind) {
        switch (kind) {
            case 0:
                typeBold = !typeBold;
                break;
            case 1:
                typeItalic = !typeItalic;
                break;
            case 2:
                typeSizeIdx = Math.max(0, typeSizeIdx - 1);
                break;
            case 3:
                typeSizeIdx = Math.min(TextFormat.SIZES.length - 1, typeSizeIdx + 1);
                break;
            default:
                Palette.pickTextColor(this, color -> {
                    typeColor = color == Palette.NO_COLOR ? null : Integer.valueOf(color);
                    updateToolState();
                });
                return;
        }
        updateToolState();
    }

    private void applyTypingFormat(Editable e, int start, int end) {
        if (start >= end) return;
        if (typeBold) TextFormat.setStyle(e, start, end, Typeface.BOLD, true);
        if (typeItalic) TextFormat.setStyle(e, start, end, Typeface.ITALIC, true);
        if (typeSizeIdx != TextFormat.DEFAULT_SIZE_IDX) TextFormat.setSize(e, start, end, TextFormat.SIZES[typeSizeIdx]);
        if (typeColor != null) TextFormat.setColor(e, start, end, typeColor);
    }

    /** Zeigt dezent an, welches Tippformat gerade eingeschaltet ist. */
    private void updateToolState() {
        int fg = Palette.textColorFor(Settings.bgColor(this));
        int hi = (fg & 0x00FFFFFF) | 0x33000000;
        toolButtons[0].setBackgroundColor(typeBold ? hi : Color.TRANSPARENT);
        toolButtons[1].setBackgroundColor(typeItalic ? hi : Color.TRANSPARENT);
        toolButtons[2].setBackgroundColor(typeSizeIdx < TextFormat.DEFAULT_SIZE_IDX ? hi : Color.TRANSPARENT);
        toolButtons[3].setBackgroundColor(typeSizeIdx > TextFormat.DEFAULT_SIZE_IDX ? hi : Color.TRANSPARENT);
        toolButtons[4].setTextColor(typeColor != null ? typeColor : fg);
    }

    // ---------- Menue ----------

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        m.add(0, 1, 0, "Einstellungen");
        m.add(0, 2, 1, "Per E-Mail senden");
        m.add(0, 3, 2, "Erinnerung für Markierung");
        m.add(0, 4, 3, "Gelöschtes zurückholen");
        m.add(0, 5, 4, "Sicherung speichern …");
        m.add(0, 6, 5, "Sicherung einspielen …");
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    startActivity(new Intent(this, SettingsActivity.class));
                    break;
                case 2:
                    sendByEmail();
                    break;
                case 3:
                    createReminder();
                    break;
                case 4:
                    showHistory();
                    break;
                case 5:
                    saveBackupAs();
                    break;
                case 6:
                    openBackup();
                    break;
                default:
                    return false;
            }
            return true;
        });
        pm.show();
    }

    private void sendByEmail() {
        String plain = editor.getText().toString();
        if (plain.trim().isEmpty()) {
            toast("Die Notiz ist leer");
            return;
        }
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
        String to = Settings.email(this);
        if (!to.isEmpty()) i.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
        i.putExtra(Intent.EXTRA_SUBJECT, "Catatan");
        i.putExtra(Intent.EXTRA_TEXT, plain);
        i.putExtra(Intent.EXTRA_HTML_TEXT,
                Html.toHtml(editor.getText(), Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE));
        try {
            startActivity(i);
        } catch (ActivityNotFoundException ex) {
            toast("Keine E-Mail-App gefunden");
        }
    }

    /** Legt aus dem markierten Text einen Kalendereintrag mit Erinnerung an (Zeit waehlt man dort). */
    private void createReminder() {
        int a = editor.getSelectionStart();
        int b = editor.getSelectionEnd();
        if (a < 0 || b < 0 || a == b) {
            toast("Erst Text markieren");
            return;
        }
        String title = editor.getText().subSequence(Math.min(a, b), Math.max(a, b)).toString().trim();
        if (title.length() > 200) title = title.substring(0, 200);
        long begin = System.currentTimeMillis() + 60L * 60 * 1000;
        Intent i = new Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI);
        i.putExtra(CalendarContract.Events.TITLE, title);
        i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin);
        i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 30L * 60 * 1000);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException ex) {
            toast("Keine Kalender-App gefunden");
        }
    }

    private void showHistory() {
        NoteStore.save(this, editor.getText()); // aktuellen Stand sichern, bevor man zurueckgeht
        final List<NoteStore.Entry> list = NoteStore.history(this);
        if (list.isEmpty()) {
            toast("Noch keine früheren Fassungen");
            return;
        }
        String[] labels = new String[list.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = list.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle("Frühere Fassung zurückholen")
                .setItems(labels, (d, which) -> {
                    setNote(NoteStore.deserialize(list.get(which).raw));
                    editor.setSelection(editor.getText().length());
                    NoteStore.save(this, editor.getText());
                    toast("Zurückgeholt");
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void saveBackupAs() {
        String name = "Catatan-" + new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(new Date()) + ".html";
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/html");
        i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, REQ_SAVE);
    }

    private void openBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_OPEN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_SAVE) {
            boolean ok = Backup.writeToUri(this, uri, Backup.toHtml(editor.getText()));
            toast(ok ? "Sicherung gespeichert" : "Speichern fehlgeschlagen");
        } else if (requestCode == REQ_OPEN) {
            String content = Backup.readFromUri(this, uri);
            if (content == null) {
                toast("Datei konnte nicht gelesen werden");
                return;
            }
            final SpannableStringBuilder imported = Backup.fromBackup(content);
            new AlertDialog.Builder(this)
                    .setTitle("Sicherung einspielen?")
                    .setMessage("Die aktuelle Notiz wird ersetzt. Sie bleibt unter „Gelöschtes zurückholen“ erhalten.")
                    .setPositiveButton("Einspielen", (d, w) -> {
                        NoteStore.save(this, editor.getText());
                        setNote(imported);
                        editor.setSelection(editor.getText().length());
                        NoteStore.save(this, editor.getText());
                        toast("Sicherung eingespielt");
                    })
                    .setNegativeButton("Abbrechen", null)
                    .show();
        }
    }

    /** Setzt den Notiztext, ohne dass die Formatierungs-Regel fuer neue Zeilen eingreift. */
    private void setNote(CharSequence content) {
        suppressWatch = true;
        try {
            editor.setText(content, TextView.BufferType.EDITABLE);
        } finally {
            suppressWatch = false;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
