package com.woodpeckerbros.watchreminder;

import android.content.Context;

import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.nio.charset.StandardCharsets;

public class PhoneBackupSender {
    static final String MESSAGE_PATH = "/watch_reminder_backup";

    public interface Callback {
        void onSuccess(int count);
        void onError(String message);
    }

    private PhoneBackupSender() {
    }

    public static void send(Context context, Callback callback) {
        String backup = ReminderBackup.exportText(context);
        if (backup.isEmpty()) {
            callback.onError("לא הצלחתי ליצור גיבוי");
            return;
        }
        byte[] data = backup.getBytes(StandardCharsets.UTF_8);
        Wearable.getNodeClient(context).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    if (nodes.isEmpty()) {
                        callback.onError("לא נמצא טלפון מחובר");
                        return;
                    }
                    final int[] pending = {nodes.size()};
                    final int[] sent = {0};
                    for (Node node : nodes) {
                        Wearable.getMessageClient(context)
                                .sendMessage(node.getId(), MESSAGE_PATH, data)
                                .addOnSuccessListener(result -> {
                                    sent[0]++;
                                    pending[0]--;
                                    if (pending[0] == 0) {
                                        callback.onSuccess(sent[0]);
                                    }
                                })
                                .addOnFailureListener(error -> {
                                    AppLog.e(context, "send backup to phone failed", error);
                                    pending[0]--;
                                    if (pending[0] == 0) {
                                        if (sent[0] > 0) {
                                            callback.onSuccess(sent[0]);
                                        } else {
                                            callback.onError("השליחה לטלפון נכשלה");
                                        }
                                    }
                                });
                    }
                })
                .addOnFailureListener(error -> {
                    AppLog.e(context, "connected nodes failed", error);
                    callback.onError("לא הצלחתי למצוא טלפון מחובר");
                });
    }
}
