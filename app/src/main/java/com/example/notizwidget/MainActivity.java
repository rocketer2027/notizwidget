package com.example.notizwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
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
import android.util.TypedValue;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Der Editor: ein Textfeld, Formatierung fuer markierten Text, ein kleines Menue. */
public class MainActivity extends Activity {

    private static final int BASE_SIZE = 18; // Standardgroesse im Editor (sp)
    private static final int[] SIZE_CHOICES = {10, 11, 12, 13, 14, 15, 16, 18, 20, 22, 24, 28, 32, 36, 40, 48};
    private static final int REQ_SAVE = 1;
    private static final int REQ_OPEN = 2;

    private static final int REQ_SPEECH = 3;

    private String uiStamp;
    private View root;
    private SelectionEditText editor;
    private TextView lastEdit;
    private View searchBar;
    private EditText searchInput;
    private ImageView btnSearch;
    private ImageView btnMic;
    private TextView menuButton;
    private TextView[] toolButtons;
    private boolean firstLoad = true;
    private boolean suppressWatch = false; // true, solange die App selbst den Text setzt
    private int changeStart;
    private int changeCount;

    // Format fuer allen neu getippten Text; bleibt, bis man es aendert (Standard = alles aus).
    private boolean typeBold;
    private boolean typeItalic;
    private boolean typeUnderline;
    private int typeSize = BASE_SIZE; // in sp, Standard = BASE_SIZE
    private Integer typeColor; // null = Standardfarbe

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(Settings.themeRes(this));
        super.onCreate(savedInstanceState);
        uiStamp = uiStamp();
        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        editor = findViewById(R.id.editor);
        menuButton = findViewById(R.id.btn_menu);
        lastEdit = findViewById(R.id.last_edit);
        searchBar = findViewById(R.id.search_bar);
        searchInput = findViewById(R.id.search_input);
        btnSearch = findViewById(R.id.btn_search);
        btnMic = findViewById(R.id.btn_mic);
        TextView bold = findViewById(R.id.tb_bold);
        TextView italic = findViewById(R.id.tb_italic);
        TextView underline = findViewById(R.id.tb_underline);
        underline.setPaintFlags(underline.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        TextView size = findViewById(R.id.tb_size);
        TextView color = findViewById(R.id.tb_color);
        toolButtons = new TextView[]{bold, italic, underline, size, color};

        bold.setOnClickListener(v -> formatSelection(0));
        italic.setOnClickListener(v -> formatSelection(1));
        underline.setOnClickListener(v -> formatSelection(2));
        size.setOnClickListener(v -> formatSelection(3));
        color.setOnClickListener(v -> formatSelection(4));
        menuButton.setOnClickListener(this::showMenu);
        btnSearch.setOnClickListener(v -> toggleSearch());
        btnMic.setOnClickListener(v -> startDictation());
        findViewById(R.id.search_next).setOnClickListener(v -> findNext(true));
        findViewById(R.id.search_prev).setOnClickListener(v -> findNext(false));
        findViewById(R.id.search_close).setOnClickListener(v -> toggleSearch());
        searchInput.setOnEditorActionListener((v, actionId, ev) -> {
            findNext(true);
            return true;
        });
        // Beim Markieren zeigt der Groessen-Knopf die Groesse des markierten Textes.
        editor.setSelectionListener((s, e) -> updateToolState());

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
                if (suppressWatch) return;
                showLastEdit(System.currentTimeMillis());
                if (changeCount <= 0) return;
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

        showSplashOnFirstStart();

        if (Settings.autoBackup(this)) {
            Backup.schedule(this);
            Backup.requestNotificationPermission(this, true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!uiStamp().equals(uiStamp)) {
            recreate();
            return;
        }
        applyColors();
        showLastEdit(Settings.lastEdit(this));
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

    private String uiStamp() {
        return Settings.language(this) + Settings.darkMode(this);
    }

    private void showLastEdit(long time) {
        if (time <= 0) {
            lastEdit.setText("");
            return;
        }
        String when = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,
                java.text.DateFormat.SHORT, Lang.locale(this)).format(new Date(time));
        lastEdit.setText(getString(R.string.last_edit, when));
    }

    // ---------- Farben ----------

    private void applyColors() {
        int bg = Settings.bgColor(this);
        int fg = Palette.textColorFor(bg);
        root.setBackgroundColor(bg);
        editor.setTextColor(fg);
        editor.setHintTextColor((fg & 0x00FFFFFF) | 0x80000000);
        menuButton.setTextColor(fg);
        lastEdit.setTextColor(fg);
        btnSearch.setColorFilter(fg);
        btnMic.setColorFilter(fg);
        searchInput.setTextColor(fg);
        searchInput.setHintTextColor((fg & 0x00FFFFFF) | 0x80000000);
        for (int id : new int[]{R.id.search_prev, R.id.search_next, R.id.search_close})
            ((TextView) findViewById(id)).setTextColor(fg);
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
                TextFormat.toggleUnderline(e, start, end);
                break;
            case 3:
                pickSize(sz -> {
                    TextFormat.setSize(e, start, end, sz / (float) BASE_SIZE);
                    updateToolState();
                });
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
                typeUnderline = !typeUnderline;
                break;
            case 3:
                pickSize(sz -> {
                    typeSize = sz;
                    updateToolState();
                });
                return;
            default:
                Palette.pickTextColor(this, color -> {
                    typeColor = color == Palette.NO_COLOR ? null : Integer.valueOf(color);
                    updateToolState();
                });
                return;
        }
        updateToolState();
    }

