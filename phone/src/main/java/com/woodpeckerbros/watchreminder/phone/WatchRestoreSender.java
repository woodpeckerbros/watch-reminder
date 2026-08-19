package com.woodpeckerbros.watchreminder.phone;

import android.content.Context;

import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

class WatchRestoreSender {
    private static final String RESTORE_PATH = "/watch_reminder_restore";

    interface Callback {
        void onSuccess();
        void onError(String message);
    }

    private WatchRestoreSender() {
    }

    static void sendLastBackup(Context context, Callback callback) {
        try {
            send(context, BackupStorage.lastBackup(context), callback);
        } catch (Exception exception) {
            callback.onError(PhoneUiText.t(context, "אין גיבוי לשליחה"));
        }
    }

    static void sendBackup(Context context, BackupStorage.BackupEntry entry, Callback callback) {
        try {
            send(context, BackupStorage.readBackup(context, entry), callback);
        } catch (Exception exception) {
            callback.onError(PhoneUiText.t(context, "לא הצלחתי לקרוא את הגיבוי"));
        }
    }

    static void sendCurrentDocument(Context context, Callback callback) {
        String text = LocalReminderDocument.text(context);
        if (text.trim().isEmpty()) {
            callback.onError(PhoneUiText.t(context, "אין נתונים לשליחה. בצע סנכרון מהשעון קודם."));
            return;
        }
        send(context, LocalReminderDocument.bytes(context), callback);
    }

    private static void send(Context context, byte[] data, Callback callback) {
        Wearable.getNodeClient(context).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    if (nodes.isEmpty()) {
                        callback.onError(PhoneUiText.t(context, "לא נמצא שעון מחובר"));
                        return;
                    }
                    final int[] pending = {nodes.size()};
                    final boolean[] sent = {false};
                    for (Node node : nodes) {
                        Wearable.getMessageClient(context)
                                .sendMessage(node.getId(), RESTORE_PATH, data)
                                .addOnSuccessListener(result -> {
                                    sent[0] = true;
                                    pending[0]--;
                                    if (pending[0] == 0) {
                                        callback.onSuccess();
                                    }
                                })
                                .addOnFailureListener(error -> {
                                    pending[0]--;
                                    if (pending[0] == 0) {
                                        if (sent[0]) {
                                            callback.onSuccess();
                                        } else {
                                            callback.onError(PhoneUiText.t(context, "השליחה לשעון נכשלה"));
                                        }
                                    }
                                });
                    }
                })
                .addOnFailureListener(error -> callback.onError(PhoneUiText.t(context, "לא הצלחתי למצוא שעון")));
    }
}
