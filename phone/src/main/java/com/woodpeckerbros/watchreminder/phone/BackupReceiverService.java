package com.woodpeckerbros.watchreminder.phone;

import android.content.Intent;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class BackupReceiverService extends WearableListenerService {
    static final String OPEN_RESTORE_PATH = "/watch_reminder_open_restore";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (OPEN_RESTORE_PATH.equals(messageEvent.getPath())) {
            openApp();
            return;
        }
        if (LogStorage.MESSAGE_PATH.equals(messageEvent.getPath())) {
            try {
                LogStorage.save(this, messageEvent.getData());
            } catch (Exception exception) {
                android.util.Log.e("WatchReminderPhone", "Could not save logs", exception);
            }
            return;
        }
        if (BackupStorage.MESSAGE_PATH.equals(messageEvent.getPath())) {
            try {
                BackupStorage.save(this, messageEvent.getData());
            } catch (Exception exception) {
                android.util.Log.e("WatchReminderPhone", "Could not save backup", exception);
            }
        }
    }

    private void openApp() {
        Intent intent = new Intent(this, PhoneMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
        } catch (Exception exception) {
            android.util.Log.e("WatchReminderPhone", "Could not open phone app", exception);
        }
    }
}