    private interface SizeCallback {
        void onSize(int sp);
    }

    private boolean hasSelection() {
        int a = editor.getSelectionStart();
        int b = editor.getSelectionEnd();
        return a >= 0 && b >= 0 && a != b;
    }

    /** Groesse (sp) des markierten Textes, gemessen am Anfang der Markierung. */
    private int selectedTextSize() {
        int a = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
        android.text.style.RelativeSizeSpan[] spans =
                editor.getText().getSpans(a, a + 1, android.text.style.RelativeSizeSpan.class);
        float f = spans.length > 0 ? spans[0].getSizeChange() : 1f;
        return Math.round(f * BASE_SIZE);
    }

    /** Zahl auf dem Groessen-Knopf: Groesse der Markierung, sonst das Tippformat. */
    private int currentSizeShown() {
        return hasSelection() ? selectedTextSize() : typeSize;
    }

    /** Liste der Schriftgroessen zum direkten Antippen; jede Zahl steht in ihrer eigenen Groesse. */
    private void pickSize(final SizeCallback cb) {
        final boolean selection = hasSelection();
        final int shown = currentSizeShown();
        final String tag = getString(selection ? R.string.size_current : R.string.size_default);
        final String[] labels = new String[SIZE_CHOICES.length];
        for (int i = 0; i < labels.length; i++)
            labels[i] = SIZE_CHOICES[i] + (SIZE_CHOICES[i] == shown ? "  " + tag : "");
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, SIZE_CHOICES[position]);
                v.setMinHeight(0);
                v.setMinimumHeight(0);
                v.setTypeface(null, SIZE_CHOICES[position] == shown ? Typeface.BOLD : Typeface.NORMAL);
                return v;
            }
        };
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.size_title))
                .setAdapter(adapter, (d, which) -> cb.onSize(SIZE_CHOICES[which]))
                .show();
    }

    // ---------- Suche ----------

    private void toggleSearch() {
        boolean show = searchBar.getVisibility() != View.VISIBLE;
        searchBar.setVisibility(show ? View.VISIBLE : View.GONE);
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (show) {
            searchInput.requestFocus();
            if (imm != null) imm.showSoftInput(searchInput, 0);
        } else {
            searchInput.setText("");
            editor.requestFocus();
        }
    }

    private void findNext(boolean forward) {
        String q = searchInput.getText().toString().toLowerCase(Lang.locale(this));
        if (q.isEmpty()) return;
        String hay = editor.getText().toString().toLowerCase(Lang.locale(this));
        if (hay.length() != editor.getText().length()) return; // seltene Sonderzeichen: Positionen waeren verschoben
        int pos;
        if (forward) {
            pos = hay.indexOf(q, editor.getSelectionEnd());
            if (pos < 0) pos = hay.indexOf(q);
        } else {
            int from = editor.getSelectionStart() - 1;
            pos = from >= 0 ? hay.lastIndexOf(q, from) : -1;
            if (pos < 0) pos = hay.lastIndexOf(q);
        }
        if (pos < 0) {
            toast(getString(R.string.not_found));
            return;
        }
        editor.requestFocus();
        editor.setSelection(pos, pos + q.length());
        final int at = pos;
        editor.post(() -> {
            android.text.Layout layout = editor.getLayout();
            if (layout == null) return;
            int y = layout.getLineTop(layout.getLineForOffset(at));
            ((android.widget.ScrollView) findViewById(R.id.editor_scroll))
                    .smoothScrollTo(0, Math.max(0, y - Palette.dp(this, 80)));
        });
    }

    // ---------- Splash (nur beim allerersten Start) ----------

    private void showSplashOnFirstStart() {
        android.content.SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
        if (sp.getBoolean("splashShown", false)) return;
        sp.edit().putBoolean("splashShown", true).apply();

        int bg = Settings.bgColor(this);
        int fg = Palette.textColorFor(bg);

        final android.widget.LinearLayout splash = new android.widget.LinearLayout(this);
        splash.setOrientation(android.widget.LinearLayout.VERTICAL);
        splash.setGravity(android.view.Gravity.CENTER);
        splash.setBackgroundColor(bg);
        splash.setClickable(true); // faengt Beruehrungen ab, Tippen schliesst den Splash

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextColor(fg);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(android.view.Gravity.CENTER);

        TextView author = new TextView(this);
        author.setText("by Tobias Siebert");
        author.setTextColor(fg);
        author.setAlpha(0.7f);
        author.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        author.setGravity(android.view.Gravity.CENTER);
        author.setPadding(0, Palette.dp(this, 8), 0, 0);

        splash.addView(title);
        splash.addView(author);
        addContentView(splash, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        final Runnable dismiss = new Runnable() {
            boolean done;

            @Override
            public void run() {
                if (done) return;
                done = true;
                splash.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                    android.view.ViewGroup parent = (android.view.ViewGroup) splash.getParent();
                    if (parent != null) parent.removeView(splash);
                }).start();
            }
        };
        splash.setOnClickListener(v -> dismiss.run());
        splash.postDelayed(dismiss, 2000);
    }

    // ---------- Sprachdiktat ----------

    private void startDictation() {
        Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, Lang.locale(this).toLanguageTag());
        i.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_prompt));
        try {
            startActivityForResult(i, REQ_SPEECH);
        } catch (Exception ex) {
            toast(getString(R.string.no_speech));
        }
    }

    private void insertDictation(String spoken) {
        if (spoken == null || spoken.trim().isEmpty()) return;
        Editable e = editor.getText();
        int a = Math.max(0, Math.min(editor.getSelectionStart(), editor.getSelectionEnd()));
        int b = Math.max(0, Math.max(editor.getSelectionStart(), editor.getSelectionEnd()));
        if (editor.getSelectionStart() < 0) a = b = e.length();
        String ins = spoken.trim();
        if (a > 0 && !Character.isWhitespace(e.charAt(a - 1))) ins = " " + ins;
        e.replace(a, b, ins);
        // Sofort speichern: onResume vergleicht Editor und Speicher und wuerde sonst den
        // frisch diktierten Text durch den alten gespeicherten Stand ersetzen.
        NoteStore.save(this, editor.getText());
    }

    private void applyTypingFormat(Editable e, int start, int end) {
        if (start >= end) return;
        if (typeBold) TextFormat.setStyle(e, start, end, Typeface.BOLD, true);
        if (typeItalic) TextFormat.setStyle(e, start, end, Typeface.ITALIC, true);
        if (typeUnderline) TextFormat.setUnderline(e, start, end, true);
        if (typeSize != BASE_SIZE) TextFormat.setSize(e, start, end, typeSize / (float) BASE_SIZE);
        if (typeColor != null) TextFormat.setColor(e, start, end, typeColor);
    }

    /** Zeigt dezent an, welches Tippformat gerade eingeschaltet ist. */
    private void updateToolState() {
        if (toolButtons == null || editor == null) return;
        int fg = Palette.textColorFor(Settings.bgColor(this));
        int hi = (fg & 0x00FFFFFF) | 0x33000000;
        toolButtons[0].setBackgroundColor(typeBold ? hi : Color.TRANSPARENT);
        toolButtons[1].setBackgroundColor(typeItalic ? hi : Color.TRANSPARENT);
        toolButtons[2].setBackgroundColor(typeUnderline ? hi : Color.TRANSPARENT);
        toolButtons[3].setText(String.valueOf(currentSizeShown()));
        toolButtons[4].setTextColor(typeColor != null ? typeColor : fg);
    }

    // ---------- Menue ----------

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        m.add(0, 1, 0, getString(R.string.settings_title));
        m.add(0, 2, 1, getString(R.string.menu_email));
        m.add(0, 3, 2, getString(R.string.menu_reminder));
        m.add(0, 4, 3, getString(R.string.menu_history));
        m.add(0, 5, 4, getString(R.string.menu_save_backup));
        m.add(0, 6, 5, getString(R.string.menu_open_backup));
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
            toast(getString(R.string.note_empty));
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
            toast(getString(R.string.no_mail_app));
        }
    }

    /** Legt aus dem markierten Text einen Kalendereintrag mit Erinnerung an (Zeit waehlt man dort). */
    private void createReminder() {
        int a = editor.getSelectionStart();
        int b = editor.getSelectionEnd();
        if (a < 0 || b < 0 || a == b) {
            toast(getString(R.string.select_text_first));
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
            toast(getString(R.string.no_calendar_app));
        }
    }

    private void showHistory() {
        NoteStore.save(this, editor.getText()); // aktuellen Stand sichern, bevor man zurueckgeht
        final List<NoteStore.Entry> list = NoteStore.history(this);
        if (list.isEmpty()) {
            toast(getString(R.string.no_history));
            return;
        }
        String[] labels = new String[list.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = list.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.history_title))
                .setItems(labels, (d, which) -> {
                    setNote(NoteStore.deserialize(list.get(which).raw));
                    editor.setSelection(editor.getText().length());
                    NoteStore.save(this, editor.getText());
                    toast(getString(R.string.restored));
                })
                .setNegativeButton(getString(R.string.cancel), null)
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
        if (requestCode == REQ_SPEECH) {
            if (data != null) {
                java.util.ArrayList<String> r =
                        data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
                if (r != null && !r.isEmpty()) insertDictation(r.get(0));
            }
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_SAVE) {
            boolean ok = Backup.writeToUri(this, uri, Backup.toHtml(editor.getText()));
            toast(ok ? getString(R.string.backup_saved) : getString(R.string.save_failed));
        } else if (requestCode == REQ_OPEN) {
            String content = Backup.readFromUri(this, uri);
            if (content == null) {
                toast(getString(R.string.read_failed));
                return;
            }
            final SpannableStringBuilder imported = Backup.fromBackup(content);
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.restore_title))
                    .setMessage(getString(R.string.restore_message))
                    .setPositiveButton(getString(R.string.restore_ok), (d, w) -> {
                        NoteStore.save(this, editor.getText());
                        setNote(imported);
                        editor.setSelection(editor.getText().length());
                        NoteStore.save(this, editor.getText());
                        toast(getString(R.string.restore_done));
                    })
                    .setNegativeButton(getString(R.string.cancel), null)
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
