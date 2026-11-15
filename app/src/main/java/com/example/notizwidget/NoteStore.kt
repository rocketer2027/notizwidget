package com.example.notizwidget

import android.content.Context

/** Speichert die eine Notiz in den SharedPreferences. */
object NoteStore {
    private const val PREFS = "note"
    private const val KEY = "text"

    fun load(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun save(context: Context, text: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, text)
            .apply()
        NoteWidget.updateAll(context)
    }
}
