package com.woodpeckerbros.watchreminder;

import android.content.Context;

import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

public class PhoneAppOpener {
    static final String MESSAGE_PATH = "/watch_reminder_open_restore";

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    private PhoneAppOpener() {
    }

    public static void openRestore(Context context, Callback callback) {
        Wearable.getNodeClient(context).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    if (nodes.isEmpty()) {
                        callback.onError("לא נמצא טלפון מחובר");
                        return;
                    }
                    final int[] pending = {nodes.size()};
                    final boolean[] opened = {false};
                    for (Node node : nodes) {
                        Wearable.getMessageClient(context)
                                .sendMessage(node.getId(), MESSAGE_PATH, new byte[0])
                                .addOnSuccessListener(result -> {
                                    opened[0] = true;
                                    pending[0]--;
                                    if (pending[0] == 0) {
                                        callback.onSuccess();
                                    }
                                })
                                .addOnFailureListener(error -> {
                                    AppLog.e(context, "open phone restore failed", error);
                                    pending[0]--;
                                    if (pending[0] == 0) {
                                        if (opened[0]) {
                                            callback.onSuccess();
                                        } else {
                                            callback.onError("לא הצלחתי לפתוח את האפליקציה בטלפון");
                                        }
                                    }
                                });
                    }
                })
                .addOnFailureListener(error -> {
                    AppLog.e(context, "connected nodes failed for open phone", error);
                    callback.onError("לא הצלחתי למצוא טלפון מחובר");
                });
    }
}
