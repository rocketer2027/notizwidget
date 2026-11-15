package com.example.notizwidget;

import android.app.Activity;
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

    private static final int MIN_SIZE = 10; // sp, SeekBar 0..20 ergibt 10..30

    private LinearLayout swatchRow;
    private TextView preview;
    private TextView labelTransp;
    private TextView labelSize;
    private TextView labelDays;
    private TextView buttonTime;
    private EditText editEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        swatchRow = findViewById(R.id.swatch_row);
        preview = findViewById(R.id.preview);
        labelTransp = findViewById(R.id.label_transp);
        labelSize = findViewById(R.id.label_size);
        labelDays = findViewById(R.id.label_days);
        buttonTime = findViewById(R.id.btn_time);
        editEmail = findViewById(R.id.edit_email);
        SeekBar seekTransp = findViewById(R.id.seek_transp);
        SeekBar seekSize = findViewById(R.id.seek_size);
        SeekBar seekDays = findViewById(R.id.seek_days);
        Switch switchBackup = findViewById(R.id.switch_backup);
        RadioGroup radioFormat = findViewById(R.id.radio_format);
        Button buttonNow = findViewById(R.id.btn_backup_now);
        TextView versionLabel = findViewById(R.id.version_label);

        // Version anzeigen, damit man sieht, welche Fassung installiert ist.
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionLabel.setText("Catatan Version " + pi.versionName + " (Build " + pi.getLongVersionCode() + ")");
        } catch (Exception e) {
            versionLabel.setText("Catatan");
        }

        // Startwerte setzen (bevor die Listener angehaengt werden)
        seekTransp.setProgress(Settings.transparency(this));
        seekSize.setProgress(Settings.widgetTextSize(this) - MIN_SIZE);
        seekDays.setProgress(Settings.backupDays(this) - 1);
        editEmail.setText(Settings.email(this));
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

        buttonTime.setOnClickListener(v -> new TimePickerDialog(this, (view, hour, minute) -> {
            Settings.setBackupTime(this, hour, minute);
            updateBackupLabels();
            rescheduleIfOn();
        }, Settings.backupHour(this), Settings.backupMinute(this), true).show());

        radioFormat.setOnCheckedChangeListener((group, checkedId) ->
                Settings.setBackupFormat(this, checkedId == R.id.fmt_txt ? "txt" : "html"));

        switchBackup.setOnCheckedChangeListener((button, checked) -> {
            Settings.setAutoBackup(this, checked);
            if (checked) Backup.schedule(this);
            else Backup.cancel(this);
        });

        buttonNow.setOnClickListener(v -> {
            boolean ok = Backup.backupNow(this);
            Toast.makeText(this, ok
                    ? "Gesichert in Dokumente/Catatan-Sicherungen"
                    : "Nichts gesichert (Notiz leer oder Speichern fehlgeschlagen)", Toast.LENGTH_LONG).show();
        });

        buildSwatches();
        updatePreview();
        updateBackupLabels();
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
        int current = Settings.bgColor(this);
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

    private void updatePreview() {
        int bg = Settings.bgColor(this);
        int alpha = Settings.bgAlpha255(this);
        preview.setBackgroundColor(Color.argb(alpha, Color.red(bg), Color.green(bg), Color.blue(bg)));
        preview.setTextColor(Palette.textColorFor(bg));
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, Settings.widgetTextSize(this));
        labelTransp.setText("Transparenz: " + Settings.transparency(this) + " %");
        labelSize.setText("Textgröße im Widget: " + Settings.widgetTextSize(this) + " sp");
    }

    private void updateBackupLabels() {
        int days = Settings.backupDays(this);
        labelDays.setText(days == 1 ? "Rhythmus: jeden Tag" : "Rhythmus: alle " + days + " Tage");
        buttonTime.setText(String.format(Locale.GERMANY, "Uhrzeit: %02d:%02d  (tippen zum Ändern)",
                Settings.backupHour(this), Settings.backupMinute(this)));
    }

    /** SeekBar-Listener, bei dem man nur onProgressChanged ueberschreiben muss. */
    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar bar) {}

        @Override
        public void onStopTrackingTouch(SeekBar bar) {}
    }
}
