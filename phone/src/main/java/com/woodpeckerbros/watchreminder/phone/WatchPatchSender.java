package com.woodpeckerbros.watchreminder.phone;

import android.content.Context;

import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

class WatchPatchSender {
    private static final String PATCH_PATH = "/watch_reminder_patch";

    interface Callback {
        void onSuccess();
        void onError(String message);
    }

    private WatchPatchSender() {
    }

    static void send(Context context, Callback callback) {
        if (!PendingPatchStore.hasPending(context)) {
            callback.onError("אין שינויים לשליחה");
            return;
        }
        byte[] data = PendingPatchStore.bytes(context);
        Wearable.getNodeClient(context).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    if (nodes.isEmpty()) {
                        callback.onError("לא נמצא שעון מחובר");
                        return;
                    }
                    final int[] pending = {nodes.size()};
                    final boolean[] sent = {false};
                    for (Node node : nodes) {
                        Wearable.getMessageClient(context)
                                .sendMessage(node.getId(), PATCH_PATH, data)
                                .addOnSuccessListener(result -> {
                                    sent[0] = true;
                                    pending[0]--;
                                    if (pending[0] == 0) {
                                        PendingPatchStore.clear(context);
                                        callback.onSuccess();
                                    }
                                })
                                .addOnFailureListener(error -> {
                                    pending[0]--;
                                    if (pending[0] == 0) {
                                        if (sent[0]) {
                                            PendingPatchStore.clear(context);
                                            callback.onSuccess();
                                        } else {
                                            callback.onError("שליחת השינויים לשעון נכשלה");
                                        }
                                    }
                                });
                    }
                })
                .addOnFailureListener(error -> callback.onError("לא הצלחתי למצוא שעון"));
    }
}
