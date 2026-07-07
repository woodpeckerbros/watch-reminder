package com.woodpeckerbros.watchreminder;

import android.content.Intent;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.charset.StandardCharsets;

public class RestoreFromPhoneService extends WearableListenerService {
    private static final String RESTORE_PATH = "/watch_reminder_restore";
    private static final String PATCH_PATH = "/watch_reminder_patch";
    private static final String REQUEST_SYNC_PATH = "/watch_reminder_request_sync";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (REQUEST_SYNC_PATH.equals(messageEvent.getPath())) {
            AppLog.d(this, "phone requested reminder sync");
            sendBackupToRequester(messageEvent.getSourceNodeId());
            return;
        }
        if (PATCH_PATH.equals(messageEvent.getPath())) {
            RestoreFromPhoneStore.save(this, messageEvent.getData(), RestoreFromPhoneStore.MODE_PATCH);
            openPending();
            return;
        }
        if (!RESTORE_PATH.equals(messageEvent.getPath())) {
            return;
        }
        RestoreFromPhoneStore.save(this, messageEvent.getData());
        openPending();
    }

    private void sendBackupToRequester(String nodeId) {
        if (nodeId == null || nodeId.isEmpty()) {
            AppLog.w(this, "phone sync failed: missing source node");
            return;
        }
        String backup = ReminderBackup.exportText(this);
        if (backup.isEmpty()) {
            AppLog.w(this, "phone sync failed: empty backup");
            return;
        }
        Wearable.getMessageClient(this)
                .sendMessage(nodeId, PhoneBackupSender.MESSAGE_PATH, backup.getBytes(StandardCharsets.UTF_8))
                .addOnSuccessListener(result -> AppLog.d(this, "phone sync sent directly to node=" + nodeId))
                .addOnFailureListener(error -> AppLog.e(this, "phone sync direct send failed", error));
    }

    private void openPending() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_PENDING_RESTORE, true);
        startActivity(intent);
    }
}
