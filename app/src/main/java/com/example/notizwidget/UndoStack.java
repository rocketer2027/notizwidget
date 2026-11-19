package com.example.notizwidget;

import java.util.ArrayDeque;

/**
 * Rueckgaengig-Verlauf fuer den Editor: merkt sich die letzten {@link #MAX} Schritte
 * (Text samt Formatierung und Cursor-Stellung vor der jeweiligen Aenderung).
 *
 * Normales Tippen wird zusammengefasst: Solange man in derselben Richtung weitertippt (oder loescht)
 * und zwischen zwei Aenderungen weniger als {@link #GROUP_MS} Millisekunden liegen, zaehlt das als
 * ein Schritt. Alles andere (Einfuegen, Diktat, Markierung loeschen, Formatieren, Wiederherstellen)
 * ist ein eigener Schritt.
 */
final class UndoStack {
    static final int MAX = 30;
    private static final long GROUP_MS = 1000;
    /** Aenderungen ab dieser Laenge (oder mehr als 1 Zeichen Unterschied) sind immer ein eigener Schritt. */
    private static final int BIG_CHANGE = 40;

    static final class Step {
        final String raw;
        final int selStart;
        final int selEnd;

        Step(String raw, int selStart, int selEnd) {
            this.raw = raw;
            this.selStart = selStart;
            this.selEnd = selEnd;
        }
    }

    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private long lastTime;
    private int lastKind = -1;

    boolean canUndo() {
        return !steps.isEmpty();
    }

    void clear() {
        steps.clear();
        lastTime = 0;
        lastKind = -1;
    }

    /** Immer ein neuer Schritt (z. B. vor Formatierung oder dem Ersetzen der ganzen Notiz). */
    void push(CharSequence text, int selStart, int selEnd) {
        add(text, selStart, selEnd);
        lastTime = 0;
        lastKind = -1;
    }

    /**
     * Wird vor jeder Textaenderung aufgerufen ({@code text} ist noch der alte Stand).
     * count = Laenge des ersetzten Textes, after = Laenge des neuen Textes.
     */
    void beforeChange(CharSequence text, int selStart, int selEnd, int count, int after) {
        long now = System.currentTimeMillis();
        boolean big = Math.abs(after - count) > 1 || after >= BIG_CHANGE || count >= BIG_CHANGE;
        int kind = after > count ? 1 : (after < count ? 2 : 3);
        boolean continues = !big && lastKind == kind && now - lastTime < GROUP_MS;
        if (!continues) add(text, selStart, selEnd);
        lastTime = big ? 0 : now;
        lastKind = big ? -1 : kind;
    }

    /** Holt den letzten Schritt (oder null) und entfernt ihn. */
    Step pop() {
        lastTime = 0;
        lastKind = -1;
        return steps.pollLast();
    }

    private void add(CharSequence text, int selStart, int selEnd) {
        String raw = NoteStore.serialize(text);
        Step top = steps.peekLast();
        if (top != null && top.raw.equals(raw)) return; // keine Doppelten
        steps.addLast(new Step(raw, selStart, selEnd));
        while (steps.size() > MAX) steps.pollFirst();
    }
}
