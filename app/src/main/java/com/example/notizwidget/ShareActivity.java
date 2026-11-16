package com.example.notizwidget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Unsichtbare Zwischenstation: nimmt geteilten bzw. markierten Text aus anderen Apps entgegen
 * und haengt ihn unten an die Notiz an.
 */
public class ShareActivity extends Activity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        String add = null;
        if (in != null) {
            String action = in.getAction();
            if (Intent.ACTION_SEND.equals(action)) {
                CharSequence text = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
                CharSequence subject = in.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
                if (text != null) {
                    add = text.toString().trim();
                    // z. B. Browser: Titel der Seite plus Link
                    if (subject != null) {
                        String sub = subject.toString().trim();
                        if (!sub.isEmpty() && !add.contains(sub)) add = sub + "\n" + add;
                    }
                }
            } else if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
                CharSequence text = in.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
                if (text != null) add = text.toString().trim();
            }
        }
        if (add != null && !add.isEmpty()) {
            NoteStore.append(this, add);
            Toast.makeText(this, getString(R.string.note_added), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.nothing_to_add), Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
