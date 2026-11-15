package com.example.notizwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Empfaengt den taeglichen Wecker und den Neustart des Handys. */
public class BackupReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        // Nach Neustart und nach einem App-Update sind die Wecker weg und muessen neu gesetzt werden.
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            if (Settings.autoBackup(context)) Backup.schedule(context);
            return;
        }
        Backup.runIfDue(context);
    }
}
