package com.example.notizwidget

import android.app.Activity
import android.os.Bundle
import android.widget.EditText

class MainActivity : Activity() {

    private lateinit var editor: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        editor = findViewById(R.id.editor)
        editor.setText(NoteStore.load(this))
        editor.setSelection(editor.text.length)
    }

    // Wird beim Verlassen der App aufgerufen (Home, Zurück, App-Wechsel): hier wird gespeichert.
    override fun onPause() {
        super.onPause()
        NoteStore.save(this, editor.text.toString())
    }
}
