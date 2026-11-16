package com.example.notizwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Empfaengt den Sicherungs-Wecker sowie Neustart des Handys und App-Update. */
public class BackupReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        // Nach Neustart und nach einem App-Update sind die Wecker weg und muessen neu gesetzt werden.
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            if (Settings.autoBackup(context)) Backup.schedule(context);
            return;
        }
        boolean force = intent.getBooleanExtra(Backup.EXTRA_FORCE, false);
        // Zuerst den naechsten Wecker setzen, damit die Kette nie abreisst - egal, was jetzt passiert.
        if (!force && Settings.autoBackup(context)) Backup.schedule(context);
        Backup.runAuto(context, force);
    }
}
