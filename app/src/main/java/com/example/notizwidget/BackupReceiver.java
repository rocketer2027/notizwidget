package com.example.notizwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Empfaengt den taeglichen Wecker und den Neustart des Handys. */
public class BackupReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (Settings.autoBackup(context)) Backup.schedule(context);
            return;
        }
        Backup.runIfDue(context);
    }
}
