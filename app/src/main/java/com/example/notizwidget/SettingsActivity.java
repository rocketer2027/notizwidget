package com.example.notizwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.app.TimePickerDialog;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Einstellungen: Aussehen des Widgets, automatische Sicherung, E-Mail-Adresse. */
public class SettingsActivity extends Activity {

    private static final int[] LOGO_RES = {R.drawable.logo, R.drawable.logo_blue};
    private static final int[] LOGO_NAME = {R.string.logo_gold, R.string.logo_blue};

    private final android.widget.ImageView[] logoViews = new android.widget.ImageView[2];

    private static final int MIN_SIZE = 10; // sp, SeekBar 0..20 ergibt 10..30

    private LinearLayout swatchRow;
    private TextView preview;
    private TextView labelTransp;
    private TextView labelSize;
    private TextView labelDays;
    private TextView labelKeep;
    private TextView buttonLanguage;
    private TextView buttonTime;
    private EditText editEmail;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(Settings.themeRes(this));
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.settings_title));
        setContentView(R.layout.activity_settings);

        swatchRow = findViewById(R.id.swatch_row);
        preview = findViewById(R.id.preview);
        labelTransp = findViewById(R.id.label_transp);
        labelSize = findViewById(R.id.label_size);
        labelDays = findViewById(R.id.label_days);
        buttonTime = findViewById(R.id.btn_time);
        labelKeep = findViewById(R.id.label_keep);
        buttonLanguage = findViewById(R.id.btn_language);
        SeekBar seekKeep = findViewById(R.id.seek_keep);
        Switch switchDark = findViewById(R.id.switch_dark);
        Button buttonCleanup = findViewById(R.id.btn_cleanup);
        editEmail = findViewById(R.id.edit_email);
        SeekBar seekTransp = findViewById(R.id.seek_transp);
        SeekBar seekSize = findViewById(R.id.seek_size);
        SeekBar seekDays = findViewById(R.id.seek_days);
        Switch switchBackup = findViewById(R.id.switch_backup);
        RadioGroup radioFormat = findViewById(R.id.radio_format);
        Button buttonNow = findViewById(R.id.btn_backup_now);
        Button buttonTest = findViewById(R.id.btn_test_auto);
        TextView versionLabel = findViewById(R.id.version_label);

        // Version anzeigen, damit man sieht, welche Fassung installiert ist.
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionLabel.setText(getString(R.string.version_fmt, pi.versionName, pi.getLongVersionCode()));
        } catch (Exception e) {
            versionLabel.setText("Catatan");
        }

        // Startwerte setzen (bevor die Listener angehaengt werden)
        seekTransp.setProgress(Settings.transparency(this));
        seekSize.setProgress(Settings.widgetTextSize(this) - MIN_SIZE);
        seekDays.setProgress(Settings.backupDays(this) - 1);
        editEmail.setText(Settings.email(this));
        seekKeep.setProgress(Settings.maxBackups(this) - 1);
        switchDark.setChecked(Settings.darkMode(this));
        switchBackup.setChecked(Settings.autoBackup(this));
        radioFormat.check("txt".equals(Settings.backupFormat(this)) ? R.id.fmt_txt : R.id.fmt_html);

        seekTransp.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                Settings.setTransparency(SettingsActivity.this, progress);
                updatePreview();
            }
        });

        seekSize.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                Settings.setWidgetTextSize(SettingsActivity.this, progress + MIN_SIZE);
                updatePreview();
            }
        });

        seekDays.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                Settings.setBackupDays(SettingsActivity.this, progress + 1);
                updateBackupLabels();
            }
        });

        seekKeep.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                Settings.setMaxBackups(SettingsActivity.this, progress + 1);
                updateBackupLabels();
            }
        });

        buttonCleanup.setOnClickListener(v -> {
            final int keep = Settings.maxBackups(this);
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.cleanup_title))
                    .setMessage(getString(R.string.cleanup_message, keep))
                    .setPositiveButton(getString(R.string.cleanup_ok), (d, w) -> {
                        int n = Backup.prune(this, keep);
                        Toast.makeText(this, getString(R.string.cleanup_done, n), Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show();
        });

        switchDark.setOnCheckedChangeListener((button, checked) -> {
            Settings.setDarkMode(this, checked);
            NoteWidget.updateAll(this);
            recreate();
        });

        buttonLanguage.setOnClickListener(v -> {
            String[] names = new String[Lang.NAMES.length];
            for (int i = 0; i < names.length; i++) names[i] = getString(Lang.NAMES[i]);
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.set_language_title))
                    .setSingleChoiceItems(names, Lang.indexOf(this), (d, which) -> {
                        d.dismiss();
                        Settings.setLanguage(this, Lang.CODES[which]);
                        NoteWidget.updateAll(this);
                        recreate();
                    })
                    .show();
        });

        buildLogoRow();

        buttonTime.setOnClickListener(v -> new TimePickerDialog(this, (view, hour, minute) -> {
            Settings.setBackupTime(this, hour, minute);
            updateBackupLabels();
            rescheduleIfOn();
        }, Settings.backupHour(this), Settings.backupMinute(this), true).show());

        radioFormat.setOnCheckedChangeListener((group, checkedId) ->
                Settings.setBackupFormat(this, checkedId == R.id.fmt_txt ? "txt" : "html"));

        switchBackup.setOnCheckedChangeListener((button, checked) -> {
            Settings.setAutoBackup(this, checked);
            if (checked) {
                Backup.schedule(this);
                Backup.requestNotificationPermission(this, false);
            } else {
                Backup.cancel(this);
            }
        });

        buttonNow.setOnClickListener(v -> {
            String name = Backup.backupNow(this, false);
            Toast.makeText(this, name != null
                    ? getString(R.string.backup_done_toast, name)
                    : getString(R.string.backup_none_toast), Toast.LENGTH_LONG).show();
        });

        buttonTest.setOnClickListener(v -> {
            Backup.requestNotificationPermission(this, false);
            Backup.scheduleTest(this);
            Toast.makeText(this, getString(R.string.test_started), Toast.LENGTH_LONG).show();
        });

        buildSwatches();
        updatePreview();
        updateBackupLabels();
        if (Settings.autoBackup(this)) Backup.schedule(this); // Wecker sicherheitshalber neu setzen
    }

    @Override
    protected void onPause() {
        super.onPause();
        Settings.setEmail(this, editEmail.getText().toString());
        NoteWidget.updateAll(this); // Widget uebernimmt die neuen Einstellungen
    }

    private void rescheduleIfOn() {
        if (Settings.autoBackup(this)) Backup.schedule(this);
    }

    private void buildSwatches() {
        swatchRow.removeAllViews();
        int current = Settings.rawBgColor(this);
        for (final int color : Palette.BG) {
            swatchRow.addView(Palette.swatch(this, color, color == current, 44, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Settings.setBgColor(SettingsActivity.this, color);
                    buildSwatches();
                    updatePreview();
                }
            }));
        }
    }

    /** Zwei Logos zum Antippen; das gewaehlte bekommt einen Rahmen. */
    private void buildLogoRow() {
        LinearLayout row = findViewById(R.id.logo_row);
        for (int i = 0; i < LOGO_RES.length; i++) {
            final int index = i;
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setImageResource(LOGO_RES[i]);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            iv.setContentDescription(getString(LOGO_NAME[i]));
            int pad = Palette.dp(this, 6);
            iv.setPadding(pad, pad, pad, pad);
            iv.setOnClickListener(v -> {
                Settings.setLogo(this, index);
                Settings.applySplashTheme(this);
                refreshLogoRow();
            });
            int size = Palette.dp(this, 96);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(Palette.dp(this, 12));
            row.addView(iv, lp);
            logoViews[i] = iv;
        }
        refreshLogoRow();
    }

    private void refreshLogoRow() {
        int selected = Settings.logo(this);
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorAccent, tv, true);
        int accent = tv.data;
        for (int i = 0; i < logoViews.length; i++) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(Palette.dp(this, 12));
            if (i == selected) g.setStroke(Palette.dp(this, 3), accent);
            else g.setStroke(Palette.dp(this, 1), 0x66888888);
            logoViews[i].setBackground(g);
        }
    }

    private void updatePreview() {
        int bg = Settings.bgColor(this);
        int alpha = Settings.bgAlpha255(this);
        preview.setBackgroundColor(Color.argb(alpha, Color.red(bg), Color.green(bg), Color.blue(bg)));
        preview.setTextColor(Palette.textColorFor(bg));
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, Settings.widgetTextSize(this));
        labelTransp.setText(getString(R.string.set_transp, Settings.transparency(this)));
        labelSize.setText(getString(R.string.set_textsize, Settings.widgetTextSize(this)));
    }

    private void updateBackupLabels() {
        int days = Settings.backupDays(this);
        labelDays.setText(days == 1 ? getString(R.string.set_every_day) : getString(R.string.set_every_n, days));
        labelKeep.setText(getString(R.string.set_keep, Settings.maxBackups(this)));
        buttonLanguage.setText(getString(R.string.set_language, getString(Lang.NAMES[Lang.indexOf(this)])));
        buttonTime.setText(getString(R.string.set_time, Settings.backupHour(this), Settings.backupMinute(this)));
    }

    /** SeekBar-Listener, bei dem man nur onProgressChanged ueberschreiben muss. */
    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar bar) {}

        @Override
        public void onStopTrackingTouch(SeekBar bar) {}
    }
}
