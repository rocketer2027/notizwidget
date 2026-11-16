package com.example.notizwidget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

/** EditText, das meldet, wenn sich Cursor oder Markierung aendern. */
public class SelectionEditText extends EditText {
    interface Listener {
        void onSelection(int start, int end);
    }

    private Listener listener;

    public SelectionEditText(Context context) {
        super(context);
    }

    public SelectionEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SelectionEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    void setSelectionListener(Listener l) {
        listener = l;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (listener != null) listener.onSelection(selStart, selEnd);
    }
}
