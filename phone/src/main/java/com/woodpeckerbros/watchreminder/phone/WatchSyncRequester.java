package com.woodpeckerbros.watchreminder.phone;

import android.content.Context;

import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

class WatchSyncRequester {
    private static final String REQUEST_PATH = "/watch_reminder_request_sync";

    interface Callback {
        void onSuccess();
        void onError(String message);
    }

    private WatchSyncRequester() {
    }

    static void request(Context context, Callback callback) {
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
                                .sendMessage(node.getId(), REQUEST_PATH, new byte[0])
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
                                        callback.onError(PhoneUiText.t(context, sent[0] ? "נשלחה בקשה לחלק מהשעונים" : "בקשת הסנכרון נכשלה"));
                                    }
                                });
                    }
                })
                .addOnFailureListener(error -> callback.onError(PhoneUiText.t(context, "לא הצלחתי למצוא שעון")));
    }
}
