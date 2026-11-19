package com.example.notizwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Freie Farbwahl: Farbfeld (Saettigung/Helligkeit) mit Fadenkreuz, Farbband (Farbton) und Hex-Feld.
 * Mit dem Fadenkreuz laesst sich jede Farbe stufenlos und genau anfahren; der Farbcode zeigt den Wert
 * und nimmt auch einen eingetippten Code (z. B. aus einem Design) entgegen.
 */
final class ColorPickerDialog {

    interface Callback {
        void onColor(int color);
    }

    private ColorPickerDialog() {}

    static void show(Activity a, int initial, final Callback cb) {
        final float[] hsv = new float[3];
        Color.colorToHSV(initial, hsv);

        final SvView sv = new SvView(a);
        final HueView hueBar = new HueView(a);
        final TextView sample = new TextView(a);
        final EditText hex = new EditText(a);
        final View oldBox = new View(a);
        final boolean[] updatingHex = {false};

        // gemeinsame Aktualisierung aller Teile nach jeder Aenderung von hsv
        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                int c = Color.HSVToColor(hsv);
                sv.hue = hsv[0];
                sv.sat = hsv[1];
                sv.val = hsv[2];
                sv.invalidate();
                hueBar.hue = hsv[0];
                hueBar.invalidate();
                sample.setBackgroundColor(c);
                sample.setTextColor(Palette.textColorFor(c));
                updatingHex[0] = true;
                hex.setText(String.format("#%06X", c & 0xFFFFFF));
                hex.setSelection(hex.getText().length());
                updatingHex[0] = false;
            }
        };
        sv.listener = () -> {
            hsv[1] = sv.sat;
            hsv[2] = sv.val;
            // Fadenkreuz selbst nicht neu setzen, sonst springt es durch Rundung
            int c = Color.HSVToColor(hsv);
            sample.setBackgroundColor(c);
            sample.setTextColor(Palette.textColorFor(c));
            updatingHex[0] = true;
            hex.setText(String.format("#%06X", c & 0xFFFFFF));
            updatingHex[0] = false;
        };
        hueBar.listener = () -> {
            hsv[0] = hueBar.hue;
            refresh.run();
        };

        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        hex.setTypeface(Typeface.MONOSPACE);
        hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {}

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {}

            @Override
            public void afterTextChanged(Editable e) {
                if (updatingHex[0]) return;
                String t = e.toString().trim();
                if (t.startsWith("#")) t = t.substring(1);
                if (!t.matches("[0-9a-fA-F]{6}")) return;
                Color.colorToHSV(0xFF000000 | Integer.parseInt(t, 16), hsv);
                // Farbton bei Grautoenen nicht auf 0 springen lassen
                sv.hue = hsv[0];
                sv.sat = hsv[1];
                sv.val = hsv[2];
                sv.invalidate();
                hueBar.hue = hsv[0];
                hueBar.invalidate();
                int c = Color.HSVToColor(hsv);
                sample.setBackgroundColor(c);
                sample.setTextColor(Palette.textColorFor(c));
            }
        });

        int pad = Palette.dp(a, 20);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Palette.dp(a, 12), pad, 0);

        box.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Palette.dp(a, 220)));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Palette.dp(a, 36));
        hlp.topMargin = Palette.dp(a, 14);
        box.addView(hueBar, hlp);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Palette.dp(a, 56));
        rlp.topMargin = Palette.dp(a, 14);
        box.addView(row, rlp);

        oldBox.setBackgroundColor(0xFF000000 | initial);
        row.addView(oldBox, new LinearLayout.LayoutParams(Palette.dp(a, 56), Palette.dp(a, 56)));
        sample.setText("Aa  Notiz");
        sample.setGravity(Gravity.CENTER);
        sample.setTextSize(18);
        row.addView(sample, new LinearLayout.LayoutParams(0, Palette.dp(a, 56), 1f));

        TextView label = new TextView(a);
        label.setText(a.getString(R.string.picker_hex));
        label.setTextSize(13);
        label.setPadding(0, Palette.dp(a, 12), 0, 0);
        box.addView(label);
        box.addView(hex, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(a);
        scroll.addView(box);

        refresh.run();

        new AlertDialog.Builder(a)
                .setTitle(a.getString(R.string.picker_title))
                .setView(scroll)
                .setPositiveButton(android.R.string.ok,
                        (d, w) -> cb.onColor(0xFF000000 | (Color.HSVToColor(hsv) & 0xFFFFFF)))
                .setNegativeButton(a.getString(R.string.cancel), null)
                .show();
    }

    // ---------- Farbfeld ----------

    private static final class SvView extends View {
        float hue;
        float sat;
        float val;
        Runnable listener;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        SvView(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth();
            int h = getHeight();
            int pure = Color.HSVToColor(new float[]{hue, 1f, 1f});
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, w, 0, Color.WHITE, pure, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, p);
            p.setShader(new LinearGradient(0, 0, 0, h, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, p);
            p.setShader(null);

            float x = sat * w;
            float y = (1f - val) * h;
            float r = Palette.dp(getContext(), 11);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Palette.dp(getContext(), 3));
            p.setColor(Color.BLACK);
            cv.drawCircle(x, y, r + Palette.dp(getContext(), 1), p);
            p.setStrokeWidth(Palette.dp(getContext(), 2));
            p.setColor(Color.WHITE);
            cv.drawCircle(x, y, r, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                sat = Math.max(0f, Math.min(1f, e.getX() / Math.max(1, getWidth())));
                val = 1f - Math.max(0f, Math.min(1f, e.getY() / Math.max(1, getHeight())));
                invalidate();
                if (listener != null) listener.run();
                return true;
            }
            return action == MotionEvent.ACTION_UP || super.onTouchEvent(e);
        }
    }

    // ---------- Farbband (Farbton) ----------

    private static final class HueView extends View {
        float hue;
        Runnable listener;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        HueView(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth();
            int h = getHeight();
            int[] cols = new int[7];
            for (int i = 0; i < cols.length; i++)
                cols[i] = Color.HSVToColor(new float[]{i * 60f, 1f, 1f});
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, w, 0, cols, null, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, p);
            p.setShader(null);

            float x = hue / 360f * w;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Palette.dp(getContext(), 5));
            p.setColor(Color.BLACK);
            cv.drawLine(x, 0, x, h, p);
            p.setStrokeWidth(Palette.dp(getContext(), 3));
            p.setColor(Color.WHITE);
            cv.drawLine(x, 0, x, h, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                hue = Math.max(0f, Math.min(359.9f, e.getX() / Math.max(1, getWidth()) * 360f));
                invalidate();
                if (listener != null) listener.run();
                return true;
            }
            return action == MotionEvent.ACTION_UP || super.onTouchEvent(e);
        }
    }
}
