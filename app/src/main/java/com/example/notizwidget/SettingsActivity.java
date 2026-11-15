package com.example.notizwidget;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Kleines Einstellungsmenue: Hintergrundfarbe, Transparenz, Textgroesse, E-Mail, Sicherung. */
public class SettingsActivity extends Activity {

    private static final int MIN_SIZE = 10; // sp, SeekBar 0..20 ergibt 10..30

    private LinearLayout swatchRow;
    private TextView preview;
    private TextView labelTransp;
    private TextView labelSize;
    private EditText editEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        swatchRow = findViewById(R.id.swatch_row);
        preview = findViewById(R.id.preview);
        labelTransp = findViewById(R.id.label_transp);
        labelSize = findViewById(R.id.label_size);
        editEmail = findViewById(R.id.edit_email);
        SeekBar seekTransp = findViewById(R.id.seek_transp);
        SeekBar seekSize = findViewById(R.id.seek_size);
        Switch switchBackup = findViewById(R.id.switch_backup);

        seekTransp.setProgress(Settings.transparency(this));
        seekSize.setProgress(Settings.widgetTextSize(this) - MIN_SIZE);
        editEmail.setText(Settings.email(this));
        switchBackup.setChecked(Settings.autoBackup(this));

        seekTransp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                Settings.setTransparency(SettingsActivity.this, progress);
                updatePreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });

        seekSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                Settings.setWidgetTextSize(SettingsActivity.this, progress + MIN_SIZE);
                updatePreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });

        switchBackup.setOnCheckedChangeListener((button, checked) -> {
            Settings.setAutoBackup(this, checked);
            if (checked) Backup.schedule(this);
            else Backup.cancel(this);
        });

        buildSwatches();
        updatePreview();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Settings.setEmail(this, editEmail.getText().toString());
        NoteWidget.updateAll(this); // Widget uebernimmt die neuen Einstellungen
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
}
